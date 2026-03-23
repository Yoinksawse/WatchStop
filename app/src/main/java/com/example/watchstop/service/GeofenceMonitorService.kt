package com.example.watchstop.service

import android.R
import android.annotation.SuppressLint
import android.app.*
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
import com.example.watchstop.activities.MainActivity
import com.example.watchstop.algo.checkPointInCircle
import com.example.watchstop.algo.checkPointInPolygon
import com.example.watchstop.data.FirebaseRepository
import com.example.watchstop.model.GeoAlarm
import com.example.watchstop.model.GeofenceArea
import com.example.watchstop.model.TripStatus
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Foreground service that:
 *  1. Listens to the user's geo alarms in real-time from Firebase.
 *  2. Checks the device location against active alarms on every location update.
 *  3. Pushes the live device location to any groups where the user has sharing enabled.
 *  4. Sends "member arrived" notifications to groups when a member enters a geofence.
 */

class GeofenceMonitorService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val MIN_LOCATION_UPDATE_TIME_INTERVAL: Long = 3_000
    private val NOTIFICATION_ID = 888
    private val CHANNEL_ID = "GeofenceMonitorChannel"
    private val ALARM_CHANNEL_ID = "GeofenceAlarmChannel"
    private val ARRIVAL_NOTIFICATION_CHANNEL_ID = "GeofenceArrivalChannel"

    @Volatile
    private var liveAlarms: List<GeoAlarm> = emptyList()
    private val activeAlarms = mutableSetOf<String>()
    private val recentlyStoppedAlarms = mutableSetOf<String>()


    // Track which members have already triggered arrival notifications per group geofence
    private val arrivedMembers = mutableMapOf<String, MutableSet<String>>() // groupId -> Set<memberId>

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, createPersistentNotification())

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        subscribeToAlarms()
        startLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopMediaPlayerSafely()
        vibrator?.cancel()
        volumeEscalationRunnable?.let { handler.removeCallbacks(it) }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP_ALARM") {
            forceStopAlarm()
        }
        return START_STICKY
    }

    // ── Firebase Alarm Subscription ────────────────────────────���──────────

    private fun subscribeToAlarms() {
        val uid = FirebaseRepository.currentUid ?: return
        serviceScope.launch {
            FirebaseRepository.observeGeoAlarms(uid).collect { alarms ->
                liveAlarms = alarms
                Log.d("GeofenceService", "Alarms updated: ${alarms.size}")
            }
        }
    }

    // ── Location Updates ──────────────────────────────────────────────────

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000)
            .setMinUpdateIntervalMillis(MIN_LOCATION_UPDATE_TIME_INTERVAL)
            .build()

        locationCallback = object : LocationCallback() {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    checkGeofences(location)
                    pushLiveLocationToGroups(location)
                    checkGroupGeofencesAndNotify(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("GeofenceService", "Location permission missing", e)
        }
    }

    // ── Geofence Checking ─────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkGeofences(location: Location) {
        Log.d("GeofenceService", "checkGeofences called. liveAlarms=${liveAlarms.size}, activeAlarms=$activeAlarms")
        if (liveAlarms.isEmpty()) {
            Log.w("GeofenceService", "liveAlarms is empty — alarms not yet loaded from Firebase")
            return
        }

        val userLatLng = LatLng(location.latitude, location.longitude)
        val now = LocalDateTime.now()
        val currentTime = LocalTime.now()

        liveAlarms.forEach { alarm ->
            Log.d("GeofenceService", "Checking alarm: ${alarm.id} name=${alarm.name} active=${alarm.active}")
            if (!alarm.active) {
                Log.d("GeofenceService", "Skipping alarm ${alarm.name} — not active")

                return@forEach
            }
            if (alarm.startTime != null && alarm.endTime != null) {
                if (currentTime.isBefore(alarm.startTime) || currentTime.isAfter(alarm.endTime))
                    return@forEach
            }
            if (alarm.specificDate != null && alarm.specificDate != now.toLocalDate()) return@forEach
            if (alarm.dayOfWeek != null && alarm.dayOfWeek != now.dayOfWeek) return@forEach

            val geofence = alarm.getGeofence()
            if (geofence == null) {
                Log.w("GeofenceService", "Alarm ${alarm.name} has null geofence")
                return@forEach
            }

            val isInside = checkPointInGeofence(userLatLng, geofence)
            Log.d("GeofenceService", "Alarm ${alarm.name}: isInside=$isInside, alreadyActive=${activeAlarms.contains(alarm.id)}")

            if (isInside && !activeAlarms.contains(alarm.id)) {
                if (alarm.id in recentlyStoppedAlarms) {
                    Log.d("GeofenceService", "Skipping alarm ${alarm.name} — recently stopped, waiting for Firebase confirmation")
                    return@forEach
                }
                activeAlarms.add(alarm.id)
                triggerGeoAlarm(alarm)
            } else if (!isInside && activeAlarms.contains(alarm.id)) {
                activeAlarms.remove(alarm.id)
                // Also remove from recently stopped if user has left the geofence
                recentlyStoppedAlarms.remove(alarm.id)
                stopAlarmIfNoneActive()
            }
        }
    }

    // ── Group Geofence Arrival Detection ────────────────────────────────

    /**
     * Check if current user has entered any group geofences and notify group members.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkGroupGeofencesAndNotify(location: Location) {
        val uid = FirebaseRepository.currentUid ?: return
        val userLatLng = LatLng(location.latitude, location.longitude)
        val db = FirebaseDatabase.getInstance().reference

        serviceScope.launch {
            db.child("users").child(uid).child("groups").get()
                //.addOnFailureListener { Log.e("GeofenceService", "Failed to fetch user groups", it) }
                .addOnSuccessListener { userGroupsSnap ->
                    userGroupsSnap.children.forEach { snap ->
                        val groupId = snap.key ?: return@forEach

                        db.child("groups").child(groupId).get()
                            //.addOnFailureListener { Log.e("GeofenceService", "Failed to fetch group $groupId", it) }
                            .addOnSuccessListener { groupSnap ->
                                val geofence = parseGeofenceFromSnapshot(groupSnap) ?: return@addOnSuccessListener
                                val arrivedSet = arrivedMembers.getOrPut(groupId) { mutableSetOf() }
                                val isInside = checkPointInGeofence(userLatLng, geofence)

                                when {
                                    isInside && uid !in arrivedSet -> {
                                        arrivedSet.add(uid)
                                        val groupTitle = groupSnap.child("title").getValue(String::class.java) ?: "Group"
                                        val tripStatus = groupSnap.child("tripStatus").child(uid).getValue(String::class.java)

                                        if (tripStatus == TripStatus.TRAVELLING.name) {
                                            serviceScope.launch {
                                                runCatching { FirebaseRepository.setTripStatus(groupId, uid, TripStatus.ARRIVED) }
                                                    .onFailure { Log.e("GeofenceService", "Failed to update trip status", it) }
                                            }
                                        }

                                        notifyUserOfArrival(groupId, groupTitle, tripStatus)
                                        sendMemberArrivedNotification(groupId, groupTitle, uid)
                                        Log.i("GeofenceService", "User $uid arrived at geofence in group $groupId")
                                    }
                                    !isInside && uid in arrivedSet -> arrivedSet.remove(uid)
                                }
                            }
                    }
                }
        }
    }

    private fun parseGeofenceFromSnapshot(groupSnap: com.google.firebase.database.DataSnapshot): GeofenceArea? {
        val snap = groupSnap.child("geofence").takeIf { it.exists() } ?: return null
        val id = snap.child("id").getValue(String::class.java) ?: return null
        return try {
            GeofenceArea(
                id = id,
                name = snap.child("name").getValue(String::class.java) ?: "",
                center = LatLng(
                    snap.child("center").child("lat").getValue(Double::class.java) ?: 0.0,
                    snap.child("center").child("lng").getValue(Double::class.java) ?: 0.0
                ),
                typeId = snap.child("typeId").getValue(Int::class.java) ?: 0,
                radius = snap.child("radius").getValue(Double::class.java) ?: 0.0,
                points = snap.child("points").children.mapNotNull { pt ->
                    val lat = pt.child("lat").getValue(Double::class.java)
                    val lng = pt.child("lng").getValue(Double::class.java)
                    if (lat != null && lng != null) LatLng(lat, lng) else null
                }
            )
        } catch (e: Exception) {
            Log.e("GeofenceService", "Failed to parse geofence in group ${groupSnap.key}", e)
            null
        }
    }
    private fun sendMemberArrivedNotification(groupId: String, groupTitle: String, memberId: String) {
        serviceScope.launch {
            try {
                val memberName = FirebaseRepository.getUsername(memberId)
                val notificationId = "arrival_${groupId}_${memberId}_${System.currentTimeMillis()}"

                // Save notification to Firebase for all group members to see
                val notificationData = mapOf(
                    "type" to "memberArrived",
                    "groupId" to groupId,
                    "groupTitle" to groupTitle,
                    "memberUid" to memberId,
                    "memberName" to memberName,
                    "notificationId" to notificationId,
                    "timestamp" to System.currentTimeMillis()
                )

                FirebaseDatabase.getInstance().reference
                    .child("notifications").child(groupId).child(notificationId)
                    .setValue(notificationData)
                    .addOnSuccessListener {
                        Log.d("GeofenceService", "Member arrival notification saved to Firebase")
                    }
                    .addOnFailureListener { e ->
                        Log.e("GeofenceService", "Failed to save arrival notification: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e("GeofenceService", "Error sending arrival notification", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun notifyUserOfArrival(groupId: String, groupTitle: String, tripStatus: String?) {
        // Only show notification if user is on an active trip (TRAVELLING)
        if (tripStatus == TripStatus.TRAVELLING.name) {
            // Create and show a local notification to the user
            val manager = getSystemService(NotificationManager::class.java)
            val notification = NotificationCompat.Builder(this, ARRIVAL_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Arrival")
                .setContentText("You arrived at Geofence of group $groupTitle")
                .setSmallIcon(com.example.watchstop.R.drawable.ic_notification_arrival)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            manager?.notify("arrival_$groupId".hashCode(), notification)

            // Optional: Add a brief vibration to make it more noticeable
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
            }

            Log.d("GeofenceService", "Notified user of arrival at group $groupTitle (trip active)")
        } else {
            Log.d("GeofenceService", "Skipped arrival notification for group $groupTitle (trip inactive)")
        }
    }

    // ── Live Location Push to Groups ──────────────────────────────────────

    private fun pushLiveLocationToGroups(location: Location) {
        val uid = FirebaseRepository.currentUid ?: return
        val db = FirebaseDatabase.getInstance().reference

        serviceScope.launch {
            db.child("users").child(uid).child("groups").get()
                .addOnSuccessListener { userGroupsSnap ->
                    userGroupsSnap.children.mapNotNull { it.key }.forEach { groupId ->
                        pushLocationToGroup(db, groupId, uid, location)
                    }
                }
        }
    }

    private fun pushLocationToGroup(
        db: com.google.firebase.database.DatabaseReference,
        groupId: String,
        uid: String,
        location: Location
    ) {
        db.child("groups").child(groupId).child("locationSharingEnabled").child(uid).get()
            .addOnSuccessListener { snap ->
                if (snap.getValue(Boolean::class.java) == true) {
                    FirebaseRepository.pushLocation(groupId, uid, location.latitude, location.longitude)
                }
            }
    }

    // ── Alarm Audio / Vibration ───────────────────────────────────────────

    private fun triggerGeoAlarm(alarm: GeoAlarm) {
        // Always send individual notification for this alarm
        sendGeoAlarmNotification(alarm)

        // Start audio/vibration if not already playing
        if (mediaPlayer == null) {
            startAlarmAudioAndVibration()
        }

        // Update or show summary notification for multiple alarms
        if (activeAlarms.size > 1) {
            showMultipleAlarmsSummary()
        }
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun sendGeoAlarmNotification(alarm: GeoAlarm) {
        val manager = getSystemService(NotificationManager::class.java)

        val stopIntent = Intent(this, StopAlarmReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            alarm.id.hashCode(), // Use alarm ID as request code to differentiate multiple alarms
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle("ALARM: ${alarm.name}")
            .setContentText("You have entered the geofence area. Tap to stop all active alarms.")
            .setSmallIcon(R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_lock_idle_alarm,
                "Stop All Alarms",
                stopPendingIntent
            )
            .setContentIntent(stopPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        // Use unique notification ID for each alarm
        manager?.notify("alarm_${alarm.id}".hashCode(), notification)
    }

    private fun showMultipleAlarmsSummary() {
        val manager = getSystemService(NotificationManager::class.java)
        val count = activeAlarms.size

        // Create a list of active alarm names for the inbox style
        val activeAlarmNames = activeAlarms.mapNotNull { alarmId ->
            liveAlarms.find { it.id == alarmId }?.name
        }.take(5) // Limit to 5 to avoid overcrowding

        val stopIntent = Intent(this, StopAlarmReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            999, // Fixed ID for summary notification
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use InboxStyle to show multiple lines
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("$count Active Alarms")
            .setSummaryText("Tap to stop all")

        activeAlarmNames.forEach { alarmName ->
            inboxStyle.addLine("• $alarmName")
        }

        if (activeAlarmNames.size < count) {
            inboxStyle.addLine("• and ${count - activeAlarmNames.size} more...")
        }

        val summaryNotification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle("$count Active Alarms")
            .setContentText("Multiple geofence alarms are triggered")
            .setSmallIcon(R.drawable.ic_lock_idle_alarm) // Fix: Use your icon
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(
                R.drawable.ic_lock_idle_alarm,
                "Stop All",
                stopPendingIntent
            )
            .setContentIntent(stopPendingIntent)
            .setOngoing(true)
            .setAutoCancel(true)
            .build()

        // Use a fixed ID for the summary notification
        manager?.notify("multiple_alarms_summary".hashCode(), summaryNotification)
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
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (maxVolume * 0.2).toInt(), 0)
            volumeEscalationRunnable = object : Runnable {
                var currentVolume = 0.2
                override fun run() {
                    if (currentVolume < 1.0) {
                        currentVolume += 0.1
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_ALARM,
                            (maxVolume * currentVolume).toInt(), 0)
                        handler.postDelayed(this, 3000)
                    }
                }
            }
            handler.postDelayed(volumeEscalationRunnable!!, 3000)
        }
        val pattern = longArrayOf(0, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarmIfNoneActive() {
        if (activeAlarms.isEmpty()) {
            stopMediaPlayerSafely()

            vibrator?.cancel()
            volumeEscalationRunnable?.let { handler.removeCallbacks(it) }

            // Cancel all notifications
            val manager = getSystemService(NotificationManager::class.java)
            manager?.cancel("multiple_alarms_summary".hashCode())
            // Individual alarm notifications will auto-cancel when clicked

            Log.d("GeofenceService", "All alarms stopped - no active alarms")
        } else {
            // Update the summary notification with current count
            showMultipleAlarmsSummary()
        }
    }

    private fun forceStopAlarm() {
        stopMediaPlayerSafely()
        vibrator?.cancel()
        volumeEscalationRunnable?.let { handler.removeCallbacks(it) }
        volumeEscalationRunnable = null

        val alarmsToDeactivate = activeAlarms.toList()

        // Track recently stopped alarms to prevent re-triggering
        recentlyStoppedAlarms.addAll(alarmsToDeactivate)

        activeAlarms.clear()

        val manager = getSystemService(NotificationManager::class.java)

        if (alarmsToDeactivate.isEmpty()) {
            manager?.cancelAll()
            return
        }

        alarmsToDeactivate.forEach { alarmId ->
            manager?.cancel("alarm_$alarmId".hashCode())
        }
        manager?.cancel("multiple_alarms_summary".hashCode())

        liveAlarms = liveAlarms.map { alarm ->
            if (alarm.id in alarmsToDeactivate) alarm.copy(active = false)
            else alarm
        }

        val uid = FirebaseRepository.currentUid ?: return
        FirebaseRepository.deactivateGeoAlarms(
            database = FirebaseDatabase.getInstance().reference,
            uid = uid,
            alarmIds = alarmsToDeactivate,
            onComplete = { success, error ->
                if (success) {
                    // Only clear the cooldown once Firebase confirms deactivation
                    recentlyStoppedAlarms.removeAll(alarmsToDeactivate.toSet())
                } else {
                    Log.e("GeofenceService", "Failed to deactivate alarms in Firebase: $error")
                }
            }
        )
    }

    private fun stopMediaPlayerSafely() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        } catch (e: IllegalStateException) {
            Log.e("GeofenceService", "MediaPlayer in bad state during stop", e)
        } finally {
            mediaPlayer = null
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitorChannel = NotificationChannel(
                CHANNEL_ID, "Geofence Monitor", NotificationManager.IMPORTANCE_LOW)
            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID, "Geofence Alarms", NotificationManager.IMPORTANCE_HIGH
            ).apply { setSound(null, null); enableVibration(true) }
            val arrivalChannel = NotificationChannel(
                ARRIVAL_NOTIFICATION_CHANNEL_ID, "Member Arrivals", NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(monitorChannel)
            manager?.createNotificationChannel(alarmChannel)
            manager?.createNotificationChannel(arrivalChannel)
        }
    }

    private fun createPersistentNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WatchStop Monitor Active")
            .setContentText("Monitoring geofences in the background...")
            .setSmallIcon(R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        //put here so other logic can also use
        fun checkPointInGeofence(point: LatLng, geofence: GeofenceArea): Boolean {
            return if (geofence.typeId == 1) checkPointInCircle(point, geofence)
            else checkPointInPolygon(point, geofence.points)
        }
    }
}