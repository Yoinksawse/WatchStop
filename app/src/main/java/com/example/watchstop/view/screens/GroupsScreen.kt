package com.example.watchstop.view.screens

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.watchstop.service.FirebaseRepository
import com.example.watchstop.view.GroupCard
import com.example.watchstop.view.ui.theme.WatchStopTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun GroupsScreen() {
    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val context = LocalContext.current

    // uid is reactive: if auth resolves after first composition, a new Flow is created
    val uid by remember { derivedStateOf { UserProfileObject.uid ?: "" } }
    val myGroups by remember(uid) {
        FirebaseRepository.observeMyGroups(uid)
    }.collectAsState(initial = emptyList())

    var showCreationDialog by remember { mutableStateOf(false) }
    var showLoginPrompt by remember { mutableStateOf(false) }

    WatchStopTheme(darkTheme = darkmode) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    containerColor = if (darkmode) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    onClick = {
                        if (UserProfileObject.isLoggedIn) showCreationDialog = true
                        else showLoginPrompt = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create group",
                        modifier = Modifier.size(24.dp))
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

                if (myGroups.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(0.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (darkmode) Color(0xFF1C1C1E) else Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().padding(32.dp)
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
                        items(myGroups) { (groupId, group) ->
                            GroupCard(
                                groupId = groupId,
                                groupEntryParameter = group,
                                onEdited = { updated ->
                                    appScope.launch {
                                        FirebaseRepository.saveGroup(updated, groupId)
                                    }
                                },
                                onDeleted = {
                                    appScope.launch {
                                        FirebaseRepository.deleteGroup(groupId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Login prompt ──────────────────────────────────────────────────
        if (showLoginPrompt) {
            AlertDialog(
                onDismissRequest = { showLoginPrompt = false },
                title = { Text("Login Required") },
                text = { Text("You must be logged in to create a group.") },
                confirmButton = {
                    TextButton(onClick = {
                        showLoginPrompt = false
                        context.startActivity(Intent(context, LoginActivity::class.java))
                    }) { Text("Login") }
                },
                dismissButton = {
                    TextButton(onClick = { showLoginPrompt = false }) { Text("Cancel") }
                }
            )
        }

        // ── Group creation dialog ─────────────────────────────────────────
        var pendingGroup by remember { mutableStateOf<GroupEntry?>(null) }

        if (showCreationDialog) {
            GroupCreationDialog(
                onDismiss = { showCreationDialog = false },
                onMake = { newGroup ->
                    Log.d("GroupsScreen", "onMake called — uid=${UserProfileObject.uid}, title=${newGroup.title}, members=${newGroup.groupMemberNames}")

                    showCreationDialog = false
                    appScope.launch {
                        try {
                            FirebaseRepository.saveGroup(newGroup)
                            Log.d("GroupsScreen", "saveGroup SUCCESS")
                        } catch (e: Exception) {
                            Log.e("GroupsScreen", "saveGroup FAILED: ${e.message}", e)
                        }
                    }
                }
            )
        }

        LaunchedEffect(pendingGroup) {
            val group = pendingGroup ?: return@LaunchedEffect
            try {
                FirebaseRepository.saveGroup(group)
                Log.d("GroupsScreen", "saveGroup SUCCESS")
            } catch (e: Exception) {
                Log.e("GroupsScreen", "saveGroup FAILED: ${e.message}", e)
            }
            pendingGroup = null
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun GroupCreationDialog(
    onDismiss: () -> Unit,
    onMake: (GroupEntry) -> Unit
) {
    var groupTitle by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(GroupRole.SUPER_ADMIN) }

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
                            Text(description, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (groupTitle.isNotBlank()) {
                        // Read uid at click time — guaranteed non-null here since isLoggedIn was true
                        val currentUid = UserProfileObject.uid ?: return@TextButton
                        val entry = GroupEntry(
                            title = groupTitle.trim(),
                            eventDateTime = LocalDateTime.now(),
                            description = ""
                        ).apply {
                            addMember(currentUid, selectedRole)
                        }
                        onMake(entry)
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