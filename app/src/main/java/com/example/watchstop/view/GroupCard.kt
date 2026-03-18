package com.example.watchstop.view

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.activities.EditGroupActivity
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.data.CurrentGroupObject
import com.example.watchstop.model.GroupEntry
import com.example.watchstop.model.GroupRole
import com.example.watchstop.model.TripStatus
import com.example.watchstop.data.FirebaseRepository
import com.example.watchstop.view.ui.theme.WatchStopTheme
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun GroupCard(
    groupId: String,
    groupEntryParameter: GroupEntry,
    onEdited: (GroupEntry) -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snapshot = remember { CurrentGroupObject.getCurrentGroupEntry() }

    // Current user's UID — read at composition, stable after login
    var myName by remember { mutableStateOf("") }
    val currentUid = UserProfileObject.uid ?: ""

    // Expansion state for the entire card
    var isExpanded by remember { mutableStateOf(false) }

    //members stuff
    val currentUser = UserProfileObject.uid ?: ""
    val memberNames = remember { mutableStateListOf(*snapshot.groupMemberNames.toTypedArray()) }
    var showRemovalDialogFor by remember { mutableStateOf<String?>(null) }
    val memberRoles = remember { mutableStateMapOf<String, GroupRole>().apply { putAll(snapshot.memberRoles) } }
    val removalVotes = remember {
        mutableStateMapOf<String, MutableSet<String>>().apply {
            snapshot.votesToRemoveAdmin.forEach { (k, v) -> put(k, v.toMutableSet()) }
        }
    }
    val canToggle = remember { mutableStateMapOf<String, Boolean>().apply { putAll(snapshot.canToggleSharing) } }

    // Expansion state for description
    var expanded by remember { mutableStateOf(false) }
    var isDescriptionOverflowing by remember { mutableStateOf(false) }

    // Local copy so optimistic UI updates feel instant before Firebase confirms
    var group by remember(groupEntryParameter) { mutableStateOf(GroupEntry(groupEntryParameter)) }

    val userRole = group.memberRoles[currentUid] ?: GroupRole.MEMBER
    val isAdmin = userRole == GroupRole.ADMIN || userRole == GroupRole.SUPER_ADMIN
    val isSuperAdmin = userRole == GroupRole.SUPER_ADMIN

    // Does this group have any super admin besides the current user?
    val hasSuperAdmin = group.memberRoles.any { (uid, role) ->
        role == GroupRole.SUPER_ADMIN
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Only update if EditGroupActivity actually saved something
        if (CurrentGroupObject.activated) {
            val updated = CurrentGroupObject.getCurrentGroupEntry()
            group = GroupEntry(updated)
            onEdited(group)
        }
        // If it crashed/returned without saving, group stays as-is
    }

    val backgroundColor = if (darkmode) Color(0xFF1C1C1E) else Color.White
    val primaryText = if (darkmode) Color.White else Color.Black
    val secondaryText = if (darkmode) Color(0xFF8E8E93) else Color(0xFF636366)
    val accentColor = Color(0xFF007AFF)
    val destructiveColor = Color(0xFFFF3B30)
    val successColor = Color(0xFF34C759)
    val warningColor = Color(0xFFFF9500)

    WatchStopTheme(darkTheme = darkmode) {
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 16.dp)
                .animateContentSize()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // ── Title / Toggle Row ──────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left part: clickable toggle area
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isExpanded = !isExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isExpanded) {
                            // Compact View: Name, Role, Date/Time in one row
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold),
                                color = primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = userRole.displayName,
                                fontSize = 11.sp,
                                color = when (userRole) {
                                    GroupRole.SUPER_ADMIN -> Color(0xFFFFCC00)
                                    GroupRole.ADMIN -> accentColor
                                    GroupRole.MEMBER -> secondaryText
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = group.eventDateTime.format(DateTimeFormatter.ofPattern("dd MMM, HH:mm")),
                                fontSize = 11.sp,
                                color = secondaryText,
                                maxLines = 1
                            )
                        } else {
                            // Expanded Header View: Name and Role
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold),
                                    color = primaryText
                                )
                                Text(
                                    text = userRole.displayName,
                                    fontSize = 11.sp,
                                    color = when (userRole) {
                                        GroupRole.SUPER_ADMIN -> Color(0xFFFFCC00)
                                        GroupRole.ADMIN -> accentColor
                                        GroupRole.MEMBER -> secondaryText
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        
                        // Expand/Collapse hint arrow
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Retract" else "Expand",
                            tint = secondaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Leave / Disband Actions (only visible when expanded)
                    if (isExpanded) {
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        val canLeave = when {
                            isSuperAdmin -> false
                            isAdmin -> hasSuperAdmin
                            else -> true
                        }

                        if (isSuperAdmin) {
                            Text(
                                text = "Disband",
                                color = destructiveColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        FirebaseRepository.deleteGroup(groupId)
                                    }
                                }
                            )
                        } else if (canLeave) {
                            Text(
                                text = "Leave",
                                color = destructiveColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        FirebaseRepository.leaveGroup(groupId, currentUid)
                                    }
                                }
                            )
                        }
                    }
                }

                // ── Detailed Content (only visible when expanded) ───────────
                if (isExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Date / Time Section ─────────────────────────────────────
                    MiniHeader("Event date & Time", secondaryText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SketchTag(group.eventDateTime.format(
                            DateTimeFormatter.ofPattern("dd MMM yyyy")))
                        SketchTag(group.eventDateTime.format(
                            DateTimeFormatter.ofPattern("HH:mm 'hrs'")))
                    }

                    // ── Description (Expandable) ────────────────────────────────
                    MiniHeader("Group/Event Description", secondaryText)
                    if (group.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = secondaryText.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = secondaryText.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = isDescriptionOverflowing || expanded) {
                                    expanded = !expanded
                                }
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = group.description,
                                    fontSize = 14.sp,
                                    color = primaryText.copy(alpha = 0.9f),
                                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                                    overflow = TextOverflow.Ellipsis,
                                    onTextLayout = { textLayoutResult ->
                                        if (!expanded) {
                                            isDescriptionOverflowing = textLayoutResult.hasVisualOverflow
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (isDescriptionOverflowing || expanded) {
                                    Text(
                                        text = if (expanded) "Tap to retract" else "Tap to expand",
                                        fontSize = 11.sp,
                                        color = secondaryText,
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ── Member List with Status Dashboard ──────────────────────
                    MiniHeader("Members", secondaryText)
                    group.groupMemberNames.forEach { memberUid ->
                        val memberRole = group.memberRoles[memberUid] ?: GroupRole.MEMBER
                        val isSharing = group.locationSharingEnabled[memberUid] ?: false
                        val status = group.tripStatus[memberUid] ?: TripStatus.INACTIVE

                        // Resolve UID → display name async
                        var displayName by remember(memberUid) { mutableStateOf(memberUid) }
                        LaunchedEffect(memberUid) {
                            displayName = FirebaseRepository.getUsername(memberUid)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Location sharing indicator
                            Icon(
                                imageVector = if (isSharing) Icons.Default.LocationOn
                                else Icons.Default.LocationOff,
                                contentDescription = null,
                                tint = if (isSharing) successColor else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName + if (memberUid == currentUid) " (you)" else "",
                                    fontSize = 13.sp,
                                    color = primaryText
                                )
                                // Trip status badge
                                Text(
                                    text = when (status) {
                                        TripStatus.TRAVELLING -> "Travelling"
                                        TripStatus.ARRIVED -> "Arrived"
                                        TripStatus.INACTIVE -> "Inactive"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (status) {
                                        TripStatus.TRAVELLING -> warningColor
                                        TripStatus.ARRIVED -> successColor
                                        TripStatus.INACTIVE -> secondaryText
                                    }
                                )
                            }

                            // Role badge
                            Text(
                                text = memberRole.displayName,
                                fontSize = 11.sp,
                                color = when (memberRole) {
                                    GroupRole.SUPER_ADMIN -> Color(0xFFFFCC00)
                                    GroupRole.ADMIN -> accentColor
                                    GroupRole.MEMBER -> secondaryText
                                }
                            )

                            // Admin removal buttons - different behavior based on role
                            if (memberUid != currentUid) {
                                val hasVoted = group.hasVotedToRemove(memberUid, currentUid)
                                val voteCount = group.voteCountToRemove(memberUid)
                                val needed = group.votesNeededToRemove(memberUid)

                                // Only show for ADMIN targets (not SUPER_ADMIN)
                                if (memberRole == GroupRole.ADMIN) {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                if (isSuperAdmin) {
                                                    // Super admin can directly demote admin without vote
                                                    showRemovalDialogFor = memberUid
                                                } else {
                                                    try {
                                                        // Cast the vote - this will now auto-demote if threshold met
                                                        val thresholdMet = FirebaseRepository.voteToRemoveAdmin(
                                                            groupId, memberUid, currentUid
                                                        )

                                                        // Update local vote state
                                                        val updated = GroupEntry(group)

                                                        if (thresholdMet) {
                                                            // If threshold was met, the user was already demoted
                                                            // Update local state to reflect demotion
                                                            updated.memberRoles[memberUid] = GroupRole.MEMBER
                                                            updated.canToggleSharing[memberUid] = false
                                                            updated.votesToRemoveAdmin.remove(memberUid)
                                                        } else {
                                                            // Just add the vote
                                                            updated.voteToRemoveAdmin(memberUid, currentUid)
                                                        }

                                                        group = updated

                                                        // Still show dialog if threshold met (for confirmation)
                                                        if (thresholdMet) {
                                                            showRemovalDialogFor = memberUid
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("GroupCard", "Error voting", e)
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp),
                                        enabled = if (isSuperAdmin) true else !hasVoted
                                    ) {
                                        BadgedIcon(
                                            hasVoted = hasVoted,
                                            voteCount = voteCount,
                                            needed = needed,
                                            isSuperAdmin = isSuperAdmin,
                                            isTargetSuperAdmin = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Locations Section ───────────────────────────────────────
                    MiniHeader("Locations", secondaryText)

                    val isSharing = group.locationSharingEnabled[currentUid] ?: false
                    val canCurrentToggle = group.canToggleSharing[currentUid] ?: false
                    val currentStatus = group.tripStatus[currentUid] ?: TripStatus.INACTIVE

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Share / Stop Sharing button
                        SketchButton(
                            text = when {
                                !canCurrentToggle -> "Sharing Locked"
                                isSharing -> "Stop Sharing"
                                else -> "Share Location"
                            },
                            onClick = {
                                if (canCurrentToggle) {
                                    coroutineScope.launch {
                                        try {
                                            FirebaseRepository.toggleLocationSharing(
                                                groupId, currentUid, !isSharing)
                                            val updated = GroupEntry(group)
                                            updated.setSharing(currentUid, !isSharing)
                                            group = updated
                                        } catch (e: Exception) { /* sharing locked */ }
                                    }
                                }
                            }
                        )

                        // Trip status cycle: Inactive → Travelling → Arrived → Inactive
                        SketchButton(
                            text = when (currentStatus) {
                                TripStatus.INACTIVE -> "Start Trip"
                                TripStatus.TRAVELLING -> "Mark Arrived"
                                TripStatus.ARRIVED -> "End Trip"
                            },
                            onClick = {
                                val next = when (currentStatus) {
                                    TripStatus.INACTIVE -> TripStatus.TRAVELLING
                                    TripStatus.TRAVELLING -> TripStatus.ARRIVED
                                    TripStatus.ARRIVED -> TripStatus.INACTIVE
                                }
                                coroutineScope.launch {
                                    FirebaseRepository.setTripStatus(groupId, currentUid, next)
                                    val updated = GroupEntry(group)
                                    updated.setTripStatus(currentUid, next)
                                    group = updated
                                }
                            }
                        )
                        
                        // Show Map button (currently doing nothing)
                        SketchButton(
                            text = "Show Map",
                            onClick = { /* TODO: Implement map view logic */ }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Management Section ──────────────────────────────────────
                    MiniHeader("Management", secondaryText)

                    // Manage group (admins) or Apply for admin (members)
                    if (isAdmin) {
                        SketchButton(
                            text = "Manage Group",
                            onClick = {
                                CurrentGroupObject.loadCurrentGroupEntry(group, groupId)
                                launcher.launch(Intent(context, EditGroupActivity::class.java))
                            }
                        )
                    } else if (!group.adminApplications.contains(currentUid)) {
                        SketchButton(
                            text = "Apply for Admin",
                            onClick = {
                                coroutineScope.launch {
                                    FirebaseRepository.applyForAdmin(groupId, currentUid)
                                    val updated = GroupEntry(group)
                                    updated.applyForAdmin(currentUid)
                                    group = updated
                                }
                            }
                        )
                    }

                    // ── My pending application banner ───────────────────────────
                    if (group.adminApplications.contains(currentUid)) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val voteCount = group.adminApplicationVoteCount(currentUid)
                        val needed = group.votesNeededForApplication(currentUid)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Admin application pending", fontSize = 12.sp,
                                color = accentColor, fontWeight = FontWeight.Bold)
                            Text("$voteCount / $needed votes", fontSize = 12.sp, color = accentColor)
                        }
                    }

                    // ── Admin Removal Dialog ───────────────────────────────────────
                    showRemovalDialogFor?.let { targetUid ->
                        val targetRole = group.memberRoles[targetUid] ?: GroupRole.MEMBER
                        val isTargetSuperAdmin = targetRole == GroupRole.SUPER_ADMIN
                        val isTargetAdmin = targetRole == GroupRole.ADMIN
                        val voteCount = group.voteCountToRemove(targetUid)
                        val needed = group.votesNeededToRemove(targetUid)

                        AlertDialog(
                            onDismissRequest = { showRemovalDialogFor = null },
                            title = {
                                Text(
                                    when {
                                        isTargetAdmin && isSuperAdmin -> "Demote Admin"
                                        else -> "Complete Admin Demotion"
                                    }
                                )
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    when {
                                        // Direct admin removal by super admin
                                        isSuperAdmin -> {
                                            Text("You are about to demote an Admin.")
                                            Text("This operation will be conducted using SuperAdmin privileges.")
                                        }

                                        // Vote-based removal completed
                                        else -> {
                                            Text("The vote to demote this Admin has reached the required threshold (${voteCount}/${needed}).")
                                            Text("Do you want to complete the demotion process?")
                                        }
                                    }

                                    // Show target member name
                                    var targetName by remember(targetUid) { mutableStateOf(targetUid) }
                                    LaunchedEffect(targetUid) {
                                        targetName = FirebaseRepository.getUsername(targetUid)
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Text(
                                            text = "Name of Target: $targetName",
                                            modifier = Modifier.padding(12.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // For non-super admin removal by super admin, show simple confirmation field
                                    if (!isTargetSuperAdmin && isSuperAdmin) {
                                        Text("Type 'DEMOTE' to confirm:")
                                        OutlinedTextField(
                                            value = myName,
                                            onValueChange = { myName = it },
                                            label = { Text("Confirmation") },
                                            singleLine = true
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                when {
                                                    // Direct admin demotion by super admin
                                                    isTargetAdmin && isSuperAdmin -> {
                                                        FirebaseRepository.voteToRemoveAdmin(groupId, targetUid, currentUid)

                                                        // Update local state
                                                        val updated = GroupEntry(group)
                                                        updated.memberRoles[targetUid] = GroupRole.MEMBER
                                                        updated.canToggleSharing[targetUid] = false
                                                        updated.votesToRemoveAdmin.remove(targetUid)
                                                        group = updated
                                                        onEdited(updated)
                                                    }

                                                    // Vote-based demotion completed
                                                    else -> {
                                                        // The admin might already be demoted by the auto-demotion
                                                        // Check current role from Firebase to be sure
                                                        val freshGroup = FirebaseRepository.getGroup(groupId)
                                                        if (freshGroup != null) {
                                                            val currentTargetRole = freshGroup.memberRoles[targetUid]
                                                            if (currentTargetRole == GroupRole.MEMBER) {
                                                                // Already demoted, just update local state
                                                                val updated = GroupEntry(group)
                                                                updated.memberRoles[targetUid] = GroupRole.MEMBER
                                                                updated.canToggleSharing[targetUid] = false
                                                                updated.votesToRemoveAdmin.remove(targetUid)
                                                                group = updated
                                                                onEdited(updated)
                                                            } else {
                                                                // Not demoted yet, call the function
                                                                FirebaseRepository.voteToRemoveAdmin(groupId, targetUid, currentUid)

                                                                val updated = GroupEntry(group)
                                                                updated.memberRoles[targetUid] = GroupRole.MEMBER
                                                                updated.canToggleSharing[targetUid] = false
                                                                updated.votesToRemoveAdmin.remove(targetUid)
                                                                group = updated
                                                                onEdited(updated)
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("GroupCard", "Error in dialog action", e)
                                            }
                                        }
                                        showRemovalDialogFor = null
                                        myName = ""
                                    },
                                    enabled = when {
                                        isTargetSuperAdmin && isSuperAdmin -> myName == "REMOVE"
                                        isTargetAdmin && isSuperAdmin -> myName == "DEMOTE"
                                        else -> true
                                    }
                                ) {
                                    Text(
                                        when {
                                            isTargetSuperAdmin -> "Remove"
                                            else -> "Demote to Member"
                                        }
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showRemovalDialogFor = null
                                    myName = ""
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    // ── Pending applications visible to admins ──────────────────
                    if (isAdmin) {
                        val pending = group.adminApplications.filter { it != currentUid }
                        if (pending.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(6.dp))
                            MiniHeader("Pending Admin Applications", secondaryText)

                            pending.forEach { applicantUid ->
                                val voteCount = group.adminApplicationVoteCount(applicantUid)
                                val needed = group.votesNeededForApplication(applicantUid)
                                val hasVoted = group.adminApplicationVotes[applicantUid]
                                    ?.contains(currentUid) == true

                                var applicantName by remember(applicantUid) {
                                    mutableStateOf(applicantUid) }
                                LaunchedEffect(applicantUid) {
                                    applicantName = FirebaseRepository.getUsername(applicantUid)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(applicantName, fontSize = 13.sp, color = primaryText,
                                        modifier = Modifier.weight(1f))
                                    Text("$voteCount/$needed", fontSize = 12.sp, color = secondaryText)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                FirebaseRepository.voteForAdminApplication(
                                                    groupId, applicantUid, currentUid)
                                                val updated = GroupEntry(group)
                                                updated.voteForAdminApplication(applicantUid, currentUid)
                                                group = updated
                                                // voteForAdminApplication uses groupRef.updateChildren
                                                // (relative paths) which admins can write — safe.
                                                onEdited(updated)
                                            }
                                        },
                                        enabled = !hasVoted,
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = if (hasVoted) "Voted" else "Approve",
                                            color = if (hasVoted) Color.Gray else successColor,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Shared small components ──────────────────────────────────────────────────

@Composable
fun MiniHeader(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = color,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
fun SketchTag(text: String, color: Color = Color.Gray) {
    Surface(
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp,
            color = if (color == Color.Gray) MaterialTheme.colorScheme.onSurfaceVariant else color
        )
    }
}

@Composable
fun SketchButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(34.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BadgedIcon(
    hasVoted: Boolean,
    voteCount: Int,
    needed: Int,
    isSuperAdmin: Boolean,
    isTargetSuperAdmin: Boolean
) {
    val destructiveColor = Color(0xFFFF3B30)

    Box(contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.HowToVote,
            contentDescription = when {
                isSuperAdmin && !isTargetSuperAdmin -> "Demote Admin"
                else -> "Vote to demote ($voteCount/$needed)"
            },
            tint = when {
                hasVoted -> Color.Gray
                isSuperAdmin -> destructiveColor
                else -> destructiveColor
            },
            modifier = Modifier.size(16.dp)
        )

        // Show vote count badge for non-super admins voting
        if (!isSuperAdmin && !isTargetSuperAdmin && !hasVoted) {
            Text(
                text = "$voteCount",
                fontSize = 8.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-4).dp)
                    .background(
                        color = destructiveColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            )
        }
    }
}