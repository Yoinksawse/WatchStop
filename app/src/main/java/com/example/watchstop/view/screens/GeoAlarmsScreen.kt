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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
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
import com.example.watchstop.activities.X
import com.example.watchstop.data.GeoAlarmsDatabase
import com.example.watchstop.data.UserGeofencesDatabase
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.model.GeoAlarm
import com.example.watchstop.data.FirebaseRepository
import com.example.watchstop.data.GeoAlarmsDatabase.alarms
import com.example.watchstop.data.GeoAlarmsDatabase.saveAlarmsToCache
import com.example.watchstop.view.GeoAlarmCard
import com.example.watchstop.view.ui.theme.ElectricYellow
import com.example.watchstop.view.ui.theme.SlateGrey
import com.example.watchstop.view.ui.theme.WatchStopTheme
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import com.example.watchstop.view.DatePickerDialog
import com.example.watchstop.view.TimePickerDialog

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoAlarmsScreen(
    onRequestMap: () -> Unit,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<GeoAlarm?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Initialize data
    LaunchedEffect(UserProfileObject.isLoggedIn) {
        if (UserProfileObject.isLoggedIn) {
            GeoAlarmsDatabase.fetchAlarmsFromFirebaseDB()
        } else {
            GeoAlarmsDatabase.loadAlarmsFromCache(context)
        }
    }

    // Also listen for real-time updates if logged in
    if (UserProfileObject.isLoggedIn) {
        val uid = UserProfileObject.uid
        if (uid != null) {
            LaunchedEffect(uid) {
                FirebaseRepository.observeGeoAlarms(uid).collect { firebaseAlarms ->
                    alarms.clear()
                    alarms.addAll(firebaseAlarms)
                    // Update cache even when using Firebase
                    saveAlarmsToCache(context)
                }
            }
        }
    }

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
                    fontSize = 24.sp * X.value,
                    fontWeight = FontWeight.Bold,
                    color = if (darkmode) Color.White else Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (alarms.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No GeoAlarms set", color = Color.Gray, fontSize = 14.sp * X.value)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(alarms) { alarm ->
                            GeoAlarmCard(
                                alarm = alarm,
                                onEdit = {
                                    alarmToEdit = alarm.copy()
                                    showEditDialog = true
                                },
                                onDelete = {
                                    GeoAlarmsDatabase.removeAlarm(alarm, context)
                                    if (UserProfileObject.isLoggedIn) {
                                        UserProfileObject.uid?.let { uid ->
                                            scope.launch {
                                                FirebaseRepository.deleteGeoAlarm(uid, alarm.id)
                                            }
                                        }
                                    }
                                },
                                onToggleActive = { updatedAlarm ->
                                    val index = alarms.indexOfFirst { it.id == updatedAlarm.id }
                                    if (index != -1) alarms[index] = updatedAlarm
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
                        GeoAlarmsDatabase.updateAlarm(updatedAlarm, context)
                    } else {
                        GeoAlarmsDatabase.addAlarm(updatedAlarm, context)
                    }

                    if (UserProfileObject.isLoggedIn) {
                        UserProfileObject.uid?.let { uid ->
                            scope.launch {
                                FirebaseRepository.saveGeoAlarm(uid, updatedAlarm)
                            }
                        }
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
    var showInvalidDateTimeDialog by remember { mutableStateOf(false) }

    var selectedDate by remember { mutableStateOf(alarm.specificDate) }
    var selectedDay by remember { mutableStateOf(alarm.dayOfWeek) }
    var startTime by remember { mutableStateOf(alarm.startTime) }
    var endTime by remember { mutableStateOf(alarm.endTime) }

    //FIELD COLOURS
    val outlineColor = if (darkmode) ElectricYellow else SlateGrey
    val yellowOutlineColorsTextField = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = outlineColor,
        unfocusedBorderColor = outlineColor.copy(alpha = 0.4f),
        focusedLabelColor = outlineColor,
        unfocusedLabelColor = Color.Gray,
        cursorColor = outlineColor
    )

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
            title = { Text(titleString, fontSize = 14.sp * X.value) },
            text = { Text(actionString, fontSize = 14.sp * X.value) },
            confirmButton = {
                if (noGeofences) {
                    TextButton(onClick = {
                        showNoGeofenceDialog = false
                        val intent = Intent(context, MapActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Text("Add Geofence", fontSize = 14.sp * X.value)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoGeofenceDialog = false }) {
                    Text("Cancel", fontSize = 14.sp * X.value)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (alarm.name.isEmpty()) "Add GeoAlarm" else "Edit GeoAlarm",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp * X.value
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        focusManager.clearFocus()
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                // Name and Active Switch in one row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Name field takes remaining space
                    OutlinedTextField(
                        value = name.ifEmpty { "" },
                        onValueChange = { name = it },
                        label = { Text("Name", fontSize = 14.sp * X.floatValue) },
                        placeholder = { Text("Enter alarm name", fontSize = 14.sp * X.floatValue) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = yellowOutlineColorsTextField,
                    )

                    // Active switch with label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Active",
                            fontSize = 14.sp * X.value,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = active,
                            onCheckedChange = { active = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = outlineColor,
                                checkedTrackColor = outlineColor.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                // Date/Time Section Header
                Text(
                    text = "Schedule",
                    fontSize = 14.sp * X.value,
                    fontWeight = FontWeight.Medium,
                    color = outlineColor,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Date Selection Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Date/Day Selection Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Date/Time display card
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showDatePicker = true }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Event,
                                            contentDescription = null,
                                            tint = outlineColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = when {
                                                selectedDate != null -> selectedDate!!.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                                                selectedDay != null -> "Every " + selectedDay!!.name.lowercase().replaceFirstChar { it.uppercase() }
                                                else -> "Date"
                                            },
                                            color = if (selectedDate != null || selectedDay != null)
                                                MaterialTheme.colorScheme.onSurface
                                            else
                                                Color.Gray,
                                            fontSize = 14.sp * X.value
                                        )
                                    }
                                    if (selectedDate != null || selectedDay != null) {
                                        IconButton(
                                            onClick = { selectedDate = null; selectedDay = null },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Clear,
                                                null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Day of Week Dropdown (only if no date)
                            if (selectedDate == null) {
                                var dayExpanded by remember { mutableStateOf(false) }
                                Box {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable { dayExpanded = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                tint = outlineColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = selectedDay?.name?.take(3) ?: "Day",
                                                color = if (selectedDay != null)
                                                    MaterialTheme.colorScheme.onSurface
                                                else
                                                    Color.Gray,
                                                fontSize = 13.sp * X.value
                                            )
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = dayExpanded,
                                        onDismissRequest = { dayExpanded = false }
                                    ) {
                                        DayOfWeek.entries.forEach { day ->
                                            DropdownMenuItem(
                                                text = { Text(
                                                    day.name.lowercase().replaceFirstChar { it.uppercase() },
                                                    fontSize = 14.sp * X.value
                                                ) },
                                                onClick = { selectedDay = day; dayExpanded = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Time Window
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Start Time
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showStartPicker = true }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = outlineColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = startTime?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "Start",
                                            color = if (startTime != null)
                                                MaterialTheme.colorScheme.onSurface
                                            else
                                                Color.Gray,
                                            fontSize = 13.sp * X.value
                                        )
                                    }
                                }
                            }

                            Text("to", color = Color.Gray, fontSize = 12.sp * X.value)

                            // End Time
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showEndPicker = true }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = outlineColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = endTime?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "End",
                                            color = if (endTime != null)
                                                MaterialTheme.colorScheme.onSurface
                                            else
                                                Color.Gray,
                                            fontSize = 13.sp * X.value
                                        )
                                    }
                                }
                            }

                            // Clear times button
                            if (startTime != null || endTime != null) {
                                IconButton(
                                    onClick = { startTime = null; endTime = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)", fontSize = 14.sp * X.value) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    colors = yellowOutlineColorsTextField
                )

                // Geofence Section Header
                Text(
                    text = "Geofence",
                    fontSize = 14.sp * X.value,
                    fontWeight = FontWeight.Medium,
                    color = outlineColor,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Geofence Selection
                var gfExpanded by remember { mutableStateOf(false) }
                val selectedGeofence = UserGeofencesDatabase.getAllGeofences()
                    .find { it.id == selectedGeofenceId }
                val selectedGeofenceName = selectedGeofence?.name ?: "Select a geofence"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Selected geofence display
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (UserGeofencesDatabase.getAllGeofences().isEmpty()) {
                                            showNoGeofenceDialog = true
                                        } else {
                                            gfExpanded = true
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = if (selectedGeofenceId != null) outlineColor else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = selectedGeofenceName,
                                        color = if (selectedGeofenceId != null)
                                            MaterialTheme.colorScheme.onSurface
                                        else
                                            Color.Gray,
                                        fontSize = 14.sp * X.value
                                    )
                                }
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    null,
                                    tint = outlineColor
                                )
                            }
                        }

                        // Dropdown Menu
                        DropdownMenu(
                            expanded = gfExpanded,
                            onDismissRequest = { gfExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            UserGeofencesDatabase.getAllGeofences().forEach { gf ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.LocationOn,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = outlineColor
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(gf.name, fontSize = 14.sp * X.value)
                                        }
                                    },
                                    onClick = {
                                        selectedGeofenceId = gf.id
                                        gfExpanded = false
                                    }
                                )
                            }

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = outlineColor
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Create New Geofence", color = outlineColor, fontSize = 14.sp * X.value)
                                    }
                                },
                                onClick = {
                                    gfExpanded = false
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
            Button(
                onClick = {
                    if (selectedGeofenceId == null) {
                        showNoGeofenceDialog = true
                    } else {
                        val finalName = name.ifBlank { "New GeoAlarm" }
                        if (startTime != null && endTime == null) {
                            endTime = LocalTime.of(23, 59)
                        }

                        if (startTime == null && endTime != null) {
                            startTime = LocalTime.of(0, 0)
                        }

                        if (selectedDate != null) {
                            val now = LocalTime.now()
                            if (selectedDate == LocalDate.now() &&
                                startTime != null && endTime != null &&
                                endTime!! < now
                            ) {
                                showInvalidDateTimeDialog = true
                                return@Button
                            }
                        }

                        onSave(
                            alarm.copy(
                                name = finalName,
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
                },
                enabled = selectedGeofenceId != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = outlineColor,
                    contentColor = if (darkmode) Color.Black else Color.White,
                    disabledContainerColor = outlineColor.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Alarm", fontSize = 14.sp * X.value)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel", fontSize = 14.sp * X.value)
            }
        }
    )

    if (showInvalidDateTimeDialog) {
        InvalidDateTimeDialog(
            onDismiss = { showInvalidDateTimeDialog = false },
            onConfirm = { showInvalidDateTimeDialog = false },
            outlineColor = outlineColor
        )
    }

    // Time Pickers
    if (showStartPicker) {
        TimePickerDialog(
            onDismiss = { showStartPicker = false },
            onConfirm = { hour, minute ->
                startTime = LocalTime.of(hour, minute)
                showStartPicker = false
            },
            initialHour = startTime?.hour ?: 12,
            initialMinute = startTime?.minute ?: 0,
            outlineColor = outlineColor
        )
    }

    if (showEndPicker) {
        TimePickerDialog(
            onDismiss = { showEndPicker = false },
            onConfirm = { hour, minute ->
                endTime = LocalTime.of(hour, minute)
                // Validate end time is after start time
                if (startTime != null && endTime != null) {
                    if (endTime!! <= startTime!!) {
                        endTime = startTime!!.plusHours(1)
                        Toast.makeText(context, "End time adjusted to be after start time", Toast.LENGTH_SHORT).show()
                    }
                }
                showEndPicker = false
            },
            initialHour = endTime?.hour ?: 0,
            initialMinute = endTime?.minute ?: 0,
            outlineColor = outlineColor
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                if (date.isBefore(LocalDate.now())) {
                    Toast.makeText(context, "Selected date is in the past", Toast.LENGTH_SHORT).show()
                } else {
                    selectedDate = date
                    selectedDay = null
                }
                showDatePicker = false
            },
            initialDate = selectedDate ?: LocalDate.now(),
            outlineColor = outlineColor
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun InvalidDateTimeDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    outlineColor: Color
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invalid Date + Time", fontSize = 14.sp * X.value) },
        text = { Text("The time you set has already passed today", fontSize = 14.sp * X.value) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm() },
                colors = ButtonDefaults.textButtonColors(contentColor = outlineColor)
            ) {
                Text("OK", fontSize = 14.sp * X.value)
            }
        },
    )
}
