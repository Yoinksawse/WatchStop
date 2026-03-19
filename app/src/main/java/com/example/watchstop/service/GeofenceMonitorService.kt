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

    private var liveAlarms: List<GeoAlarm> = emptyList()
    private val activeAlarms = mutableSetOf<String>()

    // Track which members have already triggered arrival notifications per group geofence
    private val arrivedMembers = mutableMapOf<String, MutableSet<String>>() // groupId -> Set<memberId>

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, createPersistentNotification())
        subscribeToAlarms()
        startLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        mediaPlayer?.release()
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
        val userLatLng = LatLng(location.latitude, location.longitude)
        val now = LocalDateTime.now()
        val currentTime = LocalTime.now()

        liveAlarms.forEach { alarm ->
            if (!alarm.active) return@forEach
            if (alarm.startTime != null && alarm.endTime != null) {
                if (currentTime.isBefore(alarm.startTime) || currentTime.isAfter(alarm.endTime))
                    return@forEach
            }
            if (alarm.specificDate != null && alarm.specificDate != now.toLocalDate()) return@forEach
            if (alarm.dayOfWeek != null && alarm.dayOfWeek != now.dayOfWeek) return@forEach

            val geofence = alarm.getGeofence() ?: return@forEach
            val isInside = isPointInGeofence(userLatLng, geofence)

            if (isInside && !activeAlarms.contains(alarm.id)) {
                activeAlarms.add(alarm.id)
                triggerGeoAlarm(alarm)
            } else if (!isInside && activeAlarms.contains(alarm.id)) {
                activeAlarms.remove(alarm.id)

                // Optionally: You might want to deactivate the alarm when leaving
                // Commented out because you may want alarms to trigger again next time
                // deactivateSingleAlarm(alarm.id)

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
        val uid = FirebaseRepository.currentUid ?: run {
            Log.w("GeofenceService", "checkGroupGeofencesAndNotify skipped: No authenticated user")
            return
        }

        val userLatLng = LatLng(location.latitude, location.longitude)

        serviceScope.launch {
            try {
                // Get user's groups
                FirebaseDatabase.getInstance().reference
                    .child("users").child(uid).child("groups")
                    .get()
                    .addOnSuccessListener { userGroupsSnap ->
                        userGroupsSnap.children.forEach { groupIndexSnap ->
                            val groupId = groupIndexSnap.key ?: return@forEach

                            // Fetch group data to get geofence
                            FirebaseDatabase.getInstance().reference
                                .child("groups").child(groupId)
                                .get()
                                .addOnSuccessListener { groupSnap ->
                                    val geofenceSnap = groupSnap.child("geofence")
                                    if (geofenceSnap.exists()) {
                                        try {
                                            val id = geofenceSnap.child("id").getValue(String::class.java)
                                            val name = geofenceSnap.child("name").getValue(String::class.java) ?: ""
                                            val centerLatValue = geofenceSnap.child("center").child("lat").getValue(Double::class.java) ?: 0.0
                                            val centerLngValue = geofenceSnap.child("center").child("lng").getValue(Double::class.java) ?: 0.0
                                            val typeId = geofenceSnap.child("typeId").getValue(Int::class.java) ?: 0
                                            val radius = geofenceSnap.child("radius").getValue(Double::class.java) ?: 0.0
                                            val points = geofenceSnap.child("points").children.mapNotNull { ptSnap ->
                                                val lat = ptSnap.child("lat").getValue(Double::class.java)
                                                val lng = ptSnap.child("lng").getValue(Double::class.java)
                                                if (lat != null && lng != null) LatLng(lat, lng) else null
                                            }

                                            if (id != null) {
                                                val geofence = GeofenceArea(
                                                    id = id,
                                                    name = name,
                                                    center = LatLng(centerLatValue, centerLngValue),
                                                    typeId = typeId,
                                                    radius = radius,
                                                    points = points
                                                )

                                                // Check if user is inside this geofence
                                                val isInside = isPointInGeofence(userLatLng, geofence)
                                                val arrivedSet = arrivedMembers.getOrPut(groupId) { mutableSetOf() }

                                                if (isInside && !arrivedSet.contains(uid)) {
                                                    // User just arrived at this geofence
                                                    arrivedSet.add(uid)

                                                    // Get current trip status
                                                    val currentTripStatus = groupSnap.child("tripStatus").child(uid).getValue(String::class.java)

                                                    // Check if user is on an active trip
                                                    if (currentTripStatus == TripStatus.TRAVELLING.name) {
                                                        // Auto-update trip status to ARRIVED - now in coroutine scope
                                                        serviceScope.launch {
                                                            try {
                                                                FirebaseRepository.setTripStatus(groupId, uid, TripStatus.ARRIVED)
                                                                Log.i("GeofenceService", "Auto-updated trip status to ARRIVED for user $uid in group $groupId")
                                                            } catch (e: Exception) {
                                                                Log.e("GeofenceService", "Failed to update trip status: ${e.message}")
                                                            }
                                                        }
                                                    }

                                                    // --- UPDATED: Show local notification to the user (only if trip is active) ---
                                                    notifyUserOfArrival(groupId, groupSnap.child("title").getValue(String::class.java) ?: "Group", currentTripStatus)

                                                    // Send notification to other group members
                                                    sendMemberArrivedNotification(groupId, groupSnap.child("title").getValue(String::class.java) ?: "Group", uid)
                                                    Log.i("GeofenceService", "✓ Member $uid arrived at geofence in group $groupId")
                                                } else if (!isInside && arrivedSet.contains(uid)) {
                                                    // User left the geofence
                                                    arrivedSet.remove(uid)
                                                    Log.d("GeofenceService", "Member $uid left geofence in group $groupId")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("GeofenceService", "Error parsing geofence: ${e.message}")
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.w("GeofenceService", "Failed to fetch group $groupId: ${e.message}")
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w("GeofenceService", "Failed to fetch user groups: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e("GeofenceService", "checkGroupGeofencesAndNotify error", e)
            }
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
        val uid = FirebaseRepository.currentUid ?: run {
            Log.w("GeofenceService", "pushLiveLocation skipped: No authenticated user")
            return
        }

        Log.d("GeofenceService", "pushLiveLocation: Starting for uid=$uid, lat=${location.latitude}, lng=${location.longitude}")

        serviceScope.launch {
            try {
                FirebaseDatabase.getInstance().reference
                    .child("users").child(uid).child("groups")
                    .get()
                    .addOnSuccessListener { userGroupsSnap ->
                        val groupCount = userGroupsSnap.childrenCount
                        Log.d("GeofenceService", "pushLiveLocation: Found $groupCount groups for user")

                        if (groupCount == 0L) {
                            Log.d("GeofenceService", "pushLiveLocation: User has no groups, skipping")
                            return@addOnSuccessListener
                        }

                        userGroupsSnap.children.forEach { groupIndexSnap ->
                            val groupId = groupIndexSnap.key ?: return@forEach

                            FirebaseDatabase.getInstance().reference
                                .child("groups").child(groupId)
                                .get()
                                .addOnSuccessListener { groupSnap ->
                                    val isSharing = groupSnap
                                        .child("locationSharingEnabled")
                                        .child(uid)
                                        .getValue(Boolean::class.java) ?: false

                                    Log.d("GeofenceService", "pushLiveLocation: Group $groupId - sharing=$isSharing")

                                    if (isSharing) {
                                        FirebaseRepository.pushLocation(
                                            groupId, uid,
                                            location.latitude, location.longitude
                                        )
                                        Log.i("GeofenceService", "✓ Location pushed to group $groupId: (${location.latitude}, ${location.longitude})")
                                    } else {
                                        Log.d("GeofenceService", "pushLiveLocation: Sharing disabled for group $groupId, skipping")
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.w("GeofenceService", "pushLiveLocation: Failed to read group $groupId: ${e.message}")
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w("GeofenceService", "pushLiveLocation: Failed to read user groups: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e("GeofenceService", "pushLiveLocationToGroups error", e)
            }
        }
    }

    // ── Geofence Math ─────────────────────────────────────────────────────

    private fun isPointInGeofence(point: LatLng, geofence: GeofenceArea): Boolean {
        return if (geofence.typeId == 1) {
            val results = FloatArray(1)
            Location.distanceBetween(
                geofence.center.latitude, geofence.center.longitude,
                point.latitude, point.longitude, results
            )
            results[0] <= geofence.radius
        } else {
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
                (point.longitude < (p2.longitude - p1.longitude) *
                        (point.latitude - p1.latitude) /
                        (p2.latitude - p1.latitude) + p1.longitude)
            ) intersectCount++
        }
        return intersectCount % 2 != 0
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

    private fun updateNotificationForMultipleAlarms() {
        val manager = getSystemService(NotificationManager::class.java)
        val count = activeAlarms.size

        if (count > 1) {
            val stopIntent = Intent(this, StopAlarmReceiver::class.java)
            val stopPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
                .setContentTitle("Multiple Alarms Active")
                .setContentText("$count geofence alarms are currently triggered")
                .setSmallIcon(R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .addAction(R.drawable.ic_media_pause, "Stop All", stopPendingIntent)
                .setContentIntent(stopPendingIntent)
                .setOngoing(true)
                .build()

            manager?.notify("multiple_alarms".hashCode(), notification)
        }
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
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
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
        // Stop audio and vibration
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        volumeEscalationRunnable?.let { handler.removeCallbacks(it) }

        // Get the current active alarms before clearing
        val alarmsToDeactivate = activeAlarms.toList()

        // Deactivate them in Firebase if user is logged in
        val uid = FirebaseRepository.currentUid
        if (uid != null && alarmsToDeactivate.isNotEmpty()) {
            FirebaseRepository.deactivateGeoAlarms(
                database = FirebaseDatabase.getInstance().reference,
                uid = uid,
                alarmIds = alarmsToDeactivate,
                onComplete = { success, error ->
                    if (success) {
                        Log.d("GeofenceService", "Successfully deactivated ${alarmsToDeactivate.size} alarms")

                        // Update local liveAlarms list
                        serviceScope.launch {
                            liveAlarms = liveAlarms.map { alarm ->
                                if (alarmsToDeactivate.contains(alarm.id)) {
                                    alarm.copy(active = false)
                                } else {
                                    alarm
                                }
                            }
                        }

                        // Cancel all notifications
                        val manager = getSystemService(NotificationManager::class.java)
                        alarmsToDeactivate.forEach { alarmId ->
                            manager?.cancel("alarm_$alarmId".hashCode())
                        }
                        manager?.cancel("multiple_alarms_summary".hashCode())

                    } else {
                        Log.e("GeofenceService", "Failed to deactivate alarms: $error")
                    }
                }
            )
        } else {
            // Just cancel notifications if no Firebase update needed
            val manager = getSystemService(NotificationManager::class.java)
            activeAlarms.forEach { alarmId ->
                manager?.cancel("alarm_$alarmId".hashCode())
            }
            manager?.cancel("multiple_alarms_summary".hashCode())
        }

        // Clear active alarms set
        activeAlarms.clear()
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
}