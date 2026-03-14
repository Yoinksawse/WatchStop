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
import com.example.watchstop.model.CurrentGroupObject
import com.example.watchstop.model.GroupEntry
import com.example.watchstop.model.GroupRole
import com.example.watchstop.view.ui.theme.WatchStopTheme
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun GroupCard(
    groupEntryParameter: GroupEntry,
    onEdited: (GroupEntry) -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val currentUser = UserProfileObject.userName
    val userRole = groupEntryParameter.memberRoles[currentUser] ?: GroupRole.MEMBER
    val isAdmin = userRole == GroupRole.ADMIN || userRole == GroupRole.SUPER_ADMIN
    val isSuperAdmin = userRole == GroupRole.SUPER_ADMIN

    // ── Stateful copy so local actions reflect immediately ──────────────────
    var group by remember(groupEntryParameter) { mutableStateOf(GroupEntry(groupEntryParameter)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val updated = CurrentGroupObject.getCurrentGroupEntry()
        group = GroupEntry(updated)
        onEdited(group)
    }

    val backgroundColor = if (darkmode) Color(0xFF1C1C1E) else Color.White
    val primaryText = if (darkmode) Color.White else Color.Black
    val secondaryText = if (darkmode) Color(0xFF8E8E93) else Color(0xFF636366)
    val accentColor = Color(0xFF007AFF)
    val destructiveColor = Color(0xFFFF3B30)
    val successColor = Color(0xFF34C759)

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
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
                    // SuperAdmins can only "Archive" not delete from card; Admins delete; Members quit
                    Text(
                        text = when {
                            isSuperAdmin -> "Archive"
                            isAdmin -> "Delete"
                            else -> "Quit"
                        },
                        color = destructiveColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onDeleted() }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Date / Time Tags ────────────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SketchTag(group.eventDateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy")))
                    SketchTag(group.eventDateTime.format(DateTimeFormatter.ofPattern("HH:mm 'hrs'")))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Members with Location Indicators ───────────────────────
                group.groupMemberNames.forEach { member ->
                    val memberRole = group.memberRoles[member] ?: GroupRole.MEMBER
                    val isSharing = group.locationSharingEnabled[member] ?: false
                    val canToggle = group.canToggleSharing[member] ?: false

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Location dot
                        Icon(
                            imageVector = if (isSharing) Icons.Default.LocationOn else Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = if (isSharing) successColor else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = member + if (member == currentUser) " (you)" else "",
                            fontSize = 13.sp,
                            color = primaryText,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = memberRole.displayName,
                            fontSize = 11.sp,
                            color = when (memberRole) {
                                GroupRole.SUPER_ADMIN -> Color(0xFFFFCC00)
                                GroupRole.ADMIN -> accentColor
                                GroupRole.MEMBER -> secondaryText
                            }
                        )

                        // Admin can toggle sharing permission for members
                        if (isAdmin && member != currentUser && memberRole == GroupRole.MEMBER) {
                            val memberCanToggle = group.canToggleSharing[member] ?: false
                            TextButton(
                                onClick = {
                                    val updated = GroupEntry(group)
                                    updated.setCanToggleSharing(member, !memberCanToggle)
                                    group = updated
                                    onEdited(updated)
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(
                                    if (memberCanToggle) "Lock" else "Allow",
                                    fontSize = 11.sp,
                                    color = if (memberCanToggle) destructiveColor else accentColor
                                )
                            }
                        }

                        // Vote to remove admin (any non-super-admin can vote against an admin)
                        if (member != currentUser && memberRole == GroupRole.ADMIN) {
                            val hasVoted = group.hasVotedToRemove(member, currentUser)
                            val voteCount = group.voteCountToRemove(member)
                            val needed = group.votesNeededToRemove(member)
                            IconButton(
                                onClick = {
                                    if (!hasVoted) {
                                        val updated = GroupEntry(group)
                                        updated.voteToRemoveAdmin(member, currentUser)
                                        group = updated
                                        onEdited(updated)
                                    }
                                },
                                modifier = Modifier.size(28.dp),
                                enabled = !hasVoted
                            ) {
                                Icon(
                                    Icons.Default.HowToVote,
                                    contentDescription = "Vote to remove admin ($voteCount/$needed)",
                                    tint = if (hasVoted) Color.Gray else destructiveColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Action Buttons ──────────────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val isSharing = group.locationSharingEnabled[currentUser] ?: false
                    val canCurrentToggle = group.canToggleSharing[currentUser] ?: false

                    SketchButton(
                        text = if (isSharing) "Stop Sharing" else "Share Location",
                        onClick = {
                            if (canCurrentToggle) {
                                val updated = GroupEntry(group)
                                updated.toggleSharing(currentUser)
                                group = updated
                                onEdited(updated)
                            }
                        }
                    )

                    if (isAdmin) {
                        SketchButton(
                            text = "Manage Group",
                            onClick = {
                                CurrentGroupObject.loadCurrentGroupEntry(group)
                                launcher.launch(Intent(context, EditGroupActivity::class.java))
                            }
                        )
                    } else if (!group.adminApplications.contains(currentUser)) {
                        SketchButton(
                            text = "Apply for Admin",
                            onClick = {
                                val updated = GroupEntry(group)
                                updated.applyForAdmin(currentUser)
                                group = updated
                                onEdited(updated)
                            }
                        )
                    }
                }

                // ── Application Pending Banner ──────────────────────────────
                if (group.adminApplications.contains(currentUser)) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val voteCount = group.adminApplicationVoteCount(currentUser)
                    val needed = group.votesNeededForApplication(currentUser)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Admin application pending",
                            fontSize = 12.sp,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$voteCount / $needed votes",
                            fontSize = 12.sp,
                            color = accentColor
                        )
                    }
                }

                // ── Pending Applications visible to Admins ──────────────────
                if (isAdmin) {
                    val pendingApplicants = group.adminApplications.filter { it != currentUser }
                    if (pendingApplicants.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Pending Admin Applications",
                            fontSize = 12.sp,
                            color = secondaryText,
                            fontWeight = FontWeight.SemiBold
                        )
                        pendingApplicants.forEach { applicant ->
                            val voteCount = group.adminApplicationVoteCount(applicant)
                            val needed = group.votesNeededForApplication(applicant)
                            val hasVoted = group.adminApplicationVotes[applicant]?.contains(currentUser) == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = applicant,
                                    fontSize = 13.sp,
                                    color = primaryText,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$voteCount/$needed",
                                    fontSize = 12.sp,
                                    color = secondaryText
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // SuperAdmin can approve instantly; regular admin adds a vote
                                TextButton(
                                    onClick = {
                                        if (!hasVoted) {
                                            val updated = GroupEntry(group)
                                            updated.voteForAdminApplication(applicant, currentUser)
                                            group = updated
                                            onEdited(updated)
                                        }
                                    },
                                    enabled = !hasVoted,
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = if (isSuperAdmin) "Approve" else if (hasVoted) "Voted" else "Approve",
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

// ── Shared small components ─────────────────────────────────────────────────

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