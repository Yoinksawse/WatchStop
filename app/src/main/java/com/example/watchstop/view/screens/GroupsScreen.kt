package com.example.watchstop.view.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.model.GroupEntry
import com.example.watchstop.model.GroupRole
import com.example.watchstop.view.GroupCard
import com.example.watchstop.view.UserRow
import com.example.watchstop.view.ui.theme.WatchStopTheme
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun GroupsScreen() {
    val assignments = remember { mutableStateListOf<GroupEntry>() }

    WatchStopTheme(darkTheme = darkmode) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    containerColor = if (darkmode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    onClick = {
                        val currentUser = UserProfileObject.userName
                        assignments.add(
                            GroupEntry(
                                title = "New Group",
                                eventDateTime = LocalDateTime.now(),
                                description = "Group for Event X / Family Y / Organisation Z",
                                groupMemberNames = mutableListOf(currentUser),
                                memberRoles = mutableMapOf(currentUser to GroupRole.SUPER_ADMIN),
                                canToggleSharing = mutableMapOf(currentUser to true)
                            )
                        )
                    }
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Create new group",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        ) { innerPadding ->
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "My Groups",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (darkmode) Color.White else Color.Black,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (assignments.isEmpty()) {
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
                        text = "No Groups",
                        modifier = Modifier
                            .padding(vertical = 40.dp, horizontal = 16.dp)
                            .align(Alignment.CenterHorizontally),
                        style = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (darkmode) Color(0xFF8E8E93) else Color(0xFF8E8E93),
                            letterSpacing = (-0.4).sp
                        )
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.padding(innerPadding)
            ) {
                itemsIndexed(assignments) { i, group ->
                    GroupCard(
                        groupEntryParameter = group,

                        onEdited = { updatedEntry ->
                            assignments[i] = updatedEntry
                        },

                        onDeleted = {
                            assignments.removeAt(i)
                        }
                    )
                }
            }
        }
    }
}