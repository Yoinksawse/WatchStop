package com.example.watchstop.data

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.example.watchstop.model.GeofenceArea
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object UserGeofencesDatabase {
    private val database = FirebaseDatabase.getInstance("https://watchstopdb-default-rtdb.firebaseio.com")
    private val geofences = mutableStateListOf<GeofenceArea>()

    fun addGeofence(geofence: GeofenceArea) {
        if (!geofences.any { it.id == geofence.id }) {
            geofences.add(geofence)
            updateGeofencesToFirebaseDB()
        }
    }

    fun removeGeofence(geofence: GeofenceArea) {
        if (geofences.removeIf { it.id == geofence.id }) {
            updateGeofencesToFirebaseDB()
        }
    }

    fun getAllGeofences(): List<GeofenceArea> {
        return geofences.toList()
    }

    fun getGeofenceInstance(geofenceId: String?): GeofenceArea? {
        if (geofenceId == null) return null
        return geofences.find { it.id == geofenceId }
    }


    /**
     * Fetches geofences from Firebase for the current user.
     * This should be called after a successful login or app startup.
     */
    fun fetchGeofencesFromFirebaseDB() {
        val uid = UserProfileObject.uid ?: return
        if (UserProfileObject.userName == GUEST_USERNAME) return

        val ref = database.getReference("geofences").child(uid)
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = mutableListOf<GeofenceArea>()
                for (child in snapshot.children) {
                    try {
                        val id = child.child("id").getValue(String::class.java) ?: ""
                        val name = child.child("name").getValue(String::class.java) ?: ""
                        val typeId = child.child("typeId").getValue(Int::class.java) ?: 1
                        val radius = child.child("radius").getValue(Double::class.java) ?: 0.0
                        
                        val centerLat = child.child("center").child("lat").getValue(Double::class.java) ?: 0.0
                        val centerLng = child.child("center").child("lng").getValue(Double::class.java) ?: 0.0
                        val center = LatLng(centerLat, centerLng)
                        
                        val pointsList = mutableListOf<LatLng>()
                        val pointsNode = child.child("points")
                        for (pointChild in pointsNode.children) {
                            val pLat = pointChild.child("lat").getValue(Double::class.java) ?: 0.0
                            val pLng = pointChild.child("lng").getValue(Double::class.java) ?: 0.0
                            pointsList.add(LatLng(pLat, pLng))
                        }
                        
                        val geoAlarmId = child.child("geoAlarmId").getValue(String::class.java)
                        
                        newList.add(GeofenceArea(id, name, center, typeId, radius, pointsList, geoAlarmId))
                    } catch (e: Exception) {
                        Log.e("UserGeofencesDatabase", "Error parsing geofence data", e)
                    }
                }
                geofences.clear()
                geofences.addAll(newList)
                Log.d("UserGeofencesDatabase", "Successfully fetched ${geofences.size} geofences from Firebase")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("UserGeofencesDatabase", "Firebase fetch cancelled: ${error.message}")
            }
        })
    }

    fun updateGeofencesToFirebaseDB() {
        val uid = UserProfileObject.uid ?: return
        if (UserProfileObject.userName == GUEST_USERNAME) {
            Log.d("UserGeofencesDatabase", "Skipping Firebase update for Guest account")
            return
        }
        
        Log.d("UserGeofencesDatabase", "Attempting to update Firebase for user: $uid")

        val ref = database.getReference("geofences").child(uid)

        // Convert geofences to a Firebase-friendly format
        val firebaseData = geofences.map { area ->
            mapOf(
                "id" to area.id,
                "name" to area.name,
                "center" to mapOf("lat" to area.center.latitude, "lng" to area.center.longitude),
                "typeId" to area.typeId,
                "radius" to area.radius,
                "points" to area.points.map { mapOf("lat" to it.latitude, "lng" to it.longitude) },
                "geoAlarmId" to area.geoAlarmId
            )
        }

        ref.setValue(firebaseData)
            .addOnSuccessListener {
                Log.d("UserGeofencesDatabase", "Successfully updated geofences in Firebase")
            }
            .addOnFailureListener { e ->
                Log.e("UserGeofencesDatabase", "Failed to update geofences in Firebase. " +
                        "Check your Database Rules! They might be denying writes.", e)
            }
    }
}
