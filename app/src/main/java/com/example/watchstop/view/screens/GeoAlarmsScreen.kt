package com.example.watchstop.view.screens

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.activities.MapActivity
import com.example.watchstop.data.GeoAlarmsDatabase
import com.example.watchstop.data.UserGeofencesDatabase
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.model.GeoAlarm
import com.example.watchstop.view.GeoAlarmCard
import com.example.watchstop.view.ui.theme.ElectricYellow
import com.example.watchstop.view.ui.theme.SlateGrey
import com.example.watchstop.view.ui.theme.WatchStopTheme
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter

val DATESETTER_STRING_DEFAULT = "Date: Daily";
val DAYSETTER_STRING_DEFAULT = "Repeat Weekly: On"

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoAlarmsScreen(
    onRequestMap: () -> Unit,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<GeoAlarm?>(null) }

    WatchStopTheme(darkTheme = darkmode) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        alarmToEdit = GeoAlarm(name = "")
                        showEditDialog = true
                    },
                    containerColor = if (darkmode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add GeoAlarm")
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "My GeoAlarms",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (darkmode) Color.White else Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (GeoAlarmsDatabase.alarms.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No GeoAlarms set", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(GeoAlarmsDatabase.alarms) { alarm ->
                            GeoAlarmCard(
                                alarm = alarm,
                                onEdit = {
                                    alarmToEdit = alarm.copy()
                                    showEditDialog = true
                                },
                                onDelete = {
                                    GeoAlarmsDatabase.removeAlarm(alarm)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showEditDialog && alarmToEdit != null) {
            EditGeoAlarmDialog(
                alarm = alarmToEdit!!,
                onDismiss = { showEditDialog = false },
                onSave = { updatedAlarm ->
                    if (GeoAlarmsDatabase.alarms.any { it.id == updatedAlarm.id }) {
                        GeoAlarmsDatabase.updateAlarm(updatedAlarm)
                    } else {
                        GeoAlarmsDatabase.addAlarm(updatedAlarm)
                    }
                    showEditDialog = false
                },
                onRequestMap = onRequestMap
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGeoAlarmDialog(
    alarm: GeoAlarm,
    onDismiss: () -> Unit,
    onSave: (GeoAlarm) -> Unit,
    onRequestMap: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(alarm.name) }
    var active by remember { mutableStateOf(alarm.active) }
    var description by remember { mutableStateOf(alarm.description) }
    var selectedGeofenceId by remember { mutableStateOf(alarm.geofenceId) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showNoGeofenceDialog by remember { mutableStateOf(false) }
    
    var selectedDate by remember { mutableStateOf(alarm.specificDate) }
    var selectedDay by remember { mutableStateOf(alarm.dayOfWeek) }
    var startTime by remember { mutableStateOf(alarm.startTime) }
    var endTime by remember { mutableStateOf(alarm.endTime) }

    //FIELD COLOURS
    val outlineColor = if (darkmode) ElectricYellow else SlateGrey
    val yellowOutlineColorsTextField = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = outlineColor,
        unfocusedBorderColor = outlineColor.copy(alpha = 0.6f),
        focusedLabelColor = outlineColor,
        unfocusedLabelColor = Color.Gray,
        cursorColor = outlineColor
    )

    val buttonBorder = BorderStroke(1.dp, outlineColor.copy(alpha = 0.6f))
    val buttonContentColor = if (darkmode) Color.White else Color.Black

    //if no geofence selected/created
    if (showNoGeofenceDialog) {
        var noGeofences: Boolean = UserGeofencesDatabase.getAllGeofences().isEmpty()
        var actionString =
            if (noGeofences) "You haven't created any Geofences yet. Create one now?"
            else "You haven't selected a Geofence."
        var titleString =
            if (noGeofences) "No Geofences Found"
            else "Select a Geofence"
        AlertDialog(
            onDismissRequest = { showNoGeofenceDialog = false },
            title = { Text(titleString) },
            text = { Text(actionString) },
            confirmButton = {
                if (noGeofences) {
                    TextButton(onClick = {
                        showNoGeofenceDialog = false
                        val intent = Intent(context, MapActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Text("Add Geofence")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoGeofenceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text =
                if (alarm.name.isEmpty()) "Add GeoAlarm"
                else "Edit GeoAlarm"
            )},
        text = {
            Box {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            focusManager.clearFocus()
                        }
                ) {
                    val outlinedTextFieldValue =
                        if (alarm.name.isEmpty()) "New GeoAlarm" else alarm.name
                    OutlinedTextField(
                        value = outlinedTextFieldValue,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        colors = yellowOutlineColorsTextField,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Activate Alarm")
                        Switch(checked = active, onCheckedChange = { active = it })
                    }

                    // Date or Day Selection
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f),
                            border = buttonBorder, //use text field border thickness
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonContentColor)
                        ) {
                            var label = when {
                                selectedDate != null -> "Date: " + selectedDate!!.format(
                                    DateTimeFormatter.ofPattern("MMM d, yyyy")
                                )

                                selectedDay != null -> "Day: " + selectedDay!!.name.lowercase()
                                    .replaceFirstChar { it.uppercase() }

                                else -> DATESETTER_STRING_DEFAULT
                            }
                            if (selectedDate != null && selectedDate!!.isBefore(LocalDate.now())) {
                                selectedDate == null
                                label = DATESETTER_STRING_DEFAULT
                                Toast.makeText(
                                    context,
                                    "Selected date is in the past",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            Text(label)
                        }
                        if (selectedDate != null || selectedDay != null) {
                            IconButton(onClick = { selectedDate = null; selectedDay = null }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        }
                    }

                    // Day of Week Dropdown (if no specific date)
                    if (selectedDate == null && selectedDay == null) {
                        var dayExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { dayExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                border = buttonBorder, //use text field border thickness
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonContentColor)
                            ) {
                                Text(DAYSETTER_STRING_DEFAULT)
                            }
                            DropdownMenu(
                                expanded = dayExpanded,
                                onDismissRequest = { dayExpanded = false }) {
                                DayOfWeek.entries.forEach { day ->
                                    DropdownMenuItem(
                                        text = { Text(day.name) },
                                        onClick = { selectedDay = day; dayExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    // Time Window
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showStartPicker = true },
                            modifier = Modifier.weight(1f),
                            border = buttonBorder,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonContentColor),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = startTime?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "Start Time",
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }

                        Text(
                            "-",
                            color = if (darkmode) Color.White else Color.Black,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                        )

                        OutlinedButton(
                            onClick = { showEndPicker = true },
                            modifier = Modifier.weight(1f),
                            border = buttonBorder,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonContentColor),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = endTime?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "End Time",
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        if (startTime == null) startTime = LocalTime.NOON;
                        if (endTime == null) endTime = LocalTime.MIDNIGHT;

                        //swap times if opposite and swapped..
                        if (endTime!!.isBefore(startTime!!)) {
                            val temp: LocalTime? = endTime
                            endTime = startTime
                            startTime = temp
                        }
                        if (endTime == startTime) {
                            startTime = LocalTime.NOON
                            endTime = LocalTime.MIDNIGHT
                            Toast.makeText(
                                context,
                                "End Time must be after Start Time",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                        IconButton(
                            onClick = { startTime = null; endTime = null },
                        ) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }

                    if (showDatePicker) {
                        val datePickerState = rememberDatePickerState()
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    datePickerState.selectedDateMillis?.let {
                                        selectedDate =
                                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                                                .toLocalDate()
                                        selectedDay = null
                                    }
                                    showDatePicker = false
                                }) { Text("OK") }
                            }
                        ) { DatePicker(state = datePickerState) }
                    }

                    if (showStartPicker) {
                        val state = rememberTimePickerState()
                        MultiPurposeDialog(
                            onDismissRequest = { showStartPicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    startTime =
                                        LocalTime.of(state.hour, state.minute); showStartPicker =
                                    false
                                }) { Text("OK") }
                            }) { TimePicker(state = state) }
                    }

                    if (showEndPicker) {
                        val state = rememberTimePickerState()
                        MultiPurposeDialog(
                            onDismissRequest = { showEndPicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    endTime =
                                        LocalTime.of(state.hour, state.minute); showEndPicker =
                                    false
                                }) { Text("OK") }
                            }) { TimePicker(state = state) }
                    }

                    OutlinedTextField(
                        description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = yellowOutlineColorsTextField
                    )

                    // Geofence
                    var gfExpanded by remember { mutableStateOf(false) }
                    val selectedGeofenceName =
                        UserGeofencesDatabase.getAllGeofences()
                            .find { it.id == selectedGeofenceId }?.name ?: "Select Geofence"

                    Box {
                        OutlinedButton(
                            onClick = {
                                if (UserGeofencesDatabase.getAllGeofences().isEmpty()) {
                                    showNoGeofenceDialog = true
                                } else {
                                    gfExpanded = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = buttonBorder, //use text field border thickness
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonContentColor)
                        ) {
                            Text("Geofence: $selectedGeofenceName");
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(
                            expanded = gfExpanded,
                            onDismissRequest = { gfExpanded = false }
                        ) {
                            UserGeofencesDatabase.getAllGeofences().forEach { gf ->
                                DropdownMenuItem(
                                    text = { Text(gf.name) },
                                    onClick = { selectedGeofenceId = gf.id; gfExpanded = false }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text(" +  Create New") },
                                onClick = {
                                    val intent = Intent(context, MapActivity::class.java)
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                //some error handling stuffs
                if (selectedGeofenceId == null) {
                    showNoGeofenceDialog = true
                }
                else {
                    if (alarm.name.isEmpty()) {
                        name = "New GeoAlarm"
                        alarm.name = name
                    }

                    onSave(
                        alarm.copy(
                            name = name,
                            active = active,
                            description = description,
                            geofenceId = selectedGeofenceId,
                            specificDate = selectedDate,
                            dayOfWeek = selectedDay,
                            startTime = startTime,
                            endTime = endTime
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MultiPurposeDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, text = { content() })
}
