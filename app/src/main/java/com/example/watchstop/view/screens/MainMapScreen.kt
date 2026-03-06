@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.watchstop.view.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Point
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.model.GeofenceArea
import com.example.watchstop.view.ui.theme.WatchStopTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlin.math.*

@Composable
fun MainMapScreen() {
    WatchStopTheme(darkTheme = UserProfileObject.darkmode) {
        MyGoogleMap()
    }
}

enum class MapInteractionMode {
    NONE, SET_PIN, DELETE, DRAW
}

@Composable
fun MyGoogleMap() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    val geofences = remember { mutableStateListOf<GeofenceArea>() }
    var interactionMode by remember { mutableStateOf(MapInteractionMode.NONE) }
    var currentPin by remember { mutableStateOf<LatLng?>(null) }
    var radius by remember { mutableFloatStateOf(100f) }

    // Drawing states
    var currentDrawingPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var failingPolygon by remember { mutableStateOf<List<Offset>?>(null) }
    val failingAlpha = remember { Animatable(0f) }

    // Check for location permissions
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }
    )

    val singapore = LatLng(1.3521, 103.8198)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(singapore, 12f)
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(
                            LatLng(it.latitude, it.longitude), 15f
                        )
                    }
                }
            } catch (e: SecurityException) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = hasLocationPermission,
                scrollGesturesEnabled = interactionMode != MapInteractionMode.DRAW,
                zoomGesturesEnabled = interactionMode != MapInteractionMode.DRAW
            ),
            onMapClick = { latLng ->
                if (interactionMode == MapInteractionMode.SET_PIN) {
                    currentPin = latLng
                    interactionMode = MapInteractionMode.NONE
                    Toast.makeText(context, "Center set!", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            currentPin?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Geofence center",
                    snippet = "Selected position"
                )
                Circle(
                    center = it,
                    radius = radius.toDouble(),
                    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    strokeWidth = 2f
                )
            }

            geofences.forEach { zone ->
                if (zone.points.isEmpty()) {
                    Circle(
                        center = zone.center,
                        radius = zone.radius,
                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        strokeColor = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2f,
                        clickable = true,
                        onClick = {
                            if (interactionMode == MapInteractionMode.DELETE) {
                                geofences.remove(zone)
                                Toast.makeText(context, "Geofence deleted", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    cameraPositionState.animate(
                                        update = CameraUpdateFactory.newLatLngZoom(zone.center, 15f),
                                        durationMs = 1000
                                    )
                                }
                            }
                        }
                    )
                } else {
                    Polygon(
                        points = zone.points,
                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        strokeColor = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2f,
                        clickable = true,
                        onClick = {
                            if (interactionMode == MapInteractionMode.DELETE) {
                                geofences.remove(zone)
                                Toast.makeText(context, "Geofence deleted", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    cameraPositionState.animate(
                                        update = CameraUpdateFactory.newLatLngZoom(zone.center, 15f),
                                        durationMs = 1000
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        // Overlay for drawing
        if (interactionMode == MapInteractionMode.DRAW) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentDrawingPoints = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentDrawingPoints = currentDrawingPoints + change.position
                            },
                            onDragEnd = {
                                if (currentDrawingPoints.size > 2) {
                                    val points = currentDrawingPoints
                                    val closed = points + points.first()
                                    
                                    val area = calculateArea(closed)
                                    val perimeter = calculatePerimeter(closed)

                                    if (area < (perimeter * perimeter) / 100.0) {
                                        failingPolygon = points
                                        Toast.makeText(context, "Shape is too thin!", Toast.LENGTH_SHORT).show()
                                        scope.launch {
                                            failingAlpha.snapTo(1f)
                                            failingAlpha.animateTo(0f, tween(2000))
                                            failingPolygon = null
                                        }
                                    } else {
                                        val projection = cameraPositionState.projection
                                        val latLngs = points.mapNotNull { offset ->
                                            projection?.fromScreenLocation(Point(offset.x.toInt(), offset.y.toInt()))
                                        }
                                        if (latLngs.isNotEmpty()) {
                                            val (center, radiusMeters) = findMEC(latLngs)
                                            geofences.add(GeofenceArea(typeId = 2, center = center, radius = radiusMeters, points = latLngs))
                                            Toast.makeText(context, "Custom geofence added", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                currentDrawingPoints = emptyList()
                                interactionMode = MapInteractionMode.NONE
                            }
                        )
                    }
            ) {
                if (currentDrawingPoints.isNotEmpty()) {
                    val path = Path().apply {
                        moveTo(currentDrawingPoints.first().x, currentDrawingPoints.first().y)
                        currentDrawingPoints.forEach { lineTo(it.x, it.y) }
                        lineTo(currentDrawingPoints.first().x, currentDrawingPoints.first().y)
                    }
                    drawPath(path, Color.Blue, style = Stroke(width = 5f))
                }
            }
        }

        // Animation for shape
        failingPolygon?.let { points ->
            val closed = points + points.first()
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(closed.first().x, closed.first().y)
                    closed.forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, Color.Red.copy(alpha = failingAlpha.value), style = Stroke(width = 8f))
            }
        }

        // Toolbar
        Card(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        interactionMode = MapInteractionMode.SET_PIN
                        Toast.makeText(context, "Tap on map to set geofence center", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (interactionMode == MapInteractionMode.SET_PIN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                
                IconButton(
                    onClick = {
                        interactionMode = MapInteractionMode.DRAW
                        Toast.makeText(context, "Draw your Geofence!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Draw Geofence",
                        tint = if (interactionMode == MapInteractionMode.DRAW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = {
                        currentPin?.let {
                            geofences.add(GeofenceArea(typeId = 1, center = it, radius = radius.toDouble()))
                            currentPin = null
                            Toast.makeText(context, "Circular geofence added", Toast.LENGTH_SHORT).show()
                        } ?: run {
                            Toast.makeText(context, "Set a pin first!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.RadioButtonUnchecked, contentDescription = "Add Circular Geofence")
                }

                IconButton(
                    onClick = {
                        if (interactionMode != MapInteractionMode.DELETE) {
                            interactionMode = MapInteractionMode.DELETE
                            Toast.makeText(context, "Entered Delete Mode", Toast.LENGTH_LONG).show()
                        } else {
                            interactionMode = MapInteractionMode.NONE
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Mode",
                        tint = if (interactionMode == MapInteractionMode.DELETE) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Radius adjustment bar
        if (currentPin != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Geofence Radius: ${radius.toInt()}m",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = radius,
                        onValueChange = { radius = it },
                        valueRange = 10f..250f,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
        
        if (geofences.isNotEmpty()) {
            Button(
                onClick = { geofences.clear() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Text("Clear (${geofences.size} Geofences)")
            }
        }
    }
}

// Geometry helpers
fun calculateArea(points: List<Offset>): Double {
    var area = 0.0
    for (i in 0 until points.size - 1) {
        area += (points[i].x * points[i+1].y - points[i+1].x * points[i].y)
    }
    return abs(area) / 2.0
}

fun calculatePerimeter(points: List<Offset>): Double {
    var perimeter = 0.0
    for (i in 0 until points.size - 1) {
        val dx = points[i].x - points[i+1].x
        val dy = points[i].y - points[i+1].y
        perimeter += sqrt((dx * dx + dy * dy).toDouble())
    }
    return perimeter
}

fun findMEC(latLngs: List<LatLng>): Pair<LatLng, Double> {
    if (latLngs.isEmpty()) return LatLng(0.0, 0.0) to 0.0
    if (latLngs.size == 1) return latLngs[0] to 0.0
    
    // Ritter's algorithm for approximate MEC
    var p = latLngs[0]
    var q = latLngs.maxBy { dist(p, it) }!!
    var r = latLngs.maxBy { dist(q, it) }!!
    
    var center = LatLng((q.latitude + r.latitude) / 2.0, (q.longitude + r.longitude) / 2.0)
    var radius = dist(q, r) / 2.0
    
    for (s in latLngs) {
        val d = dist(center, s)
        if (d > radius) {
            val newRadius = (radius + d) / 2.0
            val ratio = (d - radius) / (2.0 * d)
            center = LatLng(
                center.latitude + (s.latitude - center.latitude) * ratio,
                center.longitude + (s.longitude - center.longitude) * ratio
            )
            radius = newRadius
        }
    }
    return center to radius
}

fun dist(p1: LatLng, p2: LatLng): Double {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
    return results[0].toDouble()
}
