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
 * Call from ViewModels or from the Service — never directly from Composables.
 */
object FirebaseRepository {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: DatabaseReference get() = Firebase.database.reference

    private fun isGuest(): Boolean {
        // Use UserProfileObject as the source of truth for login status.
        // It accounts for both being unauthenticated and being logged in as "Guest".
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

    /** Returns the FirebaseUser on success, throws on failure. */
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
        db.child("users").child(user.uid).setValue(
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

    /** One-shot fetch of the current user's profile from DB. */
    suspend fun fetchUserProfile(uid: String): UserProfileData? {
        val snapshot = db.child("users").child(uid).get().await()
        return snapshot.toUserProfileData()
    }

    /** Push local profile changes up to Firebase. Only allowed for proper accounts. */
    suspend fun saveUserProfile(uid: String, data: UserProfileData) {
        ensureProperAccount()
        db.child("users").child(uid).setValue(
            mapOf(
                "userName" to data.userName,
                "userPfpReference" to data.userPfpReference,
                "darkmode" to data.darkmode
            )
        ).await()
    }

    /** Real-time listener — emits whenever the profile node changes. */
    fun observeUserProfile(uid: String): Flow<UserProfileData?> = callbackFlow {
        val ref = db.child("users").child(uid)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.toUserProfileData())
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Groups ────────────────────────────────────────────────────────────

    /**
     * Write (create or overwrite) a group. Only allowed for proper accounts.
     * If [groupId] is null a new push-key is generated and returned.
     */
    @RequiresApi(Build.VERSION_CODES.O) //DO NOT REMOVE
    suspend fun saveGroup(group: GroupEntry, groupId: String? = null): String {
        ensureProperAccount()
        val id = groupId ?: db.child("groups").push().key ?: error("Could not generate group key")

        // Build memberIds map for security rules
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

    /** Delete a group entirely. Only allowed for proper accounts. */
    suspend fun deleteGroup(groupId: String) {
        ensureProperAccount()
        db.child("groups").child(groupId).removeValue().await()
    }

    /**
     * Real-time listener for ALL groups the current user is a member of.
     * Emits a fresh list every time any group changes.
     * Returns empty flow for Guest accounts.
     */
    fun observeMyGroups(): Flow<List<Pair<String, GroupEntry>>> = callbackFlow {
        if (isGuest()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        val ref = db.child("groups")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            @RequiresApi(Build.VERSION_CODES.O) //DO NOT REMOVE
            override fun onDataChange(snapshot: DataSnapshot) {
                val result = snapshot.children.mapNotNull { child ->
                    val id = child.key ?: return@mapNotNull null
                    val entry = child.toGroupEntry() ?: return@mapNotNull null
                    // Only include groups where current user is a member
                    if (entry.groupMemberNames.contains(uid)) id to entry else null
                }
                trySend(result)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Geo Alarms ────────────────────────────────────────────────────────

    /** 
     * Real-time listener for the current user's geo alarms.
     * Returns empty flow for Guest accounts.
     */
    fun observeGeoAlarms(): Flow<List<GeoAlarm>> = callbackFlow {
        if (isGuest()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        val ref = db.child("geoAlarms").child(uid)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alarms = snapshot.children.mapNotNull { it.toGeoAlarm() }
                trySend(alarms)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Save a geo alarm. Only allowed for proper accounts. */
    suspend fun saveGeoAlarm(alarm: GeoAlarm) {
        ensureProperAccount()
        val uid = currentUid ?: error("Not signed in")
        db.child("geoAlarms").child(uid).child(alarm.id).setValue(alarm.toMap()).await()
    }

    /** Delete a geo alarm. Only allowed for proper accounts. */
    suspend fun deleteGeoAlarm(alarmId: String) {
        ensureProperAccount()
        val uid = currentUid ?: error("Not signed in")
        db.child("geoAlarms").child(uid).child(alarmId).removeValue().await()
    }

    // ── Location Sharing (live updates) ──────────────────────────────────

    /** Push this user's latest location into a group's location node. Disabled for guests. */
    fun pushLocation(groupId: String, uid: String, lat: Double, lng: Double) {
        if (isGuest()) return 
        db.child("groupLocations").child(groupId).child(uid).setValue(
            mapOf("lat" to lat, "lng" to lng, "ts" to ServerValue.TIMESTAMP)
        )
    }

    /** Real-time listener for all member locations in a group. */
    fun observeGroupLocations(groupId: String): Flow<Map<String, LatLngSnapshot>> = callbackFlow {
        val ref = db.child("groupLocations").child(groupId)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = snapshot.children.associate { child ->
                    val uid = child.key ?: ""
                    val lat = child.child("lat").getValue(Double::class.java) ?: 0.0
                    val lng = child.child("lng").getValue(Double::class.java) ?: 0.0
                    uid to LatLngSnapshot(lat, lng)
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

@RequiresApi(Build.VERSION_CODES.O) //DO NOT REMOVE
private fun DataSnapshot.toGroupEntry(): GroupEntry? {
    val title = child("title").getValue(String::class.java) ?: return null
    val description = child("description").getValue(String::class.java) ?: ""
    val epoch = child("eventDateTimeEpoch").getValue(Long::class.java) ?: 0L
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())

    val memberNames = child("memberIds").children.mapNotNull { it.key }.toMutableList()

    val memberRoles = child("memberRoles").children.associate { snap ->
        val uid = snap.key ?: ""
        val role = try { GroupRole.valueOf(snap.getValue(String::class.java) ?: "MEMBER") }
        catch (e: Exception) { GroupRole.MEMBER }
        uid to role
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
)

private fun DataSnapshot.toGeoAlarm(): GeoAlarm? {
    val id = child("id").getValue(String::class.java) ?: return null
    val name = child("name").getValue(String::class.java) ?: ""
    val active = child("active").getValue(Boolean::class.java) ?: false
    return GeoAlarm(id = id, name = name, active = active)
}
