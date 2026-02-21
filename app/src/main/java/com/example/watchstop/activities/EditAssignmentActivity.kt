package com.example.watchstop.activities

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.watchstop.model.UserProfileObject
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.model.AssignmentEntry
import com.example.watchstop.model.CurrentAssignmentObject
import com.example.watchstop.view.ui.theme.WatchStopTheme
import java.time.LocalDateTime

class EditAssignmentActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WatchStopTheme (darkTheme = UserProfileObject.darkmode) {
                val current = remember { CurrentAssignmentObject.getCurrentAssignmentEntry() }

                var title by remember { mutableStateOf(current.title) }
                var description by remember { mutableStateOf(current.description) }
                var day by remember { mutableStateOf(current.dueDate.dayOfMonth.toString()) }
                var month by remember { mutableStateOf(current.dueDate.monthValue.toString()) }
                var year by remember { mutableStateOf(current.dueDate.year.toString()) }

                val result = remember {
                    AssignmentEntry(
                        current.title,
                        current.dueDate,
                        current.description
                    )
                }

                val timePickerState = rememberTimePickerState(
                    initialHour = current.dueDate.hour,
                    initialMinute = current.dueDate.minute
                )

                var showTimePicker by remember { mutableStateOf(false) }

                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Edit Assignment") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
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
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        )

                        Text(
                            text = "Due Date",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                            OutlinedTextField(
                                value = day,
                                onValueChange = { if (it.length <= 2) day = it },
                                label = { Text("Day") },
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = month,
                                onValueChange = { if (it.length <= 2) month = it },
                                label = { Text("Month") },
                                modifier = Modifier.weight(1.2f)
                            )

                            OutlinedTextField(
                                value = year,
                                onValueChange = { if (it.length <= 4) year = it },
                                label = { Text("Year") },
                                modifier = Modifier.weight(1.5f)
                            )

                            IconButton(
                                onClick = { showTimePicker = true },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null)
                            }
                        }

                        if (showTimePicker) {
                            TimePickerDialog(
                                onDismissRequest = { showTimePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {

                                        result.dueDate = result.dueDate
                                            .withHour(timePickerState.hour)
                                            .withMinute(timePickerState.minute)

                                        showTimePicker = false
                                    }) { Text("OK") }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        showTimePicker = false
                                    }) { Text("Cancel") }
                                },
                                title = { Text("Edit Time") }
                            ) {
                                TimePicker(state = timePickerState)
                            }
                        }

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Assignment Description") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            minLines = 5
                        )

                        Button(
                            onClick = {
                                if (title.isBlank()) return@Button

                                val newDay = day.toIntOrNull() ?: result.dueDate.dayOfMonth
                                val newMonth = month.toIntOrNull() ?: result.dueDate.monthValue
                                val newYear = year.toIntOrNull() ?: result.dueDate.year

                                val sanitisedDate = try {
                                    result.dueDate
                                        .withYear(newYear)
                                        .withMonth(newMonth)
                                        .withDayOfMonth(newDay)
                                } catch (e: Exception) {
                                    result.dueDate
                                }

                                result.title = title.trim()
                                result.description = description
                                result.dueDate = sanitisedDate
                                CurrentAssignmentObject.loadCurrentAssignmentEntry(result)

                                finish()
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 10.dp),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Text("Save Changes")
                        }
                    }
                }
            }
        }
    }
}
