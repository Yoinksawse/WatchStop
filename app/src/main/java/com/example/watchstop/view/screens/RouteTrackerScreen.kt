@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.watchstop.view.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.watchstop.view.ui.theme.MapStyles
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.data.UserProfileObject.darkmode
import com.example.watchstop.model.RouteSegment
import com.example.watchstop.model.TrackedPoint
import com.example.watchstop.view.ui.theme.ElectricYellow
import com.example.watchstop.view.ui.theme.NeonLime
import com.example.watchstop.view.ui.theme.Purple40
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import com.google.android.gms.maps.model.StyleSpan
import com.google.android.gms.maps.model.StrokeStyle

@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RouteTrackerScreen() {
    val context = LocalContext.current
    var isTracking by remember { mutableStateOf(false) }
    var recordingInterval by remember { mutableFloatStateOf(10f) }
    var showSettings by remember { mutableStateOf(false) }
    val pathPoints = remember { mutableStateListOf<TrackedPoint>() }

    var startTime by remember { mutableLongStateOf(0L) }
    var durationSeconds by remember { mutableLongStateOf(0L) }
    var totalDistanceMeters by remember { mutableDoubleStateOf(0.0) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val hasLocationPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val singaporeCenter = LatLng(1.3521, 103.8198)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(singaporeCenter, 12f)
    }

    // Centering on user on start
    LaunchedEffect(hasLocationPermission.value) {
        if (hasLocationPermission.value) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    cameraPositionState.position =
                        CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                }
            }
        }
    }

    // Timer for duration
    LaunchedEffect(isTracking) {
        if (isTracking) {
            if (startTime == 0L) startTime = System.currentTimeMillis()
            while (isTracking) {
                durationSeconds = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        }
    }

    // Location Recording Loop
    LaunchedEffect(isTracking, recordingInterval) {
        if (isTracking) {
            while (isTracking) {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        location?.let {
                            val newPoint = TrackedPoint(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                speed = it.speed,
                                timestamp = System.currentTimeMillis()
                            )

                            // Calculate distance
                            var tooShort: Boolean = false
                            if (pathPoints.isNotEmpty()) {
                                val last = pathPoints.last()
                                val results = FloatArray(1)
                                Location.distanceBetween(
                                    last.latitude, last.longitude,
                                    it.latitude, it.longitude,
                                    results
                                )

                                //if moved distance is too small/didnt really move
                                //then don't add the point
                                tooShort = results[0] < 1.0
                                if (!tooShort) totalDistanceMeters += results[0]
                            }

                            if (!tooShort) pathPoints.add(newPoint)
                        }
                    }
                delay(recordingInterval.toLong() * 1000)
            }
        }
    }

    // Pre-compute segment speeds so we don't do it inside two loops

    val segments = remember(pathPoints.size) {
        if (pathPoints.size < 2) emptyList()
        else (0 until pathPoints.size - 1).map { i ->
            val s = pathPoints[i]
            val e = pathPoints[i + 1]
            val dist = FloatArray(1).also {
                Location.distanceBetween(s.latitude, s.longitude, e.latitude, e.longitude, it)
            }
            val secs = (e.timestamp - s.timestamp) / 1000.0
            val speed = if (secs > 0) dist[0] / secs else 0.0
            RouteSegment(
                start = LatLng(s.latitude, s.longitude),
                end = LatLng(e.latitude, e.longitude),
                speed = speed
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission.value,
                mapStyleOptions = if (UserProfileObject.darkmode)
                    MapStyleOptions(MapStyles.DARK_MAP_STYLE) else null
            ),
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            if (segments.isNotEmpty()) {
                //white outline for line
                segments.forEach { seg ->
                    Polyline(
                        points = listOf(seg.start, seg.end),
                        color = Color.White,
                        width = 26f, //slightly wider than actual line for outline
                        jointType = JointType.ROUND,
                        startCap = RoundCap(),
                        endCap = RoundCap(),
                        zIndex = 0f
                    )
                }

                //coloured fill
                val prevSeg: RouteSegment = segments[0]
                segments.forEachIndexed { i, seg ->
                    val prev = segments.getOrNull(i - 1) ?: seg
                    val next = segments.getOrNull(i + 1) ?: seg

                    val red = (prev.red + seg.red + next.red) / 3
                    val green = (prev.green + seg.green + next.green) / 3
                    val blue = (prev.blue + seg.blue + next.blue) / 3

                    val color = Color(red, green, blue)

                    Polyline(
                        points = listOf(seg.start, seg.end),
                        color = color,
                        width = 16f,
                        jointType = JointType.ROUND,
                        startCap = RoundCap(),
                        endCap = RoundCap(),
                        zIndex = 1f
                    )
                }
            }
        }

        // ── Control panel ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, "Interval Settings", tint = Color.White)
            }
            IconButton(onClick = { isTracking = !isTracking }) {
                Icon(
                    if (isTracking) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Toggle Tracking",
                    tint = Color.White
                )
            }
            IconButton(onClick = {
                pathPoints.clear()
                totalDistanceMeters = 0.0
                durationSeconds = 0L
                startTime = 0L
            }) {
                Icon(Icons.Default.Delete, "Clear Path", tint = Color.White)
            }
        }

        // ── Status bar ──────────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            border = BorderStroke(1.dp, ElectricYellow.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatusItem("Tracked Duration", formatDuration(durationSeconds))
                StatusItem("Distance", "%.2f km".format(totalDistanceMeters / 1000.0))
            }
        }

        if (showSettings) {
            IntervalSettingsDialog(
                currentInterval = recordingInterval,
                onIntervalChange = { recordingInterval = it },
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
fun StatusItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun IntervalSettingsDialog(
    currentInterval: Float,
    onIntervalChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var tempInterval by remember { mutableFloatStateOf(currentInterval) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Movement Tracer Settings") },
        text = {
            Column {
                Text("Adjust time interval of recording: ${tempInterval.toInt()}s")
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
                    Text("1s", fontSize = 10.sp)
                    Text("60s", fontSize = 10.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onIntervalChange(tempInterval)
                onDismiss()
            }) { Text("Apply", color = if (darkmode) NeonLime else Purple40) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp
    )
}

fun formatDuration(seconds: Long): String {
    val hours = TimeUnit.SECONDS.toHours(seconds)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val secs = seconds % 60
    return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, secs)
    else "%02d:%02d".format(minutes, secs)
}