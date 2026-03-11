package com.example.watchstop.model

data class TrackedPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val speed: Float = 0f
)