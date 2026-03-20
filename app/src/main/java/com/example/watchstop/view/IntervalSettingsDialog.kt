package com.example.watchstop.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchstop.activities.X
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.view.ui.theme.NeonLime
import com.example.watchstop.view.ui.theme.Purple40


@Composable
fun IntervalSettingsDialog(
    currentInterval: Float,
    onIntervalChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var tempInterval by remember { mutableFloatStateOf(currentInterval) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Route Tracker Settings",
            fontSize = MaterialTheme.typography.titleLarge.fontSize * X.value) },
        text = {
            Column {
                Text("Recording interval: ${tempInterval.toInt()}s",
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value)
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = tempInterval,
                    onValueChange = { tempInterval = it },
                    valueRange = 1f..60f,
                    colors = SliderDefaults.colors(
                        thumbColor = if (darkmode) NeonLime else Purple40,
                        activeTrackColor = if (darkmode) NeonLime else Purple40
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1s", fontSize = 10.sp * X.value)
                    Text("60s", fontSize = 10.sp * X.value)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntervalChange(tempInterval); onDismiss() }) {
                Text(text = "Apply", color = if (darkmode) NeonLime else Purple40,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel",
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value) }
        },
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp
    )
}