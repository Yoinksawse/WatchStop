@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.watchstop.view.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Point
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.watchstop.data.UserGeofencesDatabase
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.model.GeofenceArea
import com.example.watchstop.view.ui.theme.WatchStopTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
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

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MyGoogleMap() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    val geofences = remember { mutableStateListOf<GeofenceArea>() }
    var interactionMode by remember { mutableStateOf(MapInteractionMode.NONE) }
    var currentPin by remember { mutableStateOf<LatLng?>(null) }
    var radius by remember { mutableFloatStateOf(100f) }

    // Sync with database on start
    LaunchedEffect(Unit) {
        geofences.addAll(UserGeofencesDatabase.getAllGeofences())
    }

    // Naming state
    var pendingGeofence by remember { mutableStateOf<GeofenceArea?>(null) }
    var geofenceNameInput by remember { mutableStateOf("") }

    // Drawing states
    var currentDrawingPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var failingPolygon by remember { mutableStateOf<List<Offset>?>(null) }
    val failingAlpha = remember { Animatable(0f) }

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
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                    }
                }
            } catch (e: SecurityException) {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission,
                    mapStyleOptions = if (UserProfileObject.darkmode) MapStyleOptions(DARK_MAP_STYLE) else null
                ),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = hasLocationPermission,
                    scrollGesturesEnabled = interactionMode != MapInteractionMode.DRAW && pendingGeofence == null,
                    zoomGesturesEnabled = interactionMode != MapInteractionMode.DRAW && pendingGeofence == null
                ),
                onMapClick = { latLng ->
                    if (pendingGeofence != null) return@GoogleMap
                    
                    if (interactionMode == MapInteractionMode.SET_PIN) {
                        currentPin = latLng
                        snackbarHostState.currentSnackbarData?.dismiss()
                        Toast.makeText(context, "Center set! Adjust radius and confirm.", Toast.LENGTH_SHORT).show()
                    } else {
                        currentPin = null
                        interactionMode = MapInteractionMode.NONE
                        snackbarHostState.currentSnackbarData?.dismiss()
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
                                    UserGeofencesDatabase.removeGeofence(zone)
                                    Toast.makeText(context, "Geofence deleted", Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch {
                                        cameraPositionState.animate(update = CameraUpdateFactory.newLatLngZoom(zone.center, 15f), durationMs = 1000)
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
                                    UserGeofencesDatabase.removeGeofence(zone)
                                    Toast.makeText(context, "Geofence deleted", Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch {
                                        cameraPositionState.animate(update = CameraUpdateFactory.newLatLngZoom(zone.center, 15f), durationMs = 1000)
                                    }
                                }
                            }
                        )
                    }
                    
                    // Label for confirmed geofence
                    Marker(
                        state = MarkerState(position = zone.center),
                        title = zone.name,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                        alpha = 0.8f
                    )
                }
                
                // Show pending geofence preview while naming
                pendingGeofence?.let { zone ->
                    if (zone.points.isEmpty()) {
                        Circle(
                            center = zone.center,
                            radius = zone.radius,
                            fillColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                            strokeColor = MaterialTheme.colorScheme.secondary,
                            strokeWidth = 3f
                        )
                    } else {
                        Polygon(
                            points = zone.points,
                            fillColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                            strokeColor = MaterialTheme.colorScheme.secondary,
                            strokeWidth = 3f
                        )
                    }
                }
            }

            if (interactionMode == MapInteractionMode.DRAW && pendingGeofence == null) {
                Canvas(
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> currentDrawingPoints = listOf(offset) },
                            onDrag = { change, _ ->
                                change.consume()
                                currentDrawingPoints = currentDrawingPoints + change.position
                            },
                            onDragEnd = {
                                if (currentDrawingPoints.size > 2) {
                                    val points = currentDrawingPoints
                                    val area = calculateArea(points + points.first())
                                    val perimeter = calculatePerimeter(points + points.first())

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
                                        val latLngs = points.mapNotNull { offset -> projection?.fromScreenLocation(Point(offset.x.toInt(), offset.y.toInt())) }
                                        if (latLngs.isNotEmpty()) {
                                            val (center, radiusMeters) = findMEC(latLngs)
                                            pendingGeofence = GeofenceArea(
                                                name = "",
                                                center = center,
                                                typeId = 2,
                                                radius = radiusMeters,
                                                points = latLngs
                                            )
                                            geofenceNameInput = ""
                                        }
                                    }
                                }
                                currentDrawingPoints = emptyList()
                                interactionMode = MapInteractionMode.NONE
                                snackbarHostState.currentSnackbarData?.dismiss()
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

            failingPolygon?.let { points ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        (points + points.first()).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, Color.Red.copy(alpha = failingAlpha.value), style = Stroke(width = 8f))
                }
            }

            // Naming Popup Overlay
            pendingGeofence?.let {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(16.dp).width(280.dp),
                        elevation = CardDefaults.cardElevation(8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Name your Geofence", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = geofenceNameInput,
                                    onValueChange = { geofenceNameInput = it },
                                    placeholder = { Text("e.g. Home") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                IconButton(
                                    onClick = {
                                        if (geofenceNameInput.isNotBlank()) {
                                            val finalGeofence = it.copy(name = geofenceNameInput)
                                            geofences.add(finalGeofence)
                                            UserGeofencesDatabase.addGeofence(finalGeofence)
                                            pendingGeofence = null
                                            Toast.makeText(context, "Geofence saved", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            TextButton(
                                onClick = { pendingGeofence = null },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            // Toolbar
            if (pendingGeofence == null) {
                Card(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = {
                            interactionMode = MapInteractionMode.SET_PIN
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result = snackbarHostState.showSnackbar(
                                    message = "Tap on map to set geofence center",
                                    actionLabel = "Cancel",
                                    duration = SnackbarDuration.Indefinite
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    interactionMode = MapInteractionMode.NONE
                                    currentPin = null
                                }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = if (interactionMode == MapInteractionMode.SET_PIN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        
                        IconButton(onClick = {
                            currentPin = null
                            interactionMode = MapInteractionMode.DRAW
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result = snackbarHostState.showSnackbar(
                                    message = "Draw on the map to create a geofence",
                                    actionLabel = "Cancel",
                                    duration = SnackbarDuration.Indefinite
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    interactionMode = MapInteractionMode.NONE
                                }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Draw Geofence", tint = if (interactionMode == MapInteractionMode.DRAW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }

                        if (currentPin != null) {
                            IconButton(onClick = {
                                currentPin?.let {
                                    pendingGeofence = GeofenceArea(
                                        name = "",
                                        center = it,
                                        typeId = 1,
                                        radius = radius.toDouble()
                                    )
                                    geofenceNameInput = ""
                                    currentPin = null
                                    interactionMode = MapInteractionMode.NONE
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                }
                            }) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = "Confirm Circular Geofence", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        IconButton(onClick = {
                            currentPin = null
                            if (interactionMode != MapInteractionMode.DELETE) {
                                interactionMode = MapInteractionMode.DELETE
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Entered Delete Mode: Click a geofence to delete",
                                        actionLabel = "Exit",
                                        duration = SnackbarDuration.Indefinite
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        interactionMode = MapInteractionMode.NONE
                                    }
                                }
                            } else {
                                interactionMode = MapInteractionMode.NONE
                                snackbarHostState.currentSnackbarData?.dismiss()
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Mode", tint = if (interactionMode == MapInteractionMode.DELETE) Color.Red else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            if (currentPin != null && pendingGeofence == null) {
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).padding(horizontal = 24.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults
                        .cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Geofence Radius: ${radius.toInt()}m", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = radius,
                            onValueChange = { radius = it },
                            valueRange = 10f..250f,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
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
    if (latLngs.size == 1) return LatLng(0.0, 0.0) to 0.0
    
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
            center = LatLng(center.latitude + (s.latitude - center.latitude) * ratio, center.longitude + (s.longitude - center.longitude) * ratio)
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

private const val DARK_MAP_STYLE = """
[
  {
    "elementType": "geometry",
    "stylers": [
      {
        "color": "#242f3e"
      }
    ]
  },
  {
    "elementType": "labels.text.fill",
    "stylers": [
      {
        "color": "#746855"
      }
    ]
  },
  {
    "elementType": "labels.text.stroke",
    "stylers": [
      {
        "color": "#242f3e"
      }
    ]
  },
  {
    "featureType": "administrative.locality",
    "elementType": "labels.text.fill",
    "stylers": [
      {
        "color": "#d59563"
      }
    ]
  },
  {
    "featureType": "poi",
    "elementType": "labels.text.fill",
    "stylers": [
      {
        "color": "#d59563"
      }
    ]
  },
  {
    "featureType": "poi.park",
    "elementType": "geometry",
    "stylers": [
      {
        "color": "#263c3f"
      }
    ]
  },
  {
    "featureType": "poi.park",
    "elementType": "labels.text.fill",
    "stylers": [
      {
        "color": "#6b9a76"
      }
    ]
  },
  {
    "featureType": "road",
    "elementType": "geometry",
    "stylers": [
      {
        "color": "#38414e"
      }
    ]
  },
  {
    "featureType": "road",
    "elementType": "geometry.stroke",
    "stylers": [
      {
        "color": "#212a37"
      }
    ]
  },
  {
    "featureType": "road",
    "elementType": "labels.text.fill",
    "stylers": [
      {
        "color": "#9ca5b3"
      }
    ]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry",
    "stylers": [
      {
        "color": "#746855"
      }
    ]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry.stroke",
    "stylers": [
      {
        "color": "#1f2835"
      }
    ]
  },
  {
    "featureType": "road.highway",
    "elementType": "labels.text.fill",
    "stylers": [
      {
        "color": "#f3d19c"
      }
    ]
  },
  {
    "featureType": "transit",
    "elementType": "geometry",
    "stylers": [
      {
        "color": "#2f3948"
      }
    ]
  },
  {
    "featureType": "transit.station",
    "elementType": "labels.text.fill",
    "stylers": [
      {
        "color": "#d59563"
      }
    ]
  },
  {
    "featureType": "water",
    "elementType": "geometry",
    "stylers": [
      {
        "color": "#17263c"
      }
    ]
  },
  {
    "featureType": "water",
    "elementType": "labels.text.fill",
    "stylers": [
      {
        "color": "#515c6d"
      }
    ]
  },
  {
    "featureType": "water",
    "elementType": "labels.text.stroke",
    "stylers": [
      {
        "color": "#17263c"
      }
    ]
  }
]
"""
