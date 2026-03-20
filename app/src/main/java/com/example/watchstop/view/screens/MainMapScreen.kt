package com.example.watchstop.view.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Point
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
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
import com.example.watchstop.activities.X
import com.example.watchstop.data.UserGeofencesDatabase
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.model.GeofenceArea
import com.example.watchstop.data.FirebaseRepository
import com.example.watchstop.view.ui.theme.MapStyles.DARK_MAP_STYLE
import com.example.watchstop.view.ui.theme.WatchStopTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlin.math.*

// Geofence Type Constants
const val GEOFENCE_TYPE_CIRCULAR = 1
const val GEOFENCE_TYPE_POLYGONAL = 2

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
    val auth = remember { FirebaseAuth.getInstance() }
    val firebaseDb = remember { FirebaseDatabase.getInstance().reference }

    val snackbarHostState = remember { SnackbarHostState() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val geofences = remember { mutableStateListOf<GeofenceArea>() }
    var showHistory by remember { mutableStateOf(false) }
    val cloudGeofencesList = remember { mutableStateListOf<GeofenceArea>() }

    var interactionMode by remember { mutableStateOf(MapInteractionMode.NONE) }
    var currentPin by remember { mutableStateOf<LatLng?>(null) }
    var radius by remember { mutableFloatStateOf(100f) }

    //fetch existing geofences from firebase for list
    LaunchedEffect(showHistory) {
        if (showHistory) {
            val userId = auth.currentUser?.uid ?: return@LaunchedEffect
            firebaseDb.child("geofences").child(userId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        cloudGeofencesList.clear()
                        snapshot.children.forEach { child ->
                            try {
                                val id = child.child("id").getValue(String::class.java) ?: return@forEach
                                val name = child.child("name").getValue(String::class.java) ?: ""
                                val typeId = child.child("typeId").getValue(Int::class.java) ?: GEOFENCE_TYPE_CIRCULAR
                                val radius = child.child("radius").getValue(Double::class.java) ?: 0.0
                                val geoAlarmId = child.child("geoAlarmId").getValue(String::class.java)

                                val centerSnap = child.child("center")
                                val centerLat = centerSnap.child("lat").getValue(Double::class.java) ?: 0.0
                                val centerLng = centerSnap.child("lng").getValue(Double::class.java) ?: 0.0
                                val center = LatLng(centerLat, centerLng)

                                val points = child.child("points").children.mapNotNull { ptSnap ->
                                    val lat = ptSnap.child("lat").getValue(Double::class.java)
                                    val lng = ptSnap.child("lng").getValue(Double::class.java)
                                    if (lat != null && lng != null) LatLng(lat, lng) else null
                                }

                                cloudGeofencesList.add(
                                    GeofenceArea(
                                        id = id,
                                        name = name,
                                        center = center,
                                        typeId = typeId,
                                        radius = radius,
                                        points = points,
                                        geoAlarmId = geoAlarmId
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e("MyGoogleMap", "Failed to parse geofence: ${e.message}")
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    //sync db on startup
    LaunchedEffect(Unit) {
        geofences.clear()
        geofences.addAll(UserGeofencesDatabase.getAllGeofences())
    }

    //naming geofence
    var pendingGeofence by remember { mutableStateOf<GeofenceArea?>(null) }
    var geofenceNameInput by remember { mutableStateOf("") }

    //drawing geofence
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
                    if (zone.typeId == GEOFENCE_TYPE_CIRCULAR || zone.points.isEmpty()) {
                        Circle(
                            center = zone.center,
                            radius = zone.radius,
                            fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            strokeColor = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2f,
                            clickable = true,
                            onClick = {
                                if (interactionMode == MapInteractionMode.DELETE) {
                                    // Delete from local
                                    geofences.remove(zone)
                                    UserGeofencesDatabase.removeGeofence(zone, context)

                                    Toast.makeText(context, "Geofence removed locally", Toast.LENGTH_SHORT).show()

                                    // Delete from Firebase using the correct ID
                                    val uid = auth.currentUser?.uid
                                    if (uid != null) {
                                        FirebaseRepository.deleteGeofenceFromFirebase(
                                            database = firebaseDb,
                                            userId = uid,
                                            geofenceId = zone.id,  // Use ID, not name
                                            context = context
                                        )
                                    }

                                    Toast.makeText(context, "Geofence removed from Cloud", Toast.LENGTH_SHORT).show()
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
                                    // Delete from local
                                    geofences.remove(zone)
                                    UserGeofencesDatabase.removeGeofence(zone, context)

                                    // Delete from Firebase
                                    val uid = auth.currentUser?.uid
                                    if (uid != null) {
                                        firebaseDb.child("geofences").child(uid).child(zone.id).removeValue()
                                            .addOnSuccessListener {
                                                Toast.makeText(context, "Geofence removed from cloud", Toast.LENGTH_SHORT).show()
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(context, "Failed to removed from cloud", Toast.LENGTH_SHORT).show()
                                            }
                                    }

                                    Toast.makeText(context, "Geofence removed locally", Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch {
                                        cameraPositionState.animate(update = CameraUpdateFactory.newLatLngZoom(zone.center, 15f), durationMs = 1000)
                                    }
                                }
                            }
                        )
                    }

                    //top label
                    Marker(
                        state = MarkerState(position = zone.center),
                        title = zone.name,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                        alpha = 0.8f
                    )
                }

                //pending: while naming
                pendingGeofence?.let { zone ->
                    if (zone.typeId == GEOFENCE_TYPE_CIRCULAR || zone.points.isEmpty()) {
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

            //show saved geofences
            if (showHistory) {
                AlertDialog(
                    onDismissRequest = { showHistory = false },
                    title = { Text("Saved Geofences",
                        fontSize = MaterialTheme.typography.titleLarge.fontSize * X.value) },
                    text = {
                        Box(modifier = Modifier.height(300.dp)) {
                            if (cloudGeofencesList.isEmpty()) {
                                Text("No geofences found.", modifier = Modifier.align(Alignment.Center),
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize * X.value)
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn {
                                    items(cloudGeofencesList) { zone ->
                                        ListItem(
                                            modifier = Modifier.clickable {
                                                // Add the geofence to the local list if not already present
                                                if (!geofences.any { it.id == zone.id }) {
                                                    geofences.add(zone)
                                                    UserGeofencesDatabase.addGeofence(zone, context)
                                                }

                                                // Center on the geofence
                                                scope.launch {
                                                    cameraPositionState.animate(
                                                        update = CameraUpdateFactory.newLatLngZoom(zone.center, 15f),
                                                        durationMs = 1000
                                                    )
                                                }
                                                showHistory = false
                                            },
                                            headlineContent = { Text(text = zone.name,
                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value) },
                                            supportingContent = {
                                                val type = when (zone.typeId) {
                                                    GEOFENCE_TYPE_CIRCULAR -> "Circular"
                                                    GEOFENCE_TYPE_POLYGONAL -> "Polygonal"
                                                    else -> if (zone.points.isEmpty()) "Circular" else "Polygonal"
                                                }
                                                Text(text = "$type • ${zone.radius.toInt()}m radius",
                                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value)
                                            },
                                            trailingContent = {
                                                IconButton(onClick = {
                                                    // Remove from cloud list
                                                    cloudGeofencesList.remove(zone)

                                                    // Remove from local geofences list
                                                    geofences.removeIf { it.id == zone.id }

                                                    // Remove from local database
                                                    UserGeofencesDatabase.removeGeofence(zone, context)

                                                    // Remove from Firebase using the correct ID
                                                    val uid = auth.currentUser?.uid
                                                    if (uid != null) {
                                                        FirebaseRepository.deleteGeofenceFromFirebase(
                                                            database = firebaseDb,
                                                            userId = uid,
                                                            geofenceId = zone.id,  // Use ID, not name
                                                            context = context
                                                        )
                                                    }
                                                }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
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
                        TextButton(onClick = { showHistory = false }) {
                            Text("Close", fontSize = MaterialTheme.typography.titleLarge.fontSize * X.value) }
                    }
                )
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
                                                typeId = GEOFENCE_TYPE_POLYGONAL,
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

            //naming popup box
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
                            Text("Name your Geofence", fontWeight = FontWeight.Bold, fontSize = 18.sp * X.value)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = geofenceNameInput,
                                    onValueChange = { geofenceNameInput = it },
                                    placeholder = { Text(text = "e.g. Home",
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * X.value) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                IconButton(
                                    onClick = {
                                        if (geofenceNameInput.isNotBlank()) {
                                            val finalGeofence = it.copy(name = geofenceNameInput)
                                            geofences.add(finalGeofence)
                                            UserGeofencesDatabase.addGeofence(finalGeofence, context)
                                            pendingGeofence = null
                                            Toast.makeText(context, "Geofence saved locally", Toast.LENGTH_SHORT).show()

                                            if (UserProfileObject.isLoggedIn) {
                                                FirebaseRepository.saveGeofenceToFirebase(firebaseDb, finalGeofence, context)
                                            }
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
                                Text("Cancel",
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value)
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
                        //1. pin button
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

                        //2. draw button
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

                        //3. confirm button
                        if (currentPin != null) {
                            IconButton(onClick = {
                                currentPin?.let {
                                    pendingGeofence = GeofenceArea(
                                        name = "",
                                        center = it,
                                        typeId = GEOFENCE_TYPE_CIRCULAR,
                                        radius = radius.toDouble(),
                                        points = emptyList()
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

                        //4. delete button
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

                        //5. saved geofences list button
                        AnimatedVisibility (UserProfileObject.isLoggedIn) {
                            IconButton(onClick = { showHistory = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "History")
                            }
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
                        Text(text = "Geofence Radius: ${radius.toInt()}m", style = MaterialTheme.typography.labelMedium,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * X.value)
                        Slider(
                            value = radius,
                            onValueChange = { radius = it },
                            valueRange = 10f..2500f,
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