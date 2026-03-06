@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.watchstop.view.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
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

@Composable
fun MainMapScreen() {
    WatchStopTheme(darkTheme = UserProfileObject.darkmode) {
        MyGoogleMap()
    }
}

enum class MapInteractionMode {
    NONE, SET_PIN, DELETE
}

@Composable
fun MyGoogleMap() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    val geofences = remember { mutableStateListOf<GeofenceArea>() }
    var interactionMode by remember { mutableStateOf(MapInteractionMode.NONE) }
    var currentPin by remember { mutableStateOf<LatLng?>(null) }
    var radius by remember { mutableFloatStateOf(500f) }

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
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
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
                // Preview circle for the geofence to be added
                Circle(
                    center = it,
                    radius = radius.toDouble(),
                    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    strokeWidth = 2f
                )
            }

            geofences.forEach { zone ->
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
            }
        }

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
                        Toast.makeText(context, "Draw your Geofence!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Draw Geofence")
                }

                IconButton(
                    onClick = {
                        currentPin?.let {
                            geofences.add(GeofenceArea(center = it, radius = radius.toDouble()))
                            currentPin = null // Reset after adding
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

        //bar for circle radius adjustment
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
