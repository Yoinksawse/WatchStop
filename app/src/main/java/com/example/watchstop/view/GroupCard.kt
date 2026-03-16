package com.example.watchstop.view

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    // Current user's UID — read at composition, stable after login
    val currentUid = UserProfileObject.uid ?: ""
    val userRole = groupEntryParameter.memberRoles[currentUid] ?: GroupRole.MEMBER
    val isAdmin = userRole == GroupRole.ADMIN || userRole == GroupRole.SUPER_ADMIN
    val isSuperAdmin = userRole == GroupRole.SUPER_ADMIN

    // Local copy so optimistic UI updates feel instant before Firebase confirms
    var group by remember(groupEntryParameter) { mutableStateOf(GroupEntry(groupEntryParameter)) }

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
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // ── Title Row ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Text(
                        text = when {
                            isSuperAdmin -> "Disband"
                            isAdmin -> "Leave"
                            else -> "Leave"
                        },
                        color = destructiveColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            //TODO: show confirmation dialogbox
                            coroutineScope.launch {
                                when {
                                    isSuperAdmin -> FirebaseRepository.deleteGroup(groupId)
                                    isAdmin -> onDeleted()
                                    else -> FirebaseRepository.removeMemberFromGroup(groupId, currentUid)
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Date / Time Tags ────────────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SketchTag(group.eventDateTime.format(
                        DateTimeFormatter.ofPattern("dd MMM yyyy")))
                    SketchTag(group.eventDateTime.format(
                        DateTimeFormatter.ofPattern("HH:mm 'hrs'")))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Member List with Status Dashboard ──────────────────────
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

                        // Admin can lock/allow sharing for members
                        if (isAdmin && memberUid != currentUid && memberRole == GroupRole.MEMBER) {
                            val memberCanToggle = group.canToggleSharing[memberUid] ?: false
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        FirebaseRepository.setCanToggleSharing(
                                            groupId, memberUid, !memberCanToggle)
                                        val updated = GroupEntry(group)
                                        updated.setCanToggleSharing(memberUid, !memberCanToggle)
                                        group = updated
                                        onEdited(updated)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(
                                    if (memberCanToggle) "Revoke Sharing" else "Allow Sharing",
                                    fontSize = 11.sp,
                                    color = if (memberCanToggle) destructiveColor else accentColor
                                )
                            }
                        }

                        // Vote to remove admin button (shown for Admin targets only, not SuperAdmin)
                        if (memberUid != currentUid && memberRole == GroupRole.ADMIN) {
                            val hasVoted = group.hasVotedToRemove(memberUid, currentUid)
                            val voteCount = group.voteCountToRemove(memberUid)
                            val needed = group.votesNeededToRemove(memberUid)
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        FirebaseRepository.voteToRemoveAdmin(
                                            groupId, memberUid, currentUid)
                                        val updated = GroupEntry(group)
                                        updated.voteToRemoveAdmin(memberUid, currentUid)
                                        group = updated
                                        onEdited(updated)
                                    }
                                },
                                modifier = Modifier.size(28.dp),
                                enabled = !hasVoted
                            ) {
                                Icon(
                                    Icons.Default.HowToVote,
                                    contentDescription = "Vote to remove ($voteCount/$needed)",
                                    tint = if (hasVoted) Color.Gray else destructiveColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Action Buttons Row ──────────────────────────────────────
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
                                        onEdited(updated)
                                    } catch (e: Exception) { /* locked */ }
                                }
                            }
                        }
                    )

                    // Trip status cycle: Inactive → En Route → Arrived → Inactive
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
                                onEdited(updated)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                                //onEdited(updated) TODO: keep?
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

                // ── Pending applications visible to admins ──────────────────
                if (isAdmin) {
                    val pending = group.adminApplications.filter { it != currentUid }
                    if (pending.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Pending Admin Applications", fontSize = 12.sp,
                            color = secondaryText, fontWeight = FontWeight.SemiBold)

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

// ── Shared small components ──────────────────────────────────────────────────

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