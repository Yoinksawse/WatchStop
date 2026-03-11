package com.example.watchstop.model

import android.graphics.Color
import com.google.android.gms.maps.model.LatLng

class RouteSegment(val start: LatLng, val end: LatLng, val speed: Double = 0.0) {
    val red = (speed * 255 / 30).toInt()
    val green = 0
    val blue = 255 - red

    val segmentColor: Int = Color.rgb(red, blue, green)
}
