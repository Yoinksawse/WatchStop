package com.example.watchstop.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.watchstop.data.CurrentGroupObject
import com.example.watchstop.data.FirebaseRepository
import com.example.watchstop.data.UserGeofencesDatabase
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.model.GroupRole
import com.example.watchstop.view.DatePickerDialog
import com.example.watchstop.view.TimePickerDialog
import com.example.watchstop.view.screens.ADMIN_ROLE_COLOUR
import com.example.watchstop.view.screens.MEMBER_ROLE_COLOUR
import com.example.watchstop.view.screens.NICEGREEN_COLOUR
import com.example.watchstop.view.screens.SUPERADMIN_ROLE_COLOUR
import com.example.watchstop.view.ui.theme.CarbonGrey
import com.example.watchstop.view.ui.theme.ElectricYellow
import com.example.watchstop.view.ui.theme.Purple40
import com.example.watchstop.view.ui.theme.SlateGrey
import com.example.watchstop.view.ui.theme.WatchStopTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val groupSnapshot = remember { CurrentGroupObject.getCurrentGroupEntry() }
    val groupId = CurrentGroupObject.getCurrentGroupId()
    var title by remember { mutableStateOf(groupSnapshot.title) }
    var description by remember { mutableStateOf(groupSnapshot.description) }
    var day by remember { mutableStateOf(groupSnapshot.eventDateTime.dayOfMonth.toString()) }
    var month by remember { mutableStateOf(groupSnapshot.eventDateTime.monthValue.toString()) }
    var year by remember { mutableStateOf(groupSnapshot.eventDateTime.year.toString()) }
    val timePickerState = rememberTimePickerState(
        initialHour = groupSnapshot.eventDateTime.hour,
        initialMinute = groupSnapshot.eventDateTime.minute
    )
    var showInfoDialog by remember { mutableStateOf(false) }
    var showNoGeofenceDialog by remember { mutableStateOf(false) }
    // Initialize separately: track the already-saved group geofence vs a new personal selection
    var selectedGeofenceId by remember {
        mutableStateOf(groupSnapshot.geofence?.id ?: "")
    }
    // Track whether the current selection is already a saved group copy (starts with "group_")
    // vs a freshly picked personal geofence
    var isExistingGroupGeofence by remember {
        mutableStateOf(groupSnapshot.geofence?.id?.startsWith("group_") == true)
    }

    val initialMemberNames = groupSnapshot.groupMemberNames.toTypedArray()
    val memberNames = remember { mutableStateListOf(*groupSnapshot.groupMemberNames.toTypedArray()) }

    // track members the admin explicitly removed via the Remove button.
    // updateGroupMetadata receives ONLY this set and nulls out exactly those UIDs.
    // We never diff live Firebase vs local state — that was the root cause of the bug
    // where a member accepting an invitation got evicted when the admin pressed Save.
    val explicitlyRemovedMembers = remember { mutableStateOf(setOf<String>()) }
    val removedMemberNames = remember { mutableStateMapOf<String, String>() }
    // Cache of all resolved display names so Remove can snapshot the name immediately
    val resolvedDisplayNames = remember { mutableStateMapOf<String, String>() }

    // pendingInvitations driven entirely by live Firebase observer.
    val pendingInvitations = remember { mutableStateListOf<String>() }

    LaunchedEffect(groupId) {
        FirebaseRepository.observePendingInvitationsSentByMe(groupId).collect { invites ->
            pendingInvitations.clear()
            pendingInvitations.addAll(invites)
        }
    }

    val memberRoles = remember { mutableStateMapOf<String, GroupRole>().apply { putAll(groupSnapshot.memberRoles) } }
    val sharingEnabled = remember { mutableStateMapOf<String, Boolean>().apply { putAll(groupSnapshot.locationSharingEnabled) } }
    val canToggle = remember { mutableStateMapOf<String, Boolean>().apply { putAll(groupSnapshot.canToggleSharing) } }
    val adminApplications = remember { mutableStateListOf(*groupSnapshot.adminApplications.toTypedArray()) }
    val appVotes = remember {
        mutableStateMapOf<String, MutableSet<String>>().apply {
            groupSnapshot.adminApplicationVotes.forEach { (k, v) -> put(k, v.toMutableSet()) }
        }
    }
    val removalVotes = remember {
        mutableStateMapOf<String, MutableSet<String>>().apply {
            groupSnapshot.votesToRemoveAdmin.forEach { (k, v) -> put(k, v.toMutableSet()) }
        }
    }

    val currentUser = UserProfileObject.uid ?: ""
    val currentRole = memberRoles[currentUser] ?: GroupRole.MEMBER
    val currentIsAdmin = (currentRole == GroupRole.ADMIN || currentRole == GroupRole.SUPER_ADMIN)
    val currentIsSuperAdmin = currentRole == GroupRole.SUPER_ADMIN

    val accentColor = Color(0xFF007AFF)
    val destructiveColor = Color(0xFFFF3B30)
    val successColor = NICEGREEN_COLOUR
    val secondaryText = if (darkmode) Color(0xFF8E8E93) else Color(0xFF636366)
    val outlineColor = if (darkmode) ElectricYellow else SlateGrey

    //if no geofence selected/created
    if (showNoGeofenceDialog) {
        var noGeofences: Boolean = UserGeofencesDatabase.getAllGeofences().isEmpty()

        var actionString =
            if (noGeofences) "You haven't created any Geofences yet. Create one now?"
            else "You haven't selected a Geofence."
        var titleString =
            if (noGeofences) "No Geofences Found"
            //else if ()
            else "Select a Geofence"
        AlertDialog(
            onDismissRequest = { showNoGeofenceDialog = false },
            title = { Text(titleString,
                fontSize = MaterialTheme.typography.titleLarge.fontSize * X.value,
                ) },
            text = { Text(actionString,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value
            ) },
            confirmButton = {
                if (noGeofences) {
                    TextButton(onClick = {
                        showNoGeofenceDialog = false
                        val intent = Intent(context, MapActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Text("Add Geofence",
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoGeofenceDialog = false }) {
                    Text("Cancel",
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value
                    )
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Group",
                    fontSize = MaterialTheme.typography.titleLarge.fontSize * X.value
                ) },
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

                // ==================== Edit Group Info Button =================
                Button(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Group Info",
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value
                    )
                }

                HorizontalDivider()

                // ==================== Add Member (Invite) ====================
                if (currentIsAdmin) {
                    Text("Invite Member", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * X.value)
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
                                    val result = FirebaseRepository.findUserByUsernameOrEmail(
                                        searchQuery.trim()
                                    )
                                    when {
                                        result == null ->
                                            searchError = "User not found"
                                        initialMemberNames.contains(result.first) ->
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
                        ) { Text(if (isSearching) "..." else "Search",
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value
                        ) }
                    }

                    if (searchError.isNotEmpty()) {
                        Text(searchError, color = destructiveColor, fontSize = 12.sp * X.value)
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
                                    Text(foundName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp * X.value)
                                    Text("Invite to group", fontSize = 11.sp * X.value, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                ) { Text("Invite",
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value
                                ) }
                            }
                        }
                    }
                    HorizontalDivider()
                }

                // ====================== Members Section ======================
                Text("Members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize * X.value)
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
                                    LaunchedEffect(member) {
                                        displayName = FirebaseRepository.getUsername(member)
                                        resolvedDisplayNames[member] = displayName
                                    }

                                    Text(
                                        text = displayName + if (isSelf) " (you)" else "",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp * X.value
                                    )
                                    Text(
                                        text = role.displayName,
                                        fontSize = 11.sp * X.value,
                                        color = when (role) {
                                            GroupRole.SUPER_ADMIN -> SUPERADMIN_ROLE_COLOUR
                                            GroupRole.ADMIN -> ADMIN_ROLE_COLOUR
                                            GroupRole.MEMBER -> MEMBER_ROLE_COLOUR
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
                                        Text(if (isSharing) "Stop Sharing" else "Share Location",
                                            fontSize = 11.sp * X.value)
                                    }
                                }
                                if (!isSelf && currentIsAdmin) {
                                    // Show remove button for all non-SuperAdmin members (including Admins)
                                    if (!isTargetSuperAdmin) {
                                        OutlinedButton(
                                            onClick = {
                                                explicitlyRemovedMembers.value += member
                                                // Snapshot the display name before removing from memberNames
                                                removedMemberNames[member] = resolvedDisplayNames[member] ?: member
                                                memberNames.remove(member)
                                                memberRoles.remove(member)
                                                sharingEnabled.remove(member)
                                                canToggle.remove(member)
                                            },
                                            modifier = Modifier.height(32.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, destructiveColor.copy(alpha = 0.5f))
                                        ) {
                                            Text("Remove", fontSize = 11.sp * X.value, color = destructiveColor)
                                        }
                                    }

                                    // Promote button only for MEMBER
                                    if (role == GroupRole.MEMBER) {
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
                                            Text("Promote", fontSize = 11.sp * X.value, color = accentColor)
                                        }
                                    }

                                    // Demote button for ADMIN targets (not SUPER_ADMIN)
                                    if (isTargetAdmin && currentIsSuperAdmin) {
                                        OutlinedButton(
                                            onClick = {
                                                // Only update local state — Firebase write happens on Save
                                                memberRoles[member] = GroupRole.MEMBER
                                                canToggle[member] = false
                                            },
                                            modifier = Modifier.height(32.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, destructiveColor.copy(alpha = 0.5f))
                                        ) {
                                            Text("Demote", fontSize = 11.sp * X.value, color = destructiveColor)
                                        }
                                    }
                                }
                                // For super admins, show immediate remove button (only for other super admins)
                                if (!isSelf && isTargetSuperAdmin && currentIsSuperAdmin) {
                                    OutlinedButton(
                                        onClick = {
                                            explicitlyRemovedMembers.value += member
                                            removedMemberNames[member] = resolvedDisplayNames[member] ?: member
                                            memberNames.remove(member)
                                            memberRoles.remove(member)
                                            sharingEnabled.remove(member)
                                            canToggle.remove(member)
                                        },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, destructiveColor.copy(alpha = 0.5f))
                                    ) {
                                        Text("Remove", fontSize = 11.sp * X.value, color = destructiveColor)
                                    }
                                }
                            }
                        }
                    }
                }

                // ===================== Pending Removals ======================
                if (explicitlyRemovedMembers.value.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        "Pending Removals",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * X.value,
                        color = destructiveColor
                    )
                    Text(
                        "These members will be removed when you tap Save Changes.",
                        fontSize = 11.sp * X.value,
                        color = secondaryText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    explicitlyRemovedMembers.value.forEach { removedUid ->
                        val name = removedMemberNames[removedUid] ?: removedUid
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = destructiveColor.copy(alpha = 0.08f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PersonOff,
                                    contentDescription = null,
                                    tint = destructiveColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = name,
                                    fontSize = 13.sp * X.value,
                                    color = destructiveColor,
                                    modifier = Modifier.weight(1f)
                                )
                                // Allow undoing the removal before Save
                                TextButton(
                                    onClick = {
                                        explicitlyRemovedMembers.value -= removedUid
                                        removedMemberNames.remove(removedUid)
                                        // Restore the member to the list
                                        val originalRole = groupSnapshot.memberRoles[removedUid] ?: GroupRole.MEMBER
                                        memberNames.add(removedUid)
                                        memberRoles[removedUid] = originalRole
                                        sharingEnabled[removedUid] = groupSnapshot.locationSharingEnabled[removedUid] ?: false
                                        canToggle[removedUid] = groupSnapshot.canToggleSharing[removedUid] ?: false
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Undo", fontSize = 11.sp * X.value, color = destructiveColor)
                                }
                            }
                        }
                    }
                }

                // ================= pending Admin Applications ================
                if (currentIsAdmin && adminApplications.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Pending Admin Applications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * X.value)
                    adminApplications.toList().forEach { applicant ->
                        val votes = appVotes.getOrPut(applicant) { mutableSetOf() }
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
                                    Text(appName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp * X.value)
                                }
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                FirebaseRepository.promoteToAdmin(groupId, applicant)
                                                FirebaseRepository.declineAdminApplication(groupId, applicant) // cleans up the application
                                                // Update local UI state immediately
                                                memberRoles[applicant] = GroupRole.ADMIN
                                                canToggle[applicant] = true
                                                adminApplications.remove(applicant)
                                                appVotes.remove(applicant)
                                            } catch (e: Exception) { }
                                        }
                                    }
                                ) {
                                    Text("Approve", color = successColor,
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value)
                                }
                                // Deny now calls FirebaseRepository.declineAdminApplication
                                // (which uses groupRef with relative paths, admin safe).
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                FirebaseRepository.declineAdminApplication(
                                                    groupId, applicant)
                                                adminApplications.remove(applicant)
                                                appVotes.remove(applicant)
                                            } catch (e: Exception) { }
                                        }
                                    }
                                ) {
                                    Text("Deny", color = destructiveColor,
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value)
                                }
                            }
                        }
                    }
                }

                // ================= Pending Invitations Section ===============
                if (pendingInvitations.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Pending Invitations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * X.value)
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
                                Text(invitedName, modifier = Modifier.weight(1f), fontSize = 14.sp * X.value)
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

                            // Create a copy of the geofence with a new group-specific ID
                            val selectedGeofence = when {
                                // User picked a personal geofence from the dropdown — make a deep copy
                                selectedGeofenceId.isNotEmpty() && !isExistingGroupGeofence -> {
                                    UserGeofencesDatabase.getAllGeofences()
                                        .find { it.id == selectedGeofenceId }
                                        ?.let { originalGeofence ->
                                            //save deep copy with group identifier
                                            val groupGeofenceId = "group_${groupId}_${System.currentTimeMillis()}"
                                            originalGeofence.copy(id = groupGeofenceId, geoAlarmId = null)
                                        }
                                }
                                // Already a saved group geofence copy — preserve it as-is
                                isExistingGroupGeofence -> groupSnapshot.geofence
                                // No geofence selected
                                else -> null
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

                            // Fire demotions for any member whose role was locally changed
                            groupSnapshot.memberRoles.forEach { (uid, originalRole) ->
                                val newRole = memberRoles[uid]
                                if (originalRole == GroupRole.ADMIN && newRole == GroupRole.MEMBER) {
                                    runCatching {
                                        FirebaseRepository.voteToRemoveAdmin(groupId, uid, currentUser)
                                    }
                                }
                            }

                            CurrentGroupObject.loadCurrentGroupEntry(updated)
                            onFinish()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(bottom = 32.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text("Save Changes",
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value
                    )
                }

            }
        }

        // ==================== Group Info Dialog ======================
        if (showInfoDialog) {
            val focusManager = LocalFocusManager.current
            val context = LocalContext.current

            // Date and Time state
            var showDatePicker by remember { mutableStateOf(false) }
            var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

            // Initialize selectedDate from existing day/month/year values
            LaunchedEffect(Unit) {
                try {
                    val dayInt = day.toIntOrNull()
                    val monthInt = month.toIntOrNull()
                    val yearInt = year.toIntOrNull()
                    if (dayInt != null && monthInt != null && yearInt != null) {
                        selectedDate = LocalDate.of(yearInt, monthInt, dayInt)
                    }
                } catch (e: Exception) {
                    // invalid date, ignore
                }
            }

            // FIELD COLOURS
            val outlineColor = if (darkmode) ElectricYellow else SlateGrey
            val yellowOutlineColorsTextField = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = outlineColor,
                unfocusedBorderColor = outlineColor.copy(alpha = 0.4f),
                focusedLabelColor = outlineColor,
                unfocusedLabelColor = Color.Gray,
                cursorColor = outlineColor
            )

            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = {
                    Text(
                        text = "Edit Group Info",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = MaterialTheme.typography.titleLarge.fontSize * X.value
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                focusManager.clearFocus()
                            }
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                    ) {
                        // Group Title
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Group Title",
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value) },
                            placeholder = { Text("Enter group title",
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value
                            ) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = yellowOutlineColorsTextField
                        )

                        // Description
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description",
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value) },
                            placeholder = { Text("Enter group description",
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp),
                            minLines = 2,
                            maxLines = 3,
                            colors = yellowOutlineColorsTextField
                        )

                        // Target Date & Time Section Header
                        Text(
                            text = "Target Date & Time",
                            fontSize = 14.sp * X.value,
                            fontWeight = FontWeight.Medium,
                            color = outlineColor,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        // Date Selection Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Date Picker Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showDatePicker = true }
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Event,
                                                contentDescription = null,
                                                tint = outlineColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = selectedDate?.format(
                                                    java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")
                                                ) ?: "Select date",
                                                color = if (selectedDate != null)
                                                    MaterialTheme.colorScheme.onSurface
                                                else
                                                    Color.Gray,
                                                fontSize = 14.sp * X.value
                                            )
                                        }
                                        if (selectedDate != null) {
                                            IconButton(
                                                onClick = {
                                                    selectedDate = null
                                                    day = ""
                                                    month = ""
                                                    year = ""
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                null,
                                                tint = outlineColor
                                            )
                                        }
                                    }
                                }

                                // Time Display (using GeoAlarmCard format - no "hrs" suffix)
                                var showTimePickerInternal by remember { mutableStateOf(false) }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showTimePickerInternal = true }
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = outlineColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 14.sp * X.value
                                            )
                                        }
                                        Icon(
                                            Icons.Default.Edit,
                                            null,
                                            tint = outlineColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (showTimePickerInternal) {
                                    TimePickerDialog(
                                        onDismiss = { showTimePickerInternal = false },
                                        onConfirm = { hour, minute ->
                                            // timePickerState is automatically updated by the TimePicker
                                            showTimePickerInternal = false
                                        },
                                        initialHour = timePickerState.hour,
                                        initialMinute = timePickerState.minute,
                                        outlineColor = outlineColor
                                    )
                                }
                            }
                        }

                        // Geofence Section Header
                        Text(
                            text = "Geofence",
                            fontSize = 14.sp * X.value,
                            fontWeight = FontWeight.Medium,
                            color = outlineColor,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        // Geofence Selection
                        var gfExpanded by remember { mutableStateOf(false) }
                        val selectedGeofenceName = when {
                            isExistingGroupGeofence -> groupSnapshot.geofence?.name ?: "Select a geofence"
                            selectedGeofenceId.isNotEmpty() -> UserGeofencesDatabase.getAllGeofences()
                                .find { it.id == selectedGeofenceId }?.name ?: "Select a geofence"
                            else -> "Select a geofence"
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Selected geofence display
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (UserGeofencesDatabase.getAllGeofences().isEmpty()) {
                                                    showNoGeofenceDialog = true
                                                } else {
                                                    gfExpanded = true
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = if (selectedGeofenceId.isNotEmpty() || isExistingGroupGeofence)
                                                    outlineColor else Color.Gray, //ui stuff: grey for no geofence selected
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = selectedGeofenceName,
                                                color = if (selectedGeofenceId.isNotEmpty() || isExistingGroupGeofence)
                                                    MaterialTheme.colorScheme.onSurface
                                                else Color.Gray, //ui stuff: grey for no geofence selected
                                                fontSize = 14.sp * X.value
                                            )
                                        }
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            null,
                                            tint = outlineColor
                                        )
                                    }
                                }

                                // Dropdown Menu
                                DropdownMenu(
                                    expanded = gfExpanded,
                                    onDismissRequest = { gfExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    UserGeofencesDatabase.getAllGeofences().forEach { gf ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.LocationOn,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = outlineColor
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(gf.name,
                                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value)
                                                }
                                            },
                                            onClick = {
                                                selectedGeofenceId = gf.id
                                                isExistingGroupGeofence = false
                                                gfExpanded = false
                                            }
                                        )
                                    }

                                    HorizontalDivider()

                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = outlineColor
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Create New Geofence", color = outlineColor,
                                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value)
                                            }
                                        },
                                        onClick = {
                                            gfExpanded = false
                                            val intent = Intent(context, MapActivity::class.java)
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Update day/month/year from selectedDate if available
                            selectedDate?.let { date ->
                                day = date.dayOfMonth.toString()
                                month = date.monthValue.toString()
                                year = date.year.toString()
                            }
                            showInfoDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = outlineColor,
                            contentColor = if (darkmode) Color.Black else Color.White,
                            disabledContainerColor = outlineColor.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Changes",
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showInfoDialog = false },
                        border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel",
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value)
                    }
                }
            )

            // Date Picker Dialog using helper function
            if (showDatePicker) {
                DatePickerDialog(
                    onDismiss = { showDatePicker = false },
                    onConfirm = { date ->
                        selectedDate = date
                        showDatePicker = false
                    },
                    initialDate = selectedDate ?: LocalDate.now(),
                    outlineColor = outlineColor
                )
            }
        }
    }
}