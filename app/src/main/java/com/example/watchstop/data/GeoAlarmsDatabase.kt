package com.example.watchstop.data

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import com.example.watchstop.model.GeoAlarm
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object GeoAlarmsDatabase {
    private val database = FirebaseDatabase.getInstance("https://watchstopdb-default-rtdb.firebaseio.com")
    val alarms = mutableStateListOf<GeoAlarm>()

    fun addAlarm(alarm: GeoAlarm) {
        alarms.add(alarm)
        updateAlarmsToFirebaseDB()
    }

    fun removeAlarm(alarm: GeoAlarm) {
        if (alarms.removeIf { it.id == alarm.id }) {
            updateAlarmsToFirebaseDB()
        }
    }

    fun updateAlarm(updatedAlarm: GeoAlarm) {
        val index = alarms.indexOfFirst { it.id == updatedAlarm.id }
        if (index != -1) {
            alarms[index] = updatedAlarm
            updateAlarmsToFirebaseDB()
        }
    }

    fun fetchAlarmsFromFirebaseDB() {
        val userName = UserProfileObject.userName
        if (userName == GUEST_USERNAME) return

        val ref = database.getReference("users").child(userName).child("alarms")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = mutableListOf<GeoAlarm>()
                for (child in snapshot.children) {
                    try {
                        val alarm = GeoAlarm(
                            id = child.child("id").getValue(String::class.java) ?: "",
                            name = child.child("name").getValue(String::class.java) ?: "",
                            active = child.child("active").getValue(Boolean::class.java) ?: false,
                            description = child.child("description").getValue(String::class.java) ?: "",
                            geofenceId = child.child("geofenceId").getValue(String::class.java),
                            specificDate = child.child("specificDate").getValue(String::class.java)?.let { LocalDate.parse(it) },
                            dayOfWeek = child.child("dayOfWeek").getValue(String::class.java)?.let { java.time.DayOfWeek.valueOf(it) },
                            startTime = child.child("startTime").getValue(String::class.java)?.let { LocalTime.parse(it) },
                            endTime = child.child("endTime").getValue(String::class.java)?.let { LocalTime.parse(it) },
                        )
                        newList.add(alarm)
                    } catch (e: Exception) {
                        Log.e("GeoAlarmsDatabase", "Error parsing alarm data", e)
                    }
                }
                alarms.clear()
                alarms.addAll(newList)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("GeoAlarmsDatabase", "Firebase fetch cancelled: ${error.message}")
            }
        })
    }

    fun updateAlarmsToFirebaseDB() {
        val userName = UserProfileObject.userName
        if (userName == GUEST_USERNAME) return

        val ref = database.getReference("users").child(userName).child("alarms")
        val firebaseData = alarms.map { alarm ->
            mapOf(
                "id" to alarm.id,
                "name" to alarm.name,
                "active" to alarm.active,
                "description" to alarm.description,
                "geofenceId" to alarm.geofenceId,
                "specificDate" to alarm.specificDate?.toString(),
                "dayOfWeek" to alarm.dayOfWeek?.name,
                "startTime" to alarm.startTime?.toString(),
                "endTime" to alarm.endTime?.toString()
            )
        }
        ref.setValue(firebaseData)
    }
}
