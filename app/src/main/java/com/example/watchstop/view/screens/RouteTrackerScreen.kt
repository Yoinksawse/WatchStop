@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.watchstop.view.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.watchstop.view.ui.theme.MapStyles
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.model.ColouredSegment
import com.example.watchstop.model.PathPoint
import com.example.watchstop.model.SegmentData
import com.example.watchstop.view.InfoDisplayColumn
import com.example.watchstop.view.IntervalSettingsDialog
import com.example.watchstop.view.ui.theme.ElectricYellow
import com.example.watchstop.view.ui.theme.NeonLime
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class SavedRoute(
    val id: String = "",
    val timestamp: Long = 0L,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Long = 0L,
    val points: List<PathPoint> = emptyList()
)

@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RouteTrackerScreen() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val database = remember { FirebaseDatabase.getInstance().getReference("savedRoutes") }

    //settings
    var isTracking by remember { mutableStateOf(false) }
    var recordingInterval by remember { mutableFloatStateOf(10f) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var savedRoutesList = remember { mutableStateListOf<SavedRoute>() }
    var isRouteSaved by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var isCheckingLocation by remember { mutableStateOf(false) }

    //route info
    val pathPoints = remember { mutableStateListOf<PathPoint>() }
    var startTime by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }   //tot travel duration in seconds
    var totalDistanceMeters by remember { mutableDoubleStateOf(0.0) }

    //(for route playback)
    var pinFraction by remember { mutableFloatStateOf(1f) }
    var pinExpanded by remember { mutableStateOf(false) }

    //location & permission handling
    val singaporeCenter = LatLng(1.3521, 103.8198)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(singaporeCenter, 12f)
    }

    //get device location (gps + wifi + cellular)
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val hasLocationPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    //check location availability + start tracking
    fun checkLocationAndStartTracking() {
        if (!hasLocationPermission.value) {
            showPermissionDialog = true
            return
        }

        isCheckingLocation = true
        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                isCheckingLocation = false
                if (loc != null) {
                    //location available => start tracking
                    isTracking = true
                    if (startTime == 0L) startTime = System.currentTimeMillis()
                } else {
                    //location is null
                    Toast.makeText(
                        context,
                        "Unable to get current location. Please check GPS/WiFi/Cellular is enabled.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener { exception ->
                isCheckingLocation = false
                //could ot get location
                Toast.makeText(
                    context,
                    "Location error: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    //BEGIN BEGIN BEGIN BEGIN BEGIN BEGIN BEGIN BEGIN BEGIN BEGIN BEGIN LIFECYCLE UPDATES
    //try to get user location
    //WHEN hasLocationPermission is changed
    LaunchedEffect(hasLocationPermission.value) {
        if (hasLocationPermission.value) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    cameraPositionState.position =
                        CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                }
            }
        }
    }

    //keep updating playback pin state
    //WHEN isTracking is changed
    LaunchedEffect(isTracking) {
        if (!isTracking) {
            pinFraction = 1f //keep location pin at end point
            pinExpanded = false //dont show playback card
        }
    }

    //duration update once every second
    //WHEN isTracking is changed
    LaunchedEffect(isTracking) {
        if (isTracking) {
            if (startTime == 0L) startTime = System.currentTimeMillis()
            while (isTracking) {
                duration = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        }
    }

    //PATH UPDATING: add point after every passing of time interval
    LaunchedEffect(isTracking, recordingInterval) {
        while (isTracking) {
            try {
                fusedLocationClient
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        loc?.let {
                            val newPoint = PathPoint(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                speed = it.speed,
                                timestamp = System.currentTimeMillis()
                            )
                            if (pathPoints.isNotEmpty()) {
                                val last = pathPoints.last()
                                val results = FloatArray(1)
                                Location.distanceBetween(
                                    last.latitude, last.longitude,
                                    it.latitude, it.longitude,
                                    results
                                )
                                if (results[0] >= 10f) {
                                    val timeChangeSecs = (newPoint.timestamp - last.timestamp) / 1000.0f
                                    val calculatedSpeed =
                                        if (timeChangeSecs > 0) results[0] / timeChangeSecs
                                        else 0f

                                    totalDistanceMeters += results[0]
                                    pathPoints.add(newPoint.copy(speed = calculatedSpeed))
                                }
                            } else {
                                pathPoints.add(newPoint)
                            }
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to get location update", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: SecurityException) {
                // Permission was revoked during tracking
                isTracking = false
                showPermissionDialog = true
            }
            delay(recordingInterval.toLong() * 1000)
        }
    }

    // Helper: Load from Firebase
    LaunchedEffect(showHistory) {
        if (showHistory) {
            val userId = auth.currentUser?.uid ?: return@LaunchedEffect
            database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    savedRoutesList.clear()
                    snapshot.children.forEach { child ->
                        child.getValue(SavedRoute::class.java)?.let { savedRoutesList.add(it) }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    //get segments from path points
    val segments = remember(pathPoints.size) {
        if (pathPoints.size < 2)
            emptyList() //final list of segs: empty
        else {
            //calc speed for every segment
            val rawSpeeds = (0 until pathPoints.size - 1).map { i ->
                val s = pathPoints[i]
                val e = pathPoints[i + 1]

                //d
                val dist = FloatArray(1).also {
                    Location.distanceBetween(
                        s.latitude,
                        s.longitude,
                        e.latitude,
                        e.longitude,
                        it
                    )
                }

                //t
                val secs = (e.timestamp - s.timestamp) / 1000.0

                //s = d / t
                if (secs > 0) dist[0] / secs.toFloat()
                else 0f
            }

            val minSpeed = rawSpeeds.minOrNull() ?: 0f
            val maxSpeed = rawSpeeds.maxOrNull() ?: 1f
            val range = (maxSpeed - minSpeed).coerceAtLeast(0.1f)

            //final list of segs: (start, end, speed)
            rawSpeeds.mapIndexed { i, speed ->
                val s = pathPoints[i]
                val e = pathPoints[i + 1]
                SegmentData(
                    start = LatLng(s.latitude, s.longitude),
                    end = LatLng(e.latitude, e.longitude),
                    speedFactor = ((speed - minSpeed) / range).coerceIn(0f, 1f)
                )
            }
        }
    }

    //colour every segment
    val colouredSegments = remember(segments) {
        if (segments.isEmpty()) emptyList()
        else {
            genColouredSegments(
                segments.map {
                    Triple(it.start, it.end, it.speedFactor)
                }
            )
        }
    }

    //route playback pin updating
    val showPin = !isTracking && pathPoints.size >= 2

    //index of segment that pin is on rn:
    //no of segments covered by pin from start, coerced into zero index
    val pinSegmentIndex =
        if (pathPoints.size < 2) 0
        else (pinFraction * (pathPoints.size - 1)).toInt() - 1

    //(coords of pin, id of closest pathPoint along the line)
    val (pinLatLng, pinNearestPointIndex)
            = remember(pinFraction, pathPoints.size) {
        if (pathPoints.size < 2) LatLng(0.0, 0.0) to 0
        else calcPinPosInfo(pathPoints.toList(), pinFraction)
    }

    val pathPointNearestToPin =
        if (pinNearestPointIndex < pathPoints.size && pathPoints.size >= 2)
            pathPoints[pinNearestPointIndex]
        else null

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    //END END END END END END END END END END END BEGIN LIFECYCLE UPDATES

    //BEGIN BEGIN BEGIN BEGIN BEGIN BEGIN BEGIN BEGIN ui components
    val markerState = rememberMarkerState(position = pinLatLng) //for playback pin

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission.value && isTracking,
                mapStyleOptions = if (UserProfileObject.darkmode)
                    MapStyleOptions(MapStyles.DARK_MAP_STYLE) else null
            ),
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            if (colouredSegments.isNotEmpty()) {
                //thick white line underneath for outline
                segments.forEachIndexed { index, seg ->
                    //should be displayed at full opacity
                    val isPast = (index <= pinSegmentIndex)
                    Polyline(
                        points = listOf(seg.start, seg.end),
                        color = if (isPast) Color.White else Color.White.copy(alpha = 0.20f),
                        width = 30f,
                        jointType = JointType.ROUND,
                        startCap = RoundCap(),
                        endCap = RoundCap(),
                        zIndex = 0f
                    )
                }

                //thin coloured line above to show speed
                colouredSegments.forEachIndexed { index, colSeg ->
                    //should be displayed at full opacity
                    val isPast = (index <= pinSegmentIndex)
                    Polyline(
                        points = listOf(colSeg.p0, colSeg.p1),
                        color = if (isPast) colSeg.color else colSeg.color.copy(alpha = 0.18f),
                        width = 18f,
                        jointType = JointType.ROUND,
                        startCap = if (colSeg.isRouteStart) RoundCap() else ButtCap(),
                        endCap = if (colSeg.isRouteEnd) RoundCap() else ButtCap(),
                        zIndex = 1f
                    )
                }

                //render playback pin
                if (showPin) {

                    //constantly update location + open or not of marker card
                    //WHEN pinLatLng, pinFraction is changed
                    LaunchedEffect(pinLatLng, pinFraction) {
                        //update loc
                        markerState.position = pinLatLng

                        //update open/close
                        if (pinExpanded) markerState.showInfoWindow()
                    }

                    //hovering marker card to show info above pin when clicked
                    MarkerInfoWindow(
                        state = markerState,
                        onClick = {
                            pinExpanded = !pinExpanded
                            if (pinExpanded) markerState.showInfoWindow()
                            else markerState.hideInfoWindow()
                            true
                        },
                        onInfoWindowClose = { pinExpanded = false }
                    ) {
                        pathPointNearestToPin?.let { pt ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                ),
                                border = BorderStroke(1.dp, ElectricYellow.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    InfoDisplayColumn(
                                        label = "Location",
                                        value = "${"%.4f".format(pt.latitude)}, ${"%.4f".format(pt.longitude)}"
                                    )
                                    InfoDisplayColumn(
                                        label = "Speed",
                                        value = "${"%.1f".format(pt.speed * 3.6f)} km/h"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(if (!showPin) Alignment.CenterEnd else Alignment.TopEnd)
                .padding(
                    top = if (showPin) 80.dp else 16.dp,
                    end = 16.dp
                )
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            //1. settings button
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, tint = Color.White, contentDescription = "Settings")
            }

            IconButton(
                onClick = {
                    if (isTracking) {
                        // Stop tracking
                        isTracking = false
                    } else {
                        // Check location and start tracking
                        checkLocationAndStartTracking()
                    }
                },
                enabled = !isCheckingLocation
            ) {
                if (isCheckingLocation) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isTracking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        tint = if (isTracking) NeonLime else Color.White,
                        contentDescription = "Toggle"
                    )
                }
            }

            //3. save button (Only visible if not tracking and route exists)
            AnimatedVisibility(visible = !isTracking && pathPoints.isNotEmpty() && UserProfileObject.isLoggedIn) {
                IconButton(onClick = {
                    if (!isRouteSaved) {
                        saveRoute(
                            database = database,
                            userId = auth.currentUser?.uid ?: "",
                            distance = totalDistanceMeters,
                            duration = duration,
                            points = pathPoints.toList(),
                            context = context
                        )
                        isRouteSaved = true
                    }
                }) {
                    Icon(Icons.Default.Check, tint = NeonLime, contentDescription = "Save")
                }
            }

            //4. clear route button
            IconButton(
                onClick = {
                    pathPoints.clear()
                    totalDistanceMeters = 0.0
                    duration = 0L
                    startTime = 0L
                    pinFraction = 1f
                    pinExpanded = false
                    isRouteSaved = false
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear Path",
                    tint = Color.White
                )
            }

            //5. saved routes button
            if (UserProfileObject.isLoggedIn) {
                IconButton(onClick = { showHistory = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        tint = Color.White,
                        contentDescription = "History"
                    )
                }
            }
        }

        //PLAYBACK CARD
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = showPin,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ),
                    border = BorderStroke(1.dp, ElectricYellow.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Route Playback",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            pathPointNearestToPin?.let { pt ->
                                Text(
                                    text = timeFormatter.format(Date(pt.timestamp)),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .align(Alignment.Center)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                getColourFromSpeed(0f),
                                                getColourFromSpeed(0.5f),
                                                getColourFromSpeed(1f)
                                            )
                                        )
                                    )
                            )
                            Slider(
                                value = pinFraction,
                                onValueChange = {
                                    pinFraction = it
                                    pinExpanded = false
                                    markerState.hideInfoWindow()
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.onSurface,
                                    activeTrackColor = Color.Transparent,
                                    inactiveTrackColor = Color.Transparent,
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent
                                )
                            )
                        }

                        if (pathPoints.size >= 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = timeFormatter.format(Date(pathPoints.first().timestamp)),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = timeFormatter.format(Date(pathPoints.last().timestamp)),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // STATS CARD
            Card(
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
                    InfoDisplayColumn("Duration", formatDuration(duration))
                    InfoDisplayColumn("Distance", "%.2f km".format(totalDistanceMeters / 1000.0))
                    InfoDisplayColumn(
                        label = "Avg Speed",
                        value = if (duration > 0)
                            "%.1f km/h".format((totalDistanceMeters / duration.toDouble()) * 3.6)
                        else "—"
                    )
                }
            }
        }

        // Permission Dialog
        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Location Permission Required") },
                text = {
                    Text("Precise location is needed to track your route. Please enable location permission in settings.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPermissionDialog = false
                            Toast.makeText(
                                context,
                                "Please enable location permission in app settings",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showHistory) {
            AlertDialog(
                onDismissRequest = { showHistory = false },
                title = { Text("Saved Routes") },
                text = {
                    Box(modifier = Modifier.height(300.dp)) {
                        if (savedRoutesList.isEmpty()) {
                            Text("No routes saved yet.", modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn {
                                items(savedRoutesList.reversed()) { route -> // Use reversed list directly
                                    ListItem(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // LOAD LOGIC
                                                isTracking = false
                                                pathPoints.clear()
                                                pathPoints.addAll(route.points)
                                                totalDistanceMeters = route.distanceMeters
                                                duration = route.durationSeconds
                                                startTime = 0L // Mark as static load
                                                isRouteSaved = true // Prevents re-saving the same route

                                                // Move camera to start of route
                                                if (route.points.isNotEmpty()) {
                                                    cameraPositionState.position = CameraPosition.fromLatLngZoom(
                                                        LatLng(route.points[0].latitude, route.points[0].longitude),
                                                        15f
                                                    )
                                                }
                                                showHistory = false
                                            },
                                        headlineContent = {
                                            Text(SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(route.timestamp)))
                                        },
                                        supportingContent = {
                                            Text("${"%.2f".format(route.distanceMeters / 1000.0)} km | ${formatDuration(route.durationSeconds)}")
                                        },
                                        trailingContent = {
                                            IconButton(onClick = {
                                                deleteRoute(database, auth.currentUser?.uid ?: "", route.id)
                                                savedRoutesList.remove(route) // Optimistic UI update
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                                            }
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHistory = false }) { Text("Close") }
                }
            )
        }

        if (showSettings) {
            IntervalSettingsDialog(
                currentInterval = recordingInterval,
                onIntervalChange = { recordingInterval = it },
                onDismiss = { showSettings = false }
            )
        }
    }
    //END END END END END END END END END END END ui components
}

//BEGIN BEGIN BEGIN BEGIN BEGIN BEGIN BEGIN BEGIN util

fun formatDuration(seconds: Long): String {
    val hours = TimeUnit.SECONDS.toHours(seconds)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val secs = seconds % 60

    if (hours > 0) return "%02d:%02d:%02d".format(hours, minutes, secs)
    else return "%02d:%02d".format(minutes, secs)
}

//linear interpolation: find value at fraction t between a and b
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

fun getColourFromSpeed(speed: Float): Color {
    //normalise v
    val speedFactor = speed.coerceIn(0f, 1f)

    if (speedFactor <= 0.5f) { //first half of speed range (green -> yellow) equals slow
        val f = speedFactor / 0.5f
        val r = lerp(0.13f, 1.00f, f)
        val g = lerp(0.70f, 0.85f, f) //more green
        val b = lerp(0.13f, 0.00f, f)

        return Color(red = r, green = g, blue = b)
    } else { //second half of speed range (yellow -> red) equals fast
        val f = (speedFactor - 0.5f) / 0.5f
        val r = lerp(1.00f, 0.85f, f) //more red
        val g = lerp(0.85f, 0.10f, f)
        val b = lerp(0.00f, 0.10f, f)

        return Color(red = r, green = g, blue = b)
    }
}

fun genColouredSegments(
    segments: List<Triple<LatLng, LatLng, Float>>,
): List<ColouredSegment> {
    val result = mutableListOf<ColouredSegment>()
    val lastSegIndex = segments.lastIndex //index of last segment to colour (to show)

    segments.forEachIndexed { segIndex, (start, end, speedFactor) ->
        val startSpeedFactor =
            //first seg; just use cur seg
            if (segIndex == 0) speedFactor

            //average of prev seg, cur seg
            else (segments[segIndex - 1].third + speedFactor) / 2f

        val endSpeedFactor =
            //last seg; just use cur seg
            if (segIndex == lastSegIndex) speedFactor

            //average of next seg, cur seg
            else (speedFactor + segments[segIndex + 1].third) / 2f

        val p0 = LatLng(start.latitude, start.longitude)
        val p1 = LatLng(end.latitude, end.longitude)
        val color = getColourFromSpeed(
            lerp(startSpeedFactor, endSpeedFactor, 0.5f)
        )

        result.add(
            ColouredSegment(
                p0 = p0,
                p1 = p1,
                color = color,
                isRouteStart = segIndex == 0,
                isRouteEnd = segIndex == lastSegIndex
            )
        )
    }
    return result
}

//return (coords, index of last point passed)
fun calcPinPosInfo(points: List<PathPoint>, fraction: Float): Pair<LatLng, Int> {
    if (points.size == 1) return LatLng(points[0].latitude, points[0].longitude) to 0

    //find index of segment the pin is on
    // + fraction of last seg
    //|oooooo|ooooo|oooo---|
    //             ^   ^   ^
    //      segIndex   f   segCount
    val segCount = points.size - 1    //tot no of segments
    val raw = fraction * segCount  //as float
    val segIndex = raw.toInt() //as int (floored)
        .coerceIn(0, segCount - 1)  //(must be smaller than segCount; make 0 indexed)
    val f = raw - segIndex  //(fractional part)

    //coarse tune (get range of segment)
    val s = points[segIndex]
    val e = points[segIndex + 1]
    //fine tune (get linear displacement from start point)
    val lat = lerp(s.latitude.toFloat(), e.latitude.toFloat(), f).toDouble()
    val lng = lerp(s.longitude.toFloat(), e.longitude.toFloat(), f).toDouble()

    val nearestIndex = if (f < 0.5f) segIndex else segIndex + 1
    return LatLng(lat, lng) to nearestIndex
}

/**
 * Handles the Firebase push logic outside of the UI tree.
 */
private fun saveRoute(
    database: com.google.firebase.database.DatabaseReference,
    userId: String,
    distance: Double,
    duration: Long,
    points: List<PathPoint>,
    context: Context
) {
    if (userId.isEmpty()) {
        Toast.makeText(context, "Log in to save route", Toast.LENGTH_SHORT).show()
        return
    }

    val routeId = database.child(userId).push().key ?: return
    val newRoute = SavedRoute(
        id = routeId,
        timestamp = System.currentTimeMillis(),
        distanceMeters = distance,
        durationSeconds = duration,
        points = points
    )
    database.child(userId).child(routeId).setValue(newRoute)

    Toast.makeText(context, "Route saved to database", Toast.LENGTH_SHORT).show()
}

private fun deleteRoute(
    database: com.google.firebase.database.DatabaseReference,
    userId: String,
    routeId: String
) {
    if (userId.isEmpty() || routeId.isEmpty()) return
    database.child(userId).child(routeId).removeValue()
}
//END END END END END END END END END END END UTIL