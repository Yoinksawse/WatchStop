package com.example.watchstop.algo

import android.location.Location
import androidx.compose.ui.geometry.Offset
import com.example.watchstop.model.GeofenceArea
import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.sqrt

// Check for self-intersection of every 2 polygon lines (adjacent lines skipped)
//  Uses parametric segment-intersection test:
//  - Computes parameters t and u for the two segments.
//  - A proper (non-endpoint) crossing requires 0 < t < 1 AND 0 < u < 1.
//  - Parallel segments (denom == 0) are treated as non-intersecting.
fun polygonSelfIntersects(points: List<Offset>): Boolean {
    val n = points.size
    for (i in 0 until n) {
        val a = points[i]
        val b = points[(i + 1) % n]
        for (j in i + 2 until n) {
            // Skip the edge pair that share the closing vertex (i=0, j=n-1)
            if (i == 0 && j == n - 1) continue
            val c = points[j]
            val d = points[(j + 1) % n]
            if (segmentsIntersect(a, b, c, d)) return true
        }
    }
    return false
}

// Find presence of segment intersection points
fun segmentsIntersect(p1: Offset, p2: Offset, p3: Offset, p4: Offset): Boolean {
    val d1x = p2.x - p1.x
    val d1y = p2.y - p1.y
    val d2x = p4.x - p3.x
    val d2y = p4.y - p3.y

    val denom = d1x * d2y - d1y * d2x
    if (denom == 0f) return false // parallel or collinear treat as no crossing

    val t = ((p3.x - p1.x) * d2y - (p3.y - p1.y) * d2x) / denom
    val u = ((p3.x - p1.x) * d1y - (p3.y - p1.y) * d1x) / denom

    // Inequalities exclude endpoint touches (shared vertices between adjacent edges)
    return t > 0f && t < 1f && u > 0f && u < 1f
}

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

fun checkPointInCircle(point: LatLng, geofence: GeofenceArea): Boolean {
    val results = FloatArray(1)
    Location.distanceBetween(
        geofence.center.latitude, geofence.center.longitude,
        point.latitude, point.longitude, results
    )
    return results[0] <= geofence.radius
}

fun checkPointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
    if (polygon.size < 3) return false
    var intersectCount = 0
    for (i in polygon.indices) {
        val p1 = polygon[i]
        val p2 = polygon[(i + 1) % polygon.size]
        if (((p1.latitude > point.latitude) != (p2.latitude > point.latitude)) &&
            (point.longitude < (p2.longitude - p1.longitude) *
                    (point.latitude - p1.latitude) / (p2.latitude - p1.latitude) + p1.longitude)
        ) intersectCount++
    }
    return intersectCount % 2 != 0
}