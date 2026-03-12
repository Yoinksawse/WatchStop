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
import com.example.watchstop.model.GroupEntry
import com.example.watchstop.model.CurrentGroupObject
import com.example.watchstop.model.GroupRole
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.data.UserProfileObject.darkmode
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

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val updatedEntry = CurrentGroupObject.getCurrentGroupEntry()
        onEdited(updatedEntry.copy())
    }

    val backgroundColor = if (darkmode) Color(0xFF1C1C1E) else Color.White
    val primaryText = if (darkmode) Color.White else Color.Black
    val secondaryText = if (darkmode) Color(0xFF8E8E93) else Color(0xFF636366)
    val accentColor = Color(0xFF007AFF)
    val destructiveColor = Color(0xFFFF3B30)

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
                // Row: Title | Delete/Quit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = groupEntryParameter.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = primaryText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (isAdmin) "Delete" else "Quit",
                        color = destructiveColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onDeleted() }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row: Date | Time
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SketchTag(groupEntryParameter.eventDateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy")))
                    SketchTag(groupEntryParameter.eventDateTime.format(DateTimeFormatter.ofPattern("HH:mm 'hrs'")))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Members List
                Text(
                    text = "Members: ${groupEntryParameter.groupMemberNames.joinToString(", ")}",
                    fontSize = 13.sp,
                    color = secondaryText,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val isSharing = groupEntryParameter.locationSharingEnabled[currentUser] ?: false

                    SketchButton(
                        text = if (isSharing) "End monitoring" else "Monitor Group",
                        onClick = {
                            val updated = GroupEntry(groupEntryParameter)
                            updated.locationSharingEnabled[currentUser] = !isSharing
                            onEdited(updated)
                        }
                    )

                    SketchButton(
                        text = if (isAdmin) "Manage Group" else "Apply Admin",
                        onClick = {
                            if (isAdmin) {
                                CurrentGroupObject.loadCurrentGroupEntry(groupEntryParameter)
                                val intent = Intent(context, EditGroupActivity::class.java)
                                launcher.launch(intent)
                            } else {
                                if (!groupEntryParameter.adminApplications.contains(currentUser)) {
                                    val updated = GroupEntry(groupEntryParameter)
                                    updated.adminApplications.add(currentUser)
                                    onEdited(updated)
                                }
                            }
                        }
                    )
                }

                // Application Banner
                if (groupEntryParameter.adminApplications.contains(currentUser)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Applied for Admin",
                            fontSize = 12.sp,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        // Note: adminVotes mapping not found in current model
                    }
                }
            }
        }
    }
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