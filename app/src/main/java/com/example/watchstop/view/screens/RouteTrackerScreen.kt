@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.watchstop.view.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
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
import com.example.watchstop.view.ui.theme.ElectricYellow
import com.example.watchstop.view.ui.theme.NeonLime
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RouteTrackerScreen() {
    val context = LocalContext.current

    //settings
    var isTracking by remember { mutableStateOf(false) }
    var recordingInterval by remember { mutableFloatStateOf(10f) }
    var showSettings by remember { mutableStateOf(false) }

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
                        }
                        else pathPoints.add(newPoint)
                    }
                }
            delay(recordingInterval.toLong() * 1000)
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

        //COLUMN CONTROL PANEL
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            //settings button
            IconButton(onClick = { showSettings = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    tint = Color.White,
                    contentDescription = "Interval Settings"
                )
            }

            //play / stop
            IconButton(onClick = { isTracking = !isTracking }) {
                Icon(
                    imageVector =
                        if (isTracking) Icons.Default.Pause
                        else Icons.Default.PlayArrow,
                    contentDescription = "Toggle Tracking",
                    tint =
                        if (isTracking) NeonLime
                        else Color.White
                )
            }

            //clear route
            IconButton(
                onClick = {
                    pathPoints.clear()
                    totalDistanceMeters = 0.0
                    duration = 0L
                    startTime = 0L
                    pinFraction = 1f
                    pinExpanded = false
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear Path",
                    tint = Color.White
                )
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

//END END END END END END END END END END END UTIL