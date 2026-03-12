package com.example.watchstop.activities

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.watchstop.data.UserProfileObject
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.model.GroupEntry
import com.example.watchstop.model.CurrentGroupObject
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
            WatchStopTheme (darkTheme = UserProfileObject.darkmode) {
                val current = remember { CurrentGroupObject.getCurrentGroupEntry() }

                var title by remember { mutableStateOf(current.title) }
                var description by remember { mutableStateOf(current.description) }
                var day by remember { mutableStateOf(current.eventDateTime.dayOfMonth.toString()) }
                var month by remember { mutableStateOf(current.eventDateTime.monthValue.toString()) }
                var year by remember { mutableStateOf(current.eventDateTime.year.toString()) }

                // Local copies of the new fields
                val memberNames = remember { mutableStateListOf(*current.groupMemberNames.toTypedArray()) }
                val memberRoles = remember { mutableStateMapOf<String, GroupRole>().apply { putAll(current.memberRoles) } }
                val sharingEnabled = remember { mutableStateMapOf<String, Boolean>().apply { putAll(current.locationSharingEnabled) } }
                val canToggle = remember { mutableStateMapOf<String, Boolean>().apply { putAll(current.canToggleSharing) } }

                val currentUser = UserProfileObject.userName
                val currentUserRole = memberRoles[currentUser] ?: GroupRole.MEMBER

                val timePickerState = rememberTimePickerState(
                    initialHour = current.eventDateTime.hour,
                    initialMinute = current.eventDateTime.minute
                )

                var showTimePicker by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Edit Group & Members") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = if (darkmode) CarbonGrey else Purple40,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White
                            )
                        )
                    }
                ){ innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Group Title") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        )

                        Text(text = "Target Date", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(value = day, onValueChange = { if (it.length <= 2) day = it }, label = { Text("D") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = month, onValueChange = { if (it.length <= 2) month = it }, label = { Text("M") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = year, onValueChange = { if (it.length <= 4) year = it }, label = { Text("Y") }, modifier = Modifier.weight(1.5f))
                            IconButton(onClick = { showTimePicker = true }, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Default.Schedule, contentDescription = null)
                            }
                        }

                        if (showTimePicker) {
                            TimePickerDialog(
                                onDismissRequest = { showTimePicker = false },
                                confirmButton = {
                                    TextButton(onClick = { showTimePicker = false }) { Text("OK") }
                                },
                                title = { Text("Edit Time") }
                            ) { TimePicker(state = timePickerState) }
                        }

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            minLines = 3
                        )

                        HorizontalDivider()
                        Text(text = "Members & Location Sharing", style = MaterialTheme.typography.titleLarge)

                        memberNames.forEach { member ->
                            val role = memberRoles[member] ?: GroupRole.MEMBER
                            val isSharing = sharingEnabled[member] ?: false
                            val allowedToToggle = canToggle[member] ?: false

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = member, fontWeight = FontWeight.Bold)
                                        Text(text = role.name, style = MaterialTheme.typography.bodySmall)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Location Toggle
                                        IconButton(
                                            onClick = {
                                                if (member == currentUser && allowedToToggle) {
                                                    sharingEnabled[member] = !isSharing
                                                }
                                            },
                                            enabled = (member == currentUser && allowedToToggle)
                                        ) {
                                            Icon(
                                                imageVector = if (isSharing) Icons.Default.LocationOn else Icons.Default.LocationOff,
                                                contentDescription = "Toggle Sharing",
                                                tint = if (isSharing) Color.Green else Color.Gray
                                            )
                                        }

                                        // Admin Privileges / Voting
                                        if (member != currentUser) {
                                            if (currentUserRole == GroupRole.SUPER_ADMIN || currentUserRole == GroupRole.ADMIN) {
                                                // Admin can approve applications or promote
                                                if (role == GroupRole.MEMBER) {
                                                    IconButton(onClick = { memberRoles[member] = GroupRole.ADMIN }) {
                                                        Icon(Icons.Default.PersonAdd, contentDescription = "Promote")
                                                    }
                                                }
                                            }
                                            
                                            // Voting to remove admin
                                            if (role == GroupRole.ADMIN) {
                                                IconButton(onClick = { 
                                                    // In a real app, this would add a vote to the map in GroupEntry
                                                }) {
                                                    Icon(Icons.Default.HowToVote, contentDescription = "Vote to remove", tint = Color.Red)
                                                }
                                            }
                                        } else if (role == GroupRole.MEMBER) {
                                            // Self-apply for admin
                                            TextButton(onClick = { /* Apply logic */ }) {
                                                Text("Apply for Admin")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (title.isBlank()) return@Button
                                
                                val newEventDateTime = try {
                                    current.eventDateTime.withYear(year.toIntOrNull() ?: current.eventDateTime.year)
                                        .withMonth(month.toIntOrNull() ?: current.eventDateTime.monthValue)
                                        .withDayOfMonth(day.toIntOrNull() ?: current.eventDateTime.dayOfMonth)
                                        .withHour(timePickerState.hour)
                                        .withMinute(timePickerState.minute)
                                } catch (e: Exception) { current.eventDateTime }

                                val updated = GroupEntry(
                                    title = title.trim(),
                                    eventDateTime = newEventDateTime,
                                    description = description,
                                    groupMemberNames = memberNames.toMutableList(),
                                    memberRoles = memberRoles.toMutableMap(),
                                    locationSharingEnabled = sharingEnabled.toMutableMap(),
                                    canToggleSharing = canToggle.toMutableMap()
                                )
                                CurrentGroupObject.loadCurrentGroupEntry(updated)
                                finish()
                            },
                            modifier = Modifier.align(Alignment.End).padding(top = 10.dp),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Text("Save Group Changes")
                        }
                    }
                }
            }
        }
    }
}
