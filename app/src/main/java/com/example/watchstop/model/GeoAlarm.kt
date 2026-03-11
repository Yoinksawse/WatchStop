package com.example.watchstop.model

import com.example.watchstop.data.UserGeofencesDatabase
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.util.UUID

data class GeoAlarm (
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var active: Boolean = true,
    var description: String = "Add Description",
    var geofenceId: String? = null,
    
    var specificDate: LocalDate? = null,
    var dayOfWeek: DayOfWeek? = null,
    var startTime: LocalTime? = null,
    var endTime: LocalTime? = null
) {
    fun getGeofence(): GeofenceArea? {
        return UserGeofencesDatabase.getGeofenceInstance(geofenceId)
    }
}
