package com.example.watchstop.activities

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.model.CurrentGroupObject
import com.example.watchstop.model.GroupEntry
import com.example.watchstop.model.GroupRole
import com.example.watchstop.view.ui.theme.CarbonGrey
import com.example.watchstop.view.ui.theme.Purple40
import com.example.watchstop.view.ui.theme.WatchStopTheme

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

    var title by remember { mutableStateOf(snapshot.title) }
    var description by remember { mutableStateOf(snapshot.description) }
    var day by remember { mutableStateOf(snapshot.eventDateTime.dayOfMonth.toString()) }
    var month by remember { mutableStateOf(snapshot.eventDateTime.monthValue.toString()) }
    var year by remember { mutableStateOf(snapshot.eventDateTime.year.toString()) }
    val timePickerState = rememberTimePickerState(
        initialHour = snapshot.eventDateTime.hour,
        initialMinute = snapshot.eventDateTime.minute
    )
    var showTimePicker by remember { mutableStateOf(false) }

    // Working copies of all mutable group state
    val memberNames = remember { mutableStateListOf(*snapshot.groupMemberNames.toTypedArray()) }
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

    val currentUser = UserProfileObject.userName
    val currentRole = memberRoles[currentUser] ?: GroupRole.MEMBER
    val currentIsAdmin = currentRole == GroupRole.ADMIN || currentRole == GroupRole.SUPER_ADMIN
    val currentIsSuperAdmin = currentRole == GroupRole.SUPER_ADMIN

    val accentColor = Color(0xFF007AFF)
    val destructiveColor = Color(0xFFFF3B30)
    val successColor = Color(0xFF34C759)

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Group Details ──────────────────────────────────────────────
            Text("Group Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Group Title") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                minLines = 2
            )

            // ── Date / Time ────────────────────────────────────────────────
            Text("Target Date & Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(value = day, onValueChange = { if (it.length <= 2) day = it }, label = { Text("D") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = month, onValueChange = { if (it.length <= 2) month = it }, label = { Text("M") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = year, onValueChange = { if (it.length <= 4) year = it }, label = { Text("Y") }, modifier = Modifier.weight(1.5f))
                IconButton(onClick = { showTimePicker = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = "Set time")
                }
            }
            if (showTimePicker) {
                TimePickerDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = { TextButton(onClick = { showTimePicker = false }) { Text("OK") } },
                    title = { Text("Set Time") }
                ) { TimePicker(state = timePickerState) }
            }

            HorizontalDivider()

            // ── Members Section ────────────────────────────────────────────
            Text("Members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            memberNames.forEach { member ->
                val role = memberRoles[member] ?: GroupRole.MEMBER
                val isSharing = sharingEnabled[member] ?: false
                val memberCanToggle = canToggle[member] ?: false
                val isSelf = member == currentUser
                val isTargetSuperAdmin = role == GroupRole.SUPER_ADMIN
                val isTargetAdmin = role == GroupRole.ADMIN
                val removalVoteSet = removalVotes[member] ?: emptySet()
                val eligibleForRemoval = memberNames.filter { it != member }
                val removalNeeded = (eligibleForRemoval.size / 2) + 1
                val hasVotedRemoval = removalVoteSet.contains(currentUser)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Location icon
                            Icon(
                                imageVector = if (isSharing) Icons.Default.LocationOn else Icons.Default.LocationOff,
                                contentDescription = null,
                                tint = if (isSharing) successColor else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = member + if (isSelf) " (you)" else "",
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

                        // ── Actions Row ──────────────────────────────────
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Self: toggle own sharing if allowed
                            if (isSelf && memberCanToggle) {
                                OutlinedButton(
                                    onClick = { sharingEnabled[member] = !isSharing },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        if (isSharing) "Stop Sharing" else "Share Location",
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Admin controls over other members
                            if (!isSelf && currentIsAdmin) {
                                // Toggle sharing lock/unlock for members
                                if (role == GroupRole.MEMBER) {
                                    OutlinedButton(
                                        onClick = { canToggle[member] = !memberCanToggle },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            if (memberCanToggle) "Lock Sharing" else "Allow Sharing",
                                            fontSize = 11.sp
                                        )
                                    }

                                    // Force sharing on/off
                                    OutlinedButton(
                                        onClick = { sharingEnabled[member] = !isSharing },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            if (isSharing) "Force Off" else "Force On",
                                            fontSize = 11.sp,
                                            color = if (isSharing) destructiveColor else successColor
                                        )
                                    }
                                }

                                // Promote member to admin
                                if (role == GroupRole.MEMBER) {
                                    OutlinedButton(
                                        onClick = {
                                            memberRoles[member] = GroupRole.ADMIN
                                            canToggle[member] = true
                                            adminApplications.remove(member)
                                        },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Promote", fontSize = 11.sp, color = accentColor)
                                    }
                                }

                                // Remove member from group (not super admin)
                                if (!isTargetSuperAdmin) {
                                    OutlinedButton(
                                        onClick = {
                                            memberNames.remove(member)
                                            memberRoles.remove(member)
                                            sharingEnabled.remove(member)
                                            canToggle.remove(member)
                                        },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, destructiveColor.copy(alpha = 0.5f))
                                    ) {
                                        Text("Remove", fontSize = 11.sp, color = destructiveColor)
                                    }
                                }
                            }

                            // Vote to remove admin (any non-super-admin member, against an admin)
                            if (!isSelf && isTargetAdmin && !isTargetSuperAdmin) {
                                OutlinedButton(
                                    onClick = {
                                        if (!hasVotedRemoval) {
                                            val updated = removalVotes.getOrPut(member) { mutableSetOf() }
                                            updated.add(currentUser)
                                            if (updated.size >= removalNeeded) {
                                                memberRoles[member] = GroupRole.MEMBER
                                                canToggle[member] = false
                                                removalVotes.remove(member)
                                            }
                                        }
                                    },
                                    enabled = !hasVotedRemoval,
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (hasVotedRemoval) Color.Gray.copy(alpha = 0.4f) else destructiveColor.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Text(
                                        "Vote Remove (${removalVoteSet.size}/$removalNeeded)",
                                        fontSize = 11.sp,
                                        color = if (hasVotedRemoval) Color.Gray else destructiveColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Pending Admin Applications ─────────────────────────────────
            if (currentIsAdmin && adminApplications.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    "Pending Admin Applications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                adminApplications.toList().forEach { applicant ->
                    val votes = appVotes.getOrPut(applicant) { mutableSetOf() }
                    val needed = ((memberNames.size - 1) / 2) + 1
                    val hasVoted = votes.contains(currentUser)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = accentColor.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(applicant, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "${votes.size}/$needed approvals needed${if (currentIsSuperAdmin) " (you can approve instantly)" else ""}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    votes.add(currentUser)
                                    val approved = currentIsSuperAdmin || votes.size >= needed
                                    if (approved) {
                                        memberRoles[applicant] = GroupRole.ADMIN
                                        canToggle[applicant] = true
                                        adminApplications.remove(applicant)
                                        appVotes.remove(applicant)
                                    }
                                },
                                enabled = !hasVoted
                            ) {
                                Text(
                                    text = if (hasVoted) "Voted" else "Approve",
                                    color = if (hasVoted) Color.Gray else successColor
                                )
                            }
                            TextButton(
                                onClick = {
                                    adminApplications.remove(applicant)
                                    appVotes.remove(applicant)
                                }
                            ) {
                                Text("Deny", color = destructiveColor)
                            }
                        }
                    }
                }
            }

            // ── Save Button ────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    val newDateTime = try {
                        snapshot.eventDateTime
                            .withYear(year.toIntOrNull() ?: snapshot.eventDateTime.year)
                            .withMonth(month.toIntOrNull() ?: snapshot.eventDateTime.monthValue)
                            .withDayOfMonth(day.toIntOrNull() ?: snapshot.eventDateTime.dayOfMonth)
                            .withHour(timePickerState.hour)
                            .withMinute(timePickerState.minute)
                    } catch (e: Exception) { snapshot.eventDateTime }

                    val updated = GroupEntry(
                        title = title.trim(),
                        eventDateTime = newDateTime,
                        description = description,
                        groupMemberNames = memberNames.toMutableList(),
                        memberRoles = memberRoles.toMutableMap(),
                        locationSharingEnabled = sharingEnabled.toMutableMap(),
                        canToggleSharing = canToggle.toMutableMap(),
                        adminApplications = adminApplications.toMutableSet(),
                        adminApplicationVotes = appVotes.mapValues { it.value.toMutableSet() }.toMutableMap(),
                        votesToRemoveAdmin = removalVotes.mapValues { it.value.toMutableSet() }.toMutableMap()
                    )
                    CurrentGroupObject.loadCurrentGroupEntry(updated)
                    onFinish()
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(bottom = 24.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text("Save Changes")
            }
        }
    }
}