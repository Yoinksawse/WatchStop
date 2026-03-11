package com.example.watchstop.model

import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.LatLng

data class ColouredSegment(
    val p0: LatLng,
    val p1: LatLng,
    val color: Color,
    val isRouteStart: Boolean,
    val isRouteEnd: Boolean
)
