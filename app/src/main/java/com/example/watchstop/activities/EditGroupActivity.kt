package com.example.watchstop.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.data.FirebaseRepository
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.data.CurrentGroupObject
import com.example.watchstop.data.UserGeofencesDatabase
import com.example.watchstop.model.GroupEntry
import com.example.watchstop.model.GroupRole
import com.example.watchstop.view.ui.theme.CarbonGrey
import com.example.watchstop.view.ui.theme.ElectricYellow
import com.example.watchstop.view.ui.theme.Purple40
import com.example.watchstop.view.ui.theme.SlateGrey
import com.example.watchstop.view.ui.theme.WatchStopTheme
import kotlinx.coroutines.launch

class EditGroupActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WatchStopTheme(darkTheme = darkmode) {
                EditGroupScreen(onFinish = { finish() })
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditGroupScreen(onFinish: () -> Unit) {
    val snapshot = remember { CurrentGroupObject.getCurrentGroupEntry() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf(snapshot.title) }
    var description by remember { mutableStateOf(snapshot.description) }
    var day by remember { mutableStateOf(snapshot.eventDateTime.dayOfMonth.toString()) }
    var month by remember { mutableStateOf(snapshot.eventDateTime.monthValue.toString()) }
    var year by remember { mutableStateOf(snapshot.eventDateTime.year.toString()) }
    val timePickerState = rememberTimePickerState(
        initialHour = snapshot.eventDateTime.hour,
        initialMinute = snapshot.eventDateTime.minute
    )
    var showInfoDialog by remember { mutableStateOf(false) }
    var showNoGeofenceDialog by remember { mutableStateOf(false) }
    var selectedGeofenceId by remember { //initialise from group current geofence
        mutableStateOf(snapshot.geofence?.id ?: "")
    }

    val memberNames = remember { mutableStateListOf(*snapshot.groupMemberNames.toTypedArray()) }
    val groupId = CurrentGroupObject.getCurrentGroupId()

    // track members the admin explicitly removed via the Remove button.
    // updateGroupMetadata receives ONLY this set and nulls out exactly those UIDs.
    // We never diff live Firebase vs local state — that was the root cause of the bug
    // where a member accepting an invitation got evicted when the admin pressed Save.
    val explicitlyRemovedMembers = remember { mutableStateOf(setOf<String>()) }

    // pendingInvitations driven entirely by live Firebase observer.
    val pendingInvitations = remember { mutableStateListOf<String>() }

    LaunchedEffect(groupId) {
        FirebaseRepository.observePendingInvitationsSentByMe(groupId).collect { invites ->
            pendingInvitations.clear()
            pendingInvitations.addAll(invites)
        }
    }

    val memberRoles = remember { mutableStateMapOf<String, GroupRole>().apply { putAll(snapshot.memberRoles) } }
    val sharingEnabled = remember { mutableStateMapOf<String, Boolean>().apply { putAll(snapshot.locationSharingEnabled) } }
    val canToggle = remember { mutableStateMapOf<String, Boolean>().apply { putAll(snapshot.canToggleSharing) } }
    val adminApplications = remember { mutableStateListOf(*snapshot.adminApplications.toTypedArray()) }
    val appVotes = remember {
        mutableStateMapOf<String, MutableSet<String>>().apply {
            snapshot.adminApplicationVotes.forEach { (k, v) -> put(k, v.toMutableSet()) }
        }
    }
    val removalVotes = remember {
        mutableStateMapOf<String, MutableSet<String>>().apply {
            snapshot.votesToRemoveAdmin.forEach { (k, v) -> put(k, v.toMutableSet()) }
        }
    }

    val currentUser = UserProfileObject.uid ?: ""
    val currentRole = memberRoles[currentUser] ?: GroupRole.MEMBER
    val currentIsAdmin = (currentRole == GroupRole.ADMIN || currentRole == GroupRole.SUPER_ADMIN)
    val currentIsSuperAdmin = currentRole == GroupRole.SUPER_ADMIN

    val accentColor = Color(0xFF007AFF)
    val destructiveColor = Color(0xFFFF3B30)
    val successColor = Color(0xFF34C759)
    val secondaryText = if (darkmode) Color(0xFF8E8E93) else Color(0xFF636366)
    val outlineColor = if (darkmode) ElectricYellow else SlateGrey
    val buttonBorder = BorderStroke(1.dp, outlineColor.copy(alpha = 0.6f))
    val buttonContentColor = if (darkmode) Color.White else Color.Black

    //if no geofence selected/created
    if (showNoGeofenceDialog) {
        var noGeofences: Boolean = UserGeofencesDatabase.getAllGeofences().isEmpty()

        var actionString =
            if (noGeofences) "You haven't created any Geofences yet. Create one now?"
            else "You haven't selected a Geofence."
        var titleString =
            if (noGeofences) "No Geofences Found"
            else "Select a Geofence"
        AlertDialog(
            onDismissRequest = { showNoGeofenceDialog = false },
            title = { Text(titleString) },
            text = { Text(actionString) },
            confirmButton = {
                if (noGeofences) {
                    TextButton(onClick = {
                        showNoGeofenceDialog = false
                        val intent = Intent(context, MapActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Text("Add Geofence")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoGeofenceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Group") },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (darkmode) CarbonGrey else Purple40,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ── Edit Group Info Button ─────────────────────────────────────
                Button(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Group Info")
                }

                HorizontalDivider()

                // ── Add Member (Invite) ────────────────────────────────────────
                if (currentIsAdmin) {
                    Text("Invite Member", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    var searchQuery by remember { mutableStateOf("") }
                    var searchResult by remember { mutableStateOf<Pair<String, String>?>(null) }
                    var searchError by remember { mutableStateOf("") }
                    var isSearching by remember { mutableStateOf(false) }
                    val searchScope = rememberCoroutineScope()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; searchResult = null; searchError = "" },
                            label = { Text("Username or Email") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                searchScope.launch {
                                    isSearching = true; searchError = ""; searchResult = null
                                    val result = FirebaseRepository.findUserByUsernameOrEmail(searchQuery.trim())
                                    when {
                                        result == null ->
                                            searchError = "User not found"
                                        memberNames.contains(result.first) ->
                                            searchError = "Already a member of this group"
                                        pendingInvitations.contains(result.first) ->
                                            searchError = "Invite already pending"
                                        else ->
                                            searchResult = result
                                    }
                                    isSearching = false
                                }
                            },
                            enabled = searchQuery.isNotBlank() && !isSearching
                        ) { Text(if (isSearching) "..." else "Search") }
                    }

                    if (searchError.isNotEmpty()) {
                        Text(searchError, color = destructiveColor, fontSize = 12.sp)
                    }

                    searchResult?.let { (foundUid, foundName) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = successColor.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(foundName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Invite to group", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = {
                                        searchScope.launch {
                                            try {
                                                FirebaseRepository.inviteToGroup(groupId, foundUid)
                                                searchResult = null
                                                searchQuery = ""
                                            } catch (e: Exception) {
                                                searchError = "Failed to invite: ${e.message}"
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = successColor)
                                ) { Text("Invite") }
                            }
                        }
                    }
                    HorizontalDivider()
                }

                // ── Members Section ────────────────────────────────────────────
                Text("Members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                memberNames.forEach { member ->
                    val role = memberRoles[member] ?: GroupRole.MEMBER
                    val isSharing = sharingEnabled[member] ?: false
                    val memberCanToggle = canToggle[member] ?: false
                    val isSelf = member == currentUser
                    val isTargetAdmin = role == GroupRole.ADMIN
                    val isTargetSuperAdmin = role == GroupRole.SUPER_ADMIN
                    val removalVoteSet = removalVotes[member] ?: emptySet()
                    val eligibleForRemoval = memberNames.filter { it != member }
                    val removalNeeded = (eligibleForRemoval.size / 2) + 1
                    val hasVotedRemoval = removalVoteSet.contains(currentUser)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isSharing) Icons.Default.LocationOn else Icons.Default.LocationOff,
                                    contentDescription = null,
                                    tint = if (isSharing) successColor else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    var displayName by remember(member) { mutableStateOf(member) }
                                    LaunchedEffect(member) { displayName = FirebaseRepository.getUsername(member) }
                                    Text(
                                        text = displayName + if (isSelf) " (you)" else "",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = role.displayName,
                                        fontSize = 11.sp,
                                        color = when (role) {
                                            GroupRole.SUPER_ADMIN -> Color(0xFFFFCC00)
                                            GroupRole.ADMIN -> accentColor
                                            GroupRole.MEMBER -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                if (isSelf && memberCanToggle) {
                                    OutlinedButton(
                                        onClick = { sharingEnabled[member] = !isSharing },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (isSharing) "Stop Sharing" else "Share Location", fontSize = 11.sp)
                                    }
                                }
                                if (!isSelf && currentIsAdmin) {
                                    // Show remove button for all non-SuperAdmin members (including Admins)
                                    if (!isTargetSuperAdmin) {
                                        OutlinedButton(
                                            onClick = {
                                                // FIX: record the explicit removal so Save passes
                                                // exactly these UIDs to updateGroupMetadata.
                                                explicitlyRemovedMembers.value += member
                                                memberNames.remove(member)
                                                memberRoles.remove(member)
                                                sharingEnabled.remove(member)
                                                canToggle.remove(member)
                                            },
                                            modifier = Modifier.height(32.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, destructiveColor.copy(alpha = 0.5f))
                                        ) {
                                            Text("Remove", fontSize = 11.sp, color = destructiveColor)
                                        }
                                    }

                                    // Promote button only for MEMBER when currentIsSuperAdmin
                                    if (role == GroupRole.MEMBER && currentIsSuperAdmin) {
                                        OutlinedButton(
                                            onClick = {
                                                memberRoles[member] = GroupRole.ADMIN
                                                canToggle[member] = true
                                                coroutineScope.launch {
                                                    FirebaseRepository.promoteToAdmin(groupId, member)
                                                }
                                            },
                                            modifier = Modifier.height(32.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Promote", fontSize = 11.sp, color = accentColor)
                                        }
                                    }
                                }
                                // For super admins, show immediate remove button (only for other super admins)
                                if (!isSelf && isTargetSuperAdmin && currentIsSuperAdmin) {
                                    OutlinedButton(
                                        onClick = {
                                            // Super admin can immediately remove another super admin
                                            explicitlyRemovedMembers.value += member
                                            memberNames.remove(member)
                                            memberRoles.remove(member)
                                            sharingEnabled.remove(member)
                                            canToggle.remove(member)
                                        },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, destructiveColor.copy(alpha = 0.5f))
                                    ) {
                                        Text("Remove", fontSize = 11.sp, color = destructiveColor)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Pending Admin Applications ─────────────────────────────────
                if (currentIsAdmin && adminApplications.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Pending Admin Applications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    adminApplications.toList().forEach { applicant ->
                        val votes = appVotes.getOrPut(applicant) { mutableSetOf() }
                        val needed = ((memberNames.size - 1) / 2) + 1
                        val hasVoted = votes.contains(currentUser)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    var appName by remember(applicant) { mutableStateOf(applicant) }
                                    LaunchedEffect(applicant) { appName = FirebaseRepository.getUsername(applicant) }
                                    Text(appName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("${votes.size}/$needed approvals needed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                // FIX: Approve now calls FirebaseRepository.voteForAdminApplication
                                // directly (which uses groupRef with relative paths — admin safe)
                                // before updating local state, instead of only updating local state.
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                FirebaseRepository.voteForAdminApplication(
                                                    groupId, applicant, currentUser)
                                                votes.add(currentUser)
                                                if (currentIsSuperAdmin || votes.size >= needed) {
                                                    memberRoles[applicant] = GroupRole.ADMIN
                                                    canToggle[applicant] = true
                                                    adminApplications.remove(applicant)
                                                    appVotes.remove(applicant)
                                                }
                                            } catch (e: Exception) {
                                                // vote was already written; local state update
                                                // may still be valid — ignore
                                            }
                                        }
                                    },
                                    enabled = !hasVoted
                                ) {
                                    Text(
                                        text = if (hasVoted) "Voted" else "Approve",
                                        color = if (hasVoted) Color.Gray else successColor
                                    )
                                }
                                // FIX: Deny now calls FirebaseRepository.declineAdminApplication
                                // (which uses groupRef with relative paths — admin safe).
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                FirebaseRepository.declineAdminApplication(
                                                    groupId, applicant)
                                                adminApplications.remove(applicant)
                                                appVotes.remove(applicant)
                                            } catch (e: Exception) {
                                                // ignore
                                            }
                                        }
                                    }
                                ) {
                                    Text("Deny", color = destructiveColor)
                                }
                            }
                        }
                    }
                }

                // ── Pending Invitations Section ────────────────────────────────
                if (pendingInvitations.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Pending Invitations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val cancelScope = rememberCoroutineScope()
                    pendingInvitations.toList().forEach { invitedUid ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var invitedName by remember(invitedUid) { mutableStateOf(invitedUid) }
                                LaunchedEffect(invitedUid) { invitedName = FirebaseRepository.getUsername(invitedUid) }
                                Text(invitedName, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                if (currentIsAdmin) {
                                    IconButton(
                                        onClick = {
                                            cancelScope.launch {
                                                FirebaseRepository.cancelInvitation(groupId, invitedUid)
                                                pendingInvitations.remove(invitedUid)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Cancel invitation",
                                            tint = destructiveColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                val saveScope = rememberCoroutineScope()

                Button(
                    onClick = {
                        if (title.isBlank()) return@Button
                        saveScope.launch {
                            val latest = FirebaseRepository.getGroup(groupId) ?: return@launch

                            val newDateTime = try {
                                latest.eventDateTime
                                    .withYear(year.toIntOrNull() ?: latest.eventDateTime.year)
                                    .withMonth(month.toIntOrNull() ?: latest.eventDateTime.monthValue)
                                    .withDayOfMonth(day.toIntOrNull() ?: latest.eventDateTime.dayOfMonth)
                                    .withHour(timePickerState.hour)
                                    .withMinute(timePickerState.minute)
                            } catch (e: Exception) {
                                latest.eventDateTime
                            }

                            val selectedGeofence = if (selectedGeofenceId.isNotEmpty()) {
                                UserGeofencesDatabase.getAllGeofences()
                                    .find { it.id == selectedGeofenceId }
                            } else {
                                null
                            }

                            val updated = latest.copy(
                                title = title.trim(),
                                description = description,
                                eventDateTime = newDateTime,
                                memberRoles = memberRoles.toMutableMap(),
                                canToggleSharing = canToggle.toMutableMap(),
                                adminApplications = adminApplications.toMutableSet(),
                                adminApplicationVotes = appVotes.mapValues { it.value.toMutableSet() }.toMutableMap(),
                                votesToRemoveAdmin = removalVotes.mapValues { it.value.toMutableSet() }.toMutableMap(),
                                geofence = selectedGeofence
                            )

                            FirebaseRepository.updateGroupMetadata(
                                groupId,
                                updated,
                                explicitlyRemovedMembers.value
                            )

                            CurrentGroupObject.loadCurrentGroupEntry(updated)
                            onFinish()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(bottom = 32.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text("Save Changes")
                }
            }
        }

        // ── Group Info Dialog ──────────────────────────────────────────
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("Edit Group Info") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Group Title") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                            minLines = 2
                        )
                        Text("Target Date & Time", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = day, onValueChange = { if (it.length <= 2) day = it }, label = { Text("D") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = month, onValueChange = { if (it.length <= 2) month = it }, label = { Text("M") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = year, onValueChange = { if (it.length <= 4) year = it }, label = { Text("Y") }, modifier = Modifier.weight(1.5f))
                        }
                        var showTimePickerInternal by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { showTimePickerInternal = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(String.format("%02d:%02d hrs", timePickerState.hour, timePickerState.minute))
                        }
                        if (showTimePickerInternal) {
                            TimePickerDialog(
                                onDismissRequest = { showTimePickerInternal = false },
                                confirmButton = { TextButton(onClick = { showTimePickerInternal = false }) { Text("OK") } },
                                title = { Text("Pick Date & Time") }
                            ) { TimePicker(state = timePickerState) }
                        }


                        // Geofence
                        var gfExpanded by remember { mutableStateOf(false) }
                        val selectedGeofenceName =
                            UserGeofencesDatabase.getAllGeofences()
                                .find { it.id == selectedGeofenceId }?.name ?: "Select Geofence"

                        Box {
                            OutlinedButton(
                                onClick = {
                                    if (UserGeofencesDatabase.getAllGeofences().isEmpty()) {
                                        showNoGeofenceDialog = true
                                    } else {
                                        gfExpanded = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                border = buttonBorder, //use text field border thickness
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonContentColor)
                            ) {
                                Text("Geofence: $selectedGeofenceName");
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(
                                expanded = gfExpanded,
                                onDismissRequest = { gfExpanded = false }
                            ) {
                                UserGeofencesDatabase.getAllGeofences().forEach { gf ->
                                    DropdownMenuItem(
                                        text = { Text(gf.name) },
                                        onClick = {
                                            selectedGeofenceId = gf.id;
                                            gfExpanded = false
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text(" +  Create New") },
                                    onClick = {
                                        val intent = Intent(context, MapActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Done") } }
            )
        }

    }
}