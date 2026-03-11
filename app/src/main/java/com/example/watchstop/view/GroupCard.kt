package com.example.watchstop.view

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val updatedEntry = CurrentGroupObject.getCurrentAssignmentEntry()
        onEdited(updatedEntry.copy())
    }

    val backgroundColor = if (darkmode) Color(0xFF1C1C1E) else Color.White
    val primaryText = if (darkmode) Color.White else Color.Black
    val secondaryText = if (darkmode) Color(0xFF8E8E93) else Color(0xFF636366)
    val accentColor = Color(0xFF007AFF)
    val destructiveColor = Color(0xFFFF3B30)

    WatchStopTheme (darkTheme = darkmode) {
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = groupEntryParameter.title,
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = primaryText
                        )

                        Text(
                            text = "Due ${
                                groupEntryParameter.eventDateTime.toLocalDate()
                                    .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                            }",
                            fontSize = 14.sp,
                            color = secondaryText,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "Edit",
                        color = accentColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                CurrentGroupObject.loadCurrentAssignmentEntry(
                                    groupEntryParameter
                                )
                                val editAssignmentIntent =
                                    Intent(context, EditGroupActivity::class.java)
                                launcher.launch(editAssignmentIntent)
                            }
                            .padding(start = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = groupEntryParameter.description,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    color = primaryText,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = (if (darkmode) Color.White else Color.Black).copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = groupEntryParameter.eventDateTime.toLocalTime()
                                .format(DateTimeFormatter.ofPattern("h:mm a")),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "Close Group",
                        color = destructiveColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onDeleted() }
                    )
                }
            }
        }
    }
}