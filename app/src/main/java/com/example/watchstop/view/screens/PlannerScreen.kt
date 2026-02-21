package com.example.watchstop.view.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.FloatingActionButton
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.model.AssignmentEntry
import com.example.watchstop.model.UserProfileObject.darkmode
import com.example.watchstop.view.AssignmentCard
import com.example.watchstop.view.ui.theme.WatchStopTheme
import java.time.LocalDateTime

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PlannerScreen() {
    val assignments = remember { mutableStateListOf<AssignmentEntry>() }

    WatchStopTheme(darkTheme = darkmode) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    backgroundColor = if (darkmode) Color.DarkGray else Color.White,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    onClick = {
                        assignments.add(
                            AssignmentEntry(
                                "Lab X",
                                LocalDateTime.now(),
                                "DO YOUR LAB."
                            )
                        )
                    }
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Assignment",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        ) { innerPadding ->
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
                        text = "No Assignments",
                        modifier = Modifier
                            .padding(vertical = 40.dp, horizontal = 16.dp)
                            .align(Alignment.CenterHorizontally),
                        style = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (darkmode) Color(0xFF8E8E93) else Color(0xFF8E8E93), // iOS Secondary Label Color
                            letterSpacing = (-0.4).sp
                        )
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.padding(innerPadding)
            ) {
                items(assignments.size) { i ->
                    AssignmentCard(
                        assignmentEntryParameter = assignments[i],

                        onEdited = { updatedEntry ->
                            assignments[i] = updatedEntry
                        },

                        onDeleted = {
                            assignments.remove(assignments[i])
                        }
                    )
                }
            }
        }
    }
}