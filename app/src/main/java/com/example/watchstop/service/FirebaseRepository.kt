package com.example.watchstop.data

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.watchstop.model.GeoAlarm
import com.example.watchstop.model.GroupEntry
import com.example.watchstop.model.GroupRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Single access point for all Firebase operations.
 * Identification is now primarily by Username.
 */
object FirebaseRepository {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: DatabaseReference get() = Firebase.database.reference

    private fun isGuest(): Boolean {
        return !UserProfileObject.isLoggedIn
    }

    private fun ensureProperAccount() {
        if (isGuest()) {
            throw IllegalStateException("Operation not allowed for Guest accounts. Please log in.")
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────

    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUid: String? get() = auth.currentUser?.uid

    /**
     * Returns the FirebaseUser on success, throws on failure.
     * [identifier] can be an email or a username.
     */
    suspend fun signIn(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user ?: error("Sign-in returned null user")
    }

    /** Creates account and writes initial profile to DB. */
    suspend fun signUp(email: String, password: String, userName: String): FirebaseUser {
        if (userName.equals(GUEST_USERNAME, ignoreCase = true)) {
            throw IllegalArgumentException("Username 'Guest' is reserved and cannot be used.")
        }
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("Registration returned null user")

        // Write mappings
        db.child("usernames").child(userName.lowercase()).setValue(email)
        db.child("uids").child(user.uid).setValue(userName)

        // Write initial profile using Username as the key
        db.child("users").child(userName).setValue(
            mapOf(
                "userName" to userName,
                "userPfpReference" to DEFAULT_PFP,
                "darkmode" to true
            )
        )
        return user
    }

    fun signOut() = auth.signOut()

    // ── User Profile ──────────────────────────────────────────────────────

    /** Fetch profile by username. */
    suspend fun fetchUserProfile(userName: String): UserProfileData? {
        val snapshot = db.child("users").child(userName).get().await()
        return snapshot.toUserProfileData()
    }

    /** Push profile changes by username. */
    suspend fun saveUserProfile(data: UserProfileData) {
        ensureProperAccount()
        db.child("users").child(data.userName).setValue(
            mapOf(
                "userName" to data.userName,
                "userPfpReference" to data.userPfpReference,
                "darkmode" to data.darkmode
            )
        ).await()
    }

    /** Real-time profile listener by username. */
    fun observeUserProfile(userName: String): Flow<UserProfileData?> = callbackFlow {
        val ref = db.child("users").child(userName)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.toUserProfileData())
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Groups ────────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun saveGroup(group: GroupEntry, groupId: String? = null): String {
        ensureProperAccount()
        val id = groupId ?: db.child("groups").push().key ?: error("Could not generate group key")

        // memberIds now contains usernames
        val memberIds = group.groupMemberNames.associateWith { true }
        val roles = group.memberRoles.mapValues { it.value.name }
        val locationSharing = group.locationSharingEnabled
        val canToggle = group.canToggleSharing
        val applications = group.adminApplications.associateWith { true }
        val removalVotes = group.votesToRemoveAdmin.mapValues { (_, voters) ->
            voters.associateWith { true }
        }

        val payload = mapOf(
            "title" to group.title,
            "description" to group.description,
            "eventDateTimeEpoch" to group.eventDateTime
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            "memberIds" to memberIds,
            "memberRoles" to roles,
            "locationSharingEnabled" to locationSharing,
            "canToggleSharing" to canToggle,
            "adminApplications" to applications,
            "votesToRemoveAdmin" to removalVotes
        )

        db.child("groups").child(id).setValue(payload).await()
        return id
    }

    suspend fun deleteGroup(groupId: String) {
        ensureProperAccount()
        db.child("groups").child(groupId).removeValue().await()
    }

    /**
     * Observe groups where the user is a member.
     * [userName] is the identifier.
     */
    fun observeMyGroups(userName: String): Flow<List<Pair<String, GroupEntry>>> = callbackFlow {
        if (isGuest() || userName == GUEST_USERNAME) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val ref = db.child("groups")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onDataChange(snapshot: DataSnapshot) {
                val result = snapshot.children.mapNotNull { child ->
                    val id = child.key ?: return@mapNotNull null
                    val entry = child.toGroupEntry() ?: return@mapNotNull null
                    if (entry.groupMemberNames.contains(userName)) id to entry else null
                }
                trySend(result)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Geo Alarms ────────────────────────────────────────────────────────

    fun observeGeoAlarms(userName: String): Flow<List<GeoAlarm>> = callbackFlow {
        if (isGuest() || userName == GUEST_USERNAME) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val ref = db.child("geoAlarms").child(userName)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            @RequiresApi(Build.VERSION_CODES.O) //DO NOT REMOVE
            override fun onDataChange(snapshot: DataSnapshot) {
                val alarms = snapshot.children.mapNotNull { it.toGeoAlarm() }
                trySend(alarms)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun saveGeoAlarm(userName: String, alarm: GeoAlarm) {
        ensureProperAccount()
        db.child("geoAlarms").child(userName).child(alarm.id).setValue(alarm.toMap()).await()
    }

    suspend fun deleteGeoAlarm(userName: String, alarmId: String) {
        ensureProperAccount()
        db.child("geoAlarms").child(userName).child(alarmId).removeValue().await()
    }

    // ── User Geofences ───────────────────────────────────────────────────

    fun observeUserGeofences(userName: String): Flow<List<DataSnapshot>> = callbackFlow {
        if (isGuest() || userName == GUEST_USERNAME) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val ref = db.child("geofences").child(userName)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.toList())
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun saveUserGeofences(userName: String, data: Any) {
        ensureProperAccount()
        db.child("geofences").child(userName).setValue(data).await()
    }

    // ── Location Sharing ─────────────────────────────────────────────────

    fun pushLocation(groupId: String, userName: String, lat: Double, lng: Double) {
        if (isGuest() || userName == GUEST_USERNAME) return
        db.child("groupLocations").child(groupId).child(userName).setValue(
            mapOf("lat" to lat, "lng" to lng, "ts" to ServerValue.TIMESTAMP)
        )
    }

    fun observeGroupLocations(groupId: String): Flow<Map<String, LatLngSnapshot>> = callbackFlow {
        val ref = db.child("groupLocations").child(groupId)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = snapshot.children.associate { child ->
                    val name = child.key ?: ""
                    val lat = child.child("lat").getValue(Double::class.java) ?: 0.0
                    val lng = child.child("lng").getValue(Double::class.java) ?: 0.0
                    name to LatLngSnapshot(lat, lng)
                }
                trySend(map)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        })
        awaitClose { ref.removeEventListener(listener) }
    }
}

// ── Snapshot helpers ─────────────────────────────────────────────────────────

data class LatLngSnapshot(val lat: Double, val lng: Double)

private fun DataSnapshot.toUserProfileData(): UserProfileData? {
    val userName = child("userName").getValue(String::class.java) ?: return null
    val pfp = child("userPfpReference").getValue(String::class.java) ?: DEFAULT_PFP
    val dark = child("darkmode").getValue(Boolean::class.java) ?: true
    return UserProfileData(userName = userName, userPfpReference = pfp, darkmode = dark)
}

@RequiresApi(Build.VERSION_CODES.O)
private fun DataSnapshot.toGroupEntry(): GroupEntry? {
    val title = child("title").getValue(String::class.java) ?: return null
    val description = child("description").getValue(String::class.java) ?: ""
    val epoch = child("eventDateTimeEpoch").getValue(Long::class.java) ?: 0L
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())

    val memberNames = child("memberIds").children.mapNotNull { it.key }.toMutableList()

    val memberRoles = child("memberRoles").children.associate { snap ->
        val name = snap.key ?: ""
        val role = try { GroupRole.valueOf(snap.getValue(String::class.java) ?: "MEMBER") }
        catch (e: Exception) { GroupRole.MEMBER }
        name to role
    }.toMutableMap()

    val locationSharing = child("locationSharingEnabled").children.associate { snap ->
        (snap.key ?: "") to (snap.getValue(Boolean::class.java) ?: false)
    }.toMutableMap()

    val canToggle = child("canToggleSharing").children.associate { snap ->
        (snap.key ?: "") to (snap.getValue(Boolean::class.java) ?: false)
    }.toMutableMap()

    val applications = child("adminApplications").children
        .mapNotNull { it.key }.toMutableSet()

    val removalVotes = child("votesToRemoveAdmin").children.associate { targetSnap ->
        val target = targetSnap.key ?: ""
        val voters = targetSnap.children.mapNotNull { it.key }.toMutableSet()
        target to voters
    }.toMutableMap()

    return GroupEntry(
        title = title,
        eventDateTime = dateTime,
        description = description,
        groupMemberNames = memberNames,
        memberRoles = memberRoles,
        locationSharingEnabled = locationSharing,
        canToggleSharing = canToggle,
        adminApplications = applications,
        votesToRemoveAdmin = removalVotes
    )
}

private fun GeoAlarm.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "active" to active,
    "description" to description,
    "geofenceId" to geofenceId,
    "specificDate" to specificDate?.toString(),
    "dayOfWeek" to dayOfWeek?.name,
    "startTime" to startTime?.toString(),
    "endTime" to endTime?.toString()
)

@RequiresApi(Build.VERSION_CODES.O) //DO NOT REMOVE
private fun DataSnapshot.toGeoAlarm(): GeoAlarm? {
    val id = child("id").getValue(String::class.java) ?: return null
    val name = child("name").getValue(String::class.java) ?: ""
    val active = child("active").getValue(Boolean::class.java) ?: false
    return GeoAlarm(
        id = id,
        name = name,
        active = active,
        description = child("description").getValue(String::class.java) ?: "",
        geofenceId = child("geofenceId").getValue(String::class.java),
        specificDate = child("specificDate").getValue(String::class.java)?.let { java.time.LocalDate.parse(it) },
        dayOfWeek = child("dayOfWeek").getValue(String::class.java)?.let { java.time.DayOfWeek.valueOf(it) },
        startTime = child("startTime").getValue(String::class.java)?.let { java.time.LocalTime.parse(it) },
        endTime = child("endTime").getValue(String::class.java)?.let { java.time.LocalTime.parse(it) }
    )
}
