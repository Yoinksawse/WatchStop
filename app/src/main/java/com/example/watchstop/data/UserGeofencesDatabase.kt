package com.example.watchstop.data

import com.example.watchstop.model.GeofenceArea

object UserGeofencesDatabase {
    private val geofences = mutableListOf<GeofenceArea>()

    fun addGeofence(geofence: GeofenceArea) {
        geofences.add(geofence)
    }

    fun removeGeofence(geofence: GeofenceArea) {
        geofences.removeIf { it.id == geofence.id }
    }

    fun getAllGeofences(): List<GeofenceArea> {
        return geofences.toList()
    }
}
