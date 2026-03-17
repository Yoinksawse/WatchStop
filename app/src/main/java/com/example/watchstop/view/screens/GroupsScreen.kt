package com.example.watchstop.view.screens

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.activities.LoginActivity
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.model.GroupEntry
import com.example.watchstop.model.GroupRole
import com.example.watchstop.data.FirebaseRepository
import com.example.watchstop.data.NotificationItem
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

    var showFabMenu by remember { mutableStateOf(false) }

    //refresh logic
    var refreshTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(3_000)//wait 3s
            refreshTrigger++

            if (refreshTrigger > 30) refreshTrigger = 0;
        }
    }

    val isLoggedIn = UserProfileObject.isLoggedIn
    val uid = if (isLoggedIn) UserProfileObject.uid ?: "" else ""

    val myGroups by remember(uid, refreshTrigger) {
        FirebaseRepository.observeMyGroups(uid)
    }.collectAsState(initial = emptyList())

    val notifications by remember(uid, refreshTrigger) {
        FirebaseRepository.observeAllNotifications(uid)
    }.collectAsState(initial = emptyList())

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Groups", "Notifications")

    var showCreationDialog by remember { mutableStateOf(false) }
    var showLoginPrompt by remember { mutableStateOf(false) }

    WatchStopTheme(darkTheme = darkmode) {
        Scaffold(
            topBar = {
                Column {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = if (darkmode) Color(0xFF121212) else Color.White,
                        contentColor = Color(0xFF007AFF),
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(title, fontWeight = FontWeight.Bold)
                                        if (index == 1 && notifications.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Red),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = notifications.size.toString(),
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                Box {
                    FloatingActionButton(
                        containerColor = if (darkmode) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        onClick = { showFabMenu = true }
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Operations Menu", modifier = Modifier.size(24.dp))
                    }

                    DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Refresh Groups") },
                            onClick = {
                                showFabMenu = false
                                refreshTrigger++

                                //TODO: refresh groups
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Create New Group") },
                            onClick = {
                                showFabMenu = false
                                if (UserProfileObject.isLoggedIn) showCreationDialog = true
                                else showLoginPrompt = true
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                if (selectedTabIndex == 0) {
                    GroupsTabContent(myGroups, appScope)
                } else {
                    NotificationsTabContent(notifications, appScope)
                }
            }
        }

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

        if (showCreationDialog) {
            GroupCreationDialog(
                onDismiss = { showCreationDialog = false },
                onMake = { newGroup ->
                    showCreationDialog = false
                    appScope.launch {
                        try { FirebaseRepository.saveGroup(newGroup) }
                        catch (e: Exception) { Log.e("GroupsScreen", "saveGroup FAILED: ${e.message}", e) }
                    }
                }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun GroupsTabContent(
    myGroups: List<Pair<String, GroupEntry>>,
    appScope: CoroutineScope
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "My Groups",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (darkmode) Color.White else Color.Black,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (myGroups.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No Groups yet — tap + to create one", color = Color.Gray)
                }
            }
        } else {
            items(myGroups) { (groupId, group) ->
                GroupCard(
                    groupId = groupId,
                    groupEntryParameter = group,
                    onEdited = { updated ->
                        appScope.launch {
                            try {
                                FirebaseRepository.updateGroupMetadata(groupId, updated)
                            } catch (e: Exception) {
                                Log.e("GroupsScreen", "updateGroupMetadata failed: ${e.message}")
                            }
                        }
                    },
                    onDeleted = {
                        appScope.launch { FirebaseRepository.deleteGroup(groupId) }
                    }
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun NotificationsTabContent(
    notifications: List<NotificationItem>,
    appScope: CoroutineScope
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Notifications",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (darkmode) Color.White else Color.Black,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (notifications.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No new notifications", color = Color.Gray)
                    }
                }
            }
        } else {
            items(notifications) { item ->
                NotificationRow(item, appScope)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun NotificationRow(item: NotificationItem, appScope: CoroutineScope) {
    val backgroundColor = if (darkmode) Color(0xFF1C1C1E) else Color.White
    val primaryText = if (darkmode) Color.White else Color.Black
    val secondaryText = if (darkmode) Color(0xFF8E8E93) else Color(0xFF636366)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (item) {
                    is NotificationItem.Invitation -> Icons.Default.GroupAdd
                    is NotificationItem.AdminApplication -> Icons.Default.AdminPanelSettings
                    is NotificationItem.RemovalVote -> Icons.Default.HowToVote
                }
                val iconColor = when (item) {
                    is NotificationItem.Invitation -> Color(0xFF007AFF)
                    is NotificationItem.AdminApplication -> Color(0xFF34C759)
                    is NotificationItem.RemovalVote -> Color(0xFFFF3B30)
                }
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (item) {
                        is NotificationItem.Invitation -> "Group Invitation"
                        is NotificationItem.AdminApplication -> "Admin Application"
                        is NotificationItem.RemovalVote -> "Administrator Removal Vote"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = iconColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (item) {
                    is NotificationItem.Invitation -> "You've been invited to join \"${item.groupTitle}\""
                    is NotificationItem.AdminApplication -> {
                        var name by remember { mutableStateOf(item.applicantUid) }
                        LaunchedEffect(item.applicantUid) { name = FirebaseRepository.getUsername(item.applicantUid) }
                        "$name applied for Admin in \"${item.groupTitle}\""
                    }
                    is NotificationItem.RemovalVote -> {
                        var name by remember { mutableStateOf(item.targetUid) }
                        LaunchedEffect(item.targetUid) { name = FirebaseRepository.getUsername(item.targetUid) }
                        "Vote to remove $name as Admin in \"${item.groupTitle}\""
                    }
                },
                color = primaryText,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (item) {
                    is NotificationItem.Invitation -> {
                        Button(
                            onClick = { appScope.launch { FirebaseRepository.acceptInvitation(item.groupId) } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                        ) { Text("Accept") }
                        OutlinedButton(
                            onClick = { appScope.launch { FirebaseRepository.declineInvitation(item.groupId) } },
                            modifier = Modifier.weight(1f)
                        ) { Text("Decline") }
                    }
                    is NotificationItem.AdminApplication -> {
                        Button(
                            onClick = {
                                appScope.launch {
                                    FirebaseRepository.voteForAdminApplication(
                                        item.groupId, item.applicantUid, UserProfileObject.uid ?: "")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                        ) { Text("Approve") }
                        OutlinedButton(
                            onClick = {
                                appScope.launch {
                                    FirebaseRepository.declineAdminApplication(
                                        item.groupId, item.applicantUid)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Decline", color = Color(0xFFFF3B30)) }
                    }

                    is NotificationItem.RemovalVote -> {
                        Button(
                            onClick = { appScope.launch { FirebaseRepository.voteToRemoveAdmin(item.groupId, item.targetUid, UserProfileObject.uid ?: "") } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
                        ) { Text("Vote to Remove") }
                    }
                }
            }
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
                    GroupRole.SUPER_ADMIN to "Super Admin — highest authority",
                    GroupRole.ADMIN to "Admin — can be voted out"
                ).forEach { (role, description) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selectedRole = role }
                    ) {
                        RadioButton(selected = selectedRole == role, onClick = { selectedRole = role })
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
                        val currentUid = UserProfileObject.uid ?: return@TextButton
                        val entry = GroupEntry(title = groupTitle.trim(), eventDateTime = LocalDateTime.now(), description = "")
                            .apply { addMember(currentUid, selectedRole) }
                        onMake(entry)
                    }
                },
                enabled = groupTitle.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
