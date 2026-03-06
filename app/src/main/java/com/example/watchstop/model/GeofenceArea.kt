package com.example.watchstop.model

import com.google.android.gms.maps.model.LatLng
import java.util.UUID

data class GeofenceArea (
    val id: String = UUID.randomUUID().toString(),
    val center: LatLng,
    val radius: Double
)