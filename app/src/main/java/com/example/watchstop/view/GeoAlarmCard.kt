package com.example.watchstop.view

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.activities.X
import com.example.watchstop.data.FirebaseRepository
import com.example.watchstop.data.UserGeofencesDatabase
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.model.GeoAlarm
import com.example.watchstop.view.ui.theme.WatchStopTheme
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun GeoAlarmCard(
    alarm: GeoAlarm,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: (GeoAlarm) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }
    var isOverflowing by remember { mutableStateOf(false) }
    var isTogglingActive by remember { mutableStateOf(false) }
    
    val backgroundColor = if (darkmode) Color(0xFF1C1C1E) else Color.White
    val primaryText = if (darkmode) Color.White else Color.Black
    val secondaryText = if (darkmode) Color(0xFF8E8E93) else Color(0xFF636366)
    val accentColor = Color(0xFF007AFF)
    val destructiveColor = Color(0xFFFF3B30)

    val geofenceName = UserGeofencesDatabase.getAllGeofences()
        .find { it.id == alarm.geofenceId }?.name ?: "No Geofence"

    WatchStopTheme(darkTheme = darkmode) {
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = alarm.name,
                            fontSize = 18.sp * X.value,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )

                        //STATUS BADGE (active/inactive)

                        val activeGreen = Color(0xFF4CAF50)
                        val activeBg = activeGreen.copy(alpha = 0.15f) // Subtle tint
                        val inactiveBg = Color.Gray.copy(alpha = 0.2f)

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (alarm.active) activeBg else inactiveBg)
                                .border(
                                    width = 1.dp,
                                    color = if (alarm.active) activeGreen.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = !isTogglingActive) {
                                    scope.launch {
                                        isTogglingActive = true
                                        try {
                                            val updatedAlarm = alarm.copy(active = !alarm.active)

                                            if (UserProfileObject.isLoggedIn) {
                                                val uid = FirebaseRepository.currentUid
                                                if (uid != null) FirebaseRepository.saveGeoAlarm(uid, updatedAlarm)
                                            }

                                            onToggleActive(updatedAlarm)

                                            Toast.makeText(context, "Alarm ${if (updatedAlarm.active) "activated" else "deactivated"}", Toast.LENGTH_SHORT).show()
                                        }
                                        catch (e: Exception) { Toast.makeText(context, "Failed to update alarm: ${e.message}", Toast.LENGTH_SHORT).show()}
                                        finally { isTogglingActive = false }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isTogglingActive) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = if (alarm.active) activeGreen else Color.Gray
                                )
                            } else {
                                Text(
                                    text = if (alarm.active) "ACTIVE" else "INACTIVE",
                                    color = if (alarm.active) activeGreen else Color.Gray,
                                    style = TextStyle(
                                        fontSize = 10.sp * X.value,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    //GEOFENCE NAME
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = geofenceName,
                            fontSize = 12.sp * X.value,
                            color = secondaryText,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // DATE/DAY + TIME WINDOW (show if not indefinite)
                    val dateStr = when {
                        alarm.specificDate != null -> alarm.specificDate!!.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                        alarm.dayOfWeek != null -> "Every " + alarm.dayOfWeek!!.name.lowercase().replaceFirstChar { it.uppercase() }
                        else -> null
                    }

                    val timeStr = if (alarm.startTime != null && alarm.endTime != null) {
                        "${alarm.startTime!!.format(DateTimeFormatter.ofPattern("h:mm a"))} - ${alarm.endTime!!.format(DateTimeFormatter.ofPattern("h:mm a"))}"
                    } else null

                    if (dateStr != null || timeStr != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .border(1.dp, secondaryText.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val displayText = when {
                                dateStr != null && timeStr != null -> "$timeStr | $dateStr"
                                dateStr != null -> "Whole Day | $dateStr"
                                timeStr != null -> "$timeStr | Daily"
                                else -> "-"
                            }
                            Text(
                                text = displayText,
                                fontSize = 13.sp * X.value,
                                fontWeight = FontWeight.Medium,
                                color = primaryText
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Monitored at all times",
                            fontSize = 13.sp * X.value,
                            color = secondaryText,
                            fontStyle = FontStyle.Italic
                        )
                    }

                    if (alarm.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = secondaryText.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = secondaryText.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = isOverflowing || expanded) {
                                    expanded = !expanded
                                }
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = alarm.description,
                                    fontSize = 14.sp * X.value,
                                    color = primaryText.copy(alpha = 0.9f),
                                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                                    overflow = TextOverflow.Ellipsis,
                                    onTextLayout = { textLayoutResult ->
                                        if (!expanded) {
                                            isOverflowing = textLayoutResult.hasVisualOverflow
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                if (isOverflowing || expanded) {
                                    Text(
                                        text = if (expanded) "Tap to retract" else "Tap to expand",
                                        fontSize = 11.sp * X.value,
                                        color = secondaryText,
                                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // edit and delete buttons
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.1f),
                        modifier = Modifier.clickable { onEdit() }
                    ) {
                        Text(
                            text = "Edit",
                            color = accentColor,
                            fontSize = 12.sp * X.value,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = destructiveColor.copy(alpha = 0.1f),
                        modifier = Modifier.clickable { onDelete() }
                    ) {
                        Text(
                            text = "Delete",
                            color = destructiveColor,
                            fontSize = 12.sp * X.value,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
