package com.example.watchstop.data

import com.example.watchstop.model.GeoAlarm
import androidx.compose.runtime.mutableStateListOf

object GeoAlarmsDatabase {
    val alarms = mutableStateListOf<GeoAlarm>()

    fun addAlarm(alarm: GeoAlarm) {
        alarms.add(alarm)
    }

    fun removeAlarm(alarm: GeoAlarm) {
        alarms.removeIf { it.id == alarm.id }
    }

    fun updateAlarm(updatedAlarm: GeoAlarm) {
        val index = alarms.indexOfFirst { it.id == updatedAlarm.id }
        if (index != -1) {
            alarms[index] = updatedAlarm
        }
    }
}
