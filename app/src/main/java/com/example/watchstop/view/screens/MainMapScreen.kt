package com.example.watchstop.view.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.watchstop.data.UserProfileObject
import com.example.watchstop.view.ui.theme.WatchStopTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.location.LocationServices
import com.google.maps.android.compose.*


@Composable
fun MainMapScreen() {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Check for either Fine or Coarse location
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

    // Request permissions on start if not already granted
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            launcher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // Centering logic when permission is granted
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
            } catch (e: SecurityException) {
                // Ignore if permission is suddenly missing
            }
        }
    }

    WatchStopTheme(darkTheme = UserProfileObject.darkmode) {
        // Always show the map. If no permission, it just shows default location.
        // If coarse or fine is granted, the "my location" dot will appear.
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission)
        )
    }
}
