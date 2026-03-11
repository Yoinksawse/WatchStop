package com.example.watchstop.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.watchstop.R
import com.example.watchstop.activities.MainActivity
import com.example.watchstop.data.GeoAlarmsDatabase
import com.example.watchstop.model.GeoAlarm
import com.example.watchstop.model.GeofenceArea
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import java.time.LocalDateTime
import java.time.LocalTime

class GeofenceMonitorService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private val NOTIFICATION_ID = 888
    private val CHANNEL_ID = "GeofenceMonitorChannel"
    private val ALARM_CHANNEL_ID = "GeofenceAlarmChannel"

    private var activeAlarms = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        createNotificationChannels()
        startForeground(NOTIFICATION_ID, createPersistentNotification())
        startLocationUpdates()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitorChannel = NotificationChannel(
                CHANNEL_ID,
                "Geofence Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Geofence Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(monitorChannel)
            manager?.createNotificationChannel(alarmChannel)
        }
    }

    private fun createPersistentNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WatchStop Monitor Active")
            .setContentText("Monitoring geofences in the background...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        locationCallback = object : LocationCallback() {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { checkGeofences(it) }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("GeofenceService", "Location permission missing", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkGeofences(location: Location) {
        val userLatLng = LatLng(location.latitude, location.longitude)
        val now = LocalDateTime.now()
        val currentTime = LocalTime.now()

        GeoAlarmsDatabase.alarms.forEach { alarm ->
            if (!alarm.active) return@forEach

            // Check Time Window
            if (alarm.startTime != null && alarm.endTime != null) {
                if (currentTime.isBefore(alarm.startTime) || currentTime.isAfter(alarm.endTime)) return@forEach
            }
            
            // Check Day/Date
            if (alarm.specificDate != null && alarm.specificDate != now.toLocalDate()) return@forEach
            if (alarm.dayOfWeek != null && alarm.dayOfWeek != now.dayOfWeek) return@forEach

            val geofence = alarm.getGeofence() ?: return@forEach
            val isInside = isPointInGeofence(userLatLng, geofence)

            if (isInside && !activeAlarms.contains(alarm.id)) {
                activeAlarms.add(alarm.id)
                triggerAlarm(alarm)
            } else if (!isInside && activeAlarms.contains(alarm.id)) {
                activeAlarms.remove(alarm.id)
                stopAlarmIfNoneActive()
            }
        }
    }

    private fun isPointInGeofence(point: LatLng, geofence: GeofenceArea): Boolean {
        return if (geofence.typeId == 1) { // Circle
            val results = FloatArray(1)
            Location.distanceBetween(
                geofence.center.latitude, geofence.center.longitude,
                point.latitude, point.longitude, results
            )
            results[0] <= geofence.radius
        } else { // Polygon
            isPointInPolygon(point, geofence.points)
        }
    }

    private fun isPointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
        if (polygon.size < 3) return false
        var intersectCount = 0
        for (i in polygon.indices) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % polygon.size]
            if (((p1.latitude > point.latitude) != (p2.latitude > point.latitude)) &&
                (point.longitude < (p2.longitude - p1.longitude) * (point.latitude - p1.latitude) / (p2.latitude - p1.latitude) + p1.longitude)
            ) {
                intersectCount++
            }
        }
        return intersectCount % 2 != 0
    }

    private fun triggerAlarm(alarm: GeoAlarm) {
        sendAlarmNotification(alarm)
        startAlarmAudioAndVibration()
    }

    private fun sendAlarmNotification(alarm: GeoAlarm) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle("ALARM: ${alarm.name}")
            .setContentText("You have entered the geofence area.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        manager?.notify(alarm.id.hashCode(), notification)
    }

    private var volumeEscalationRunnable: Runnable? = null

    private fun startAlarmAudioAndVibration() {
        if (mediaPlayer == null) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@GeofenceMonitorService, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            // Volume Escalation
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (maxVolume * 0.2).toInt(), 0)

            volumeEscalationRunnable = object : Runnable {
                var currentVolume = 0.2
                override fun run() {
                    if (currentVolume < 1.0) {
                        currentVolume += 0.1
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (maxVolume * currentVolume).toInt(), 0)
                        handler.postDelayed(this, 3000)
                    }
                }
            }
            handler.postDelayed(volumeEscalationRunnable!!, 3000)
        }

        val vibrationPattern = longArrayOf(0, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(vibrationPattern, 0)
        }
    }

    private fun stopAlarmIfNoneActive() {
        if (activeAlarms.isEmpty()) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
            volumeEscalationRunnable?.let { handler.removeCallbacks(it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        mediaPlayer?.release()
        vibrator?.cancel()
        volumeEscalationRunnable?.let { handler.removeCallbacks(it) }
    }
}
