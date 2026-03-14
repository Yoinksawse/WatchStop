package com.example.watchstop.view.screens

import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.activities.LoginActivity
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.model.GroupEntry
import com.example.watchstop.model.GroupRole
import com.example.watchstop.view.GroupCard
import com.example.watchstop.view.ui.theme.WatchStopTheme
import java.time.LocalDateTime

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun GroupsScreen() {
    val groups = remember { mutableStateListOf<GroupEntry>() }
    var showCreationDialog by remember { mutableStateOf(false) }
    var showLoginPrompt by remember { mutableStateOf(false) }
    val context = LocalContext.current

    WatchStopTheme(darkTheme = darkmode) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    containerColor = if (darkmode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    onClick = {
                        if (UserProfileObject.isLoggedIn) {
                            showCreationDialog = true
                        } else {
                            showLoginPrompt = true
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create new group", modifier = Modifier.size(24.dp))
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "My Groups",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (darkmode) Color.White else Color.Black,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (groups.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(0.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (darkmode) Color(0xFF1C1C1E) else Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        ) {
                            Text(
                                text = "No Groups yet — tap + to create one",
                                modifier = Modifier
                                    .padding(vertical = 40.dp, horizontal = 16.dp)
                                    .align(Alignment.CenterHorizontally),
                                style = TextStyle(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF8E8E93),
                                    letterSpacing = (-0.4).sp
                                )
                            )
                        }
                    }
                } else {
                    LazyColumn {
                        itemsIndexed(groups) { i, group ->
                            GroupCard(
                                groupEntryParameter = group,
                                onEdited = { updated -> groups[i] = updated },
                                onDeleted = { groups.removeAt(i) }
                            )
                        }
                    }
                }
            }
        }

        if (showLoginPrompt) {
            AlertDialog(
                onDismissRequest = { showLoginPrompt = false },
                title = { Text("Login Required") },
                text = { Text("You must be logged in to create a group.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLoginPrompt = false
                            val intent = Intent(context, LoginActivity::class.java)
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Login")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLoginPrompt = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // ── Group Creation Dialog ─────────────────────────────────────────
        if (showCreationDialog) {
            GroupCreationDialog(
                onDismiss = { showCreationDialog = false },
                onCreate = { newGroup ->
                    groups.add(newGroup)
                    showCreationDialog = false
                }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun GroupCreationDialog(
    onDismiss: () -> Unit,
    onCreate: (GroupEntry) -> Unit
) {
    var groupTitle by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(GroupRole.SUPER_ADMIN) }
    val currentUser = UserProfileObject.userName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = groupTitle,
                    onValueChange = { groupTitle = it },
                    label = { Text("Group Name") },
                    singleLine = true
                )

                Text("Your role as creator:", style = MaterialTheme.typography.bodyMedium)

                // Role selector
                listOf(
                    GroupRole.SUPER_ADMIN to "Super Admin — cannot be removed, full control",
                    GroupRole.ADMIN to "Admin — can be voted out by other members"
                ).forEach { (role, description) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role }
                        )
                        Column {
                            Text(role.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (groupTitle.isNotBlank()) {
                        val entry = GroupEntry(
                            title = groupTitle.trim(),
                            eventDateTime = LocalDateTime.now(),
                            description = "",
                            groupMemberNames = mutableListOf(currentUser),
                            memberRoles = mutableMapOf(currentUser to selectedRole),
                            locationSharingEnabled = mutableMapOf(currentUser to false),
                            canToggleSharing = mutableMapOf(currentUser to true)
                        )
                        onCreate(entry)
                    }
                },
                enabled = groupTitle.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
