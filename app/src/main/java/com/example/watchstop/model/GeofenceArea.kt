package com.example.watchstop.model

import com.google.android.gms.maps.model.LatLng
import java.util.UUID

data class GeofenceArea (
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val center: LatLng,
    val typeId: Int, //1 for circle, 2 for polygon
    val radius: Double,
    val points: List<LatLng> = emptyList()
)