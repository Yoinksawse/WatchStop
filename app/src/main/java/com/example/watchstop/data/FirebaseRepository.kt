package com.example.watchstop.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.watchstop.model.GeoAlarm
import com.example.watchstop.model.GeofenceArea
import com.example.watchstop.model.GroupEntry
import com.example.watchstop.model.GroupRole
import com.example.watchstop.model.TripStatus
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
import android.widget.Toast
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime


object FirebaseRepository {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: DatabaseReference get() = Firebase.database.reference

    val currentUid: String? get() = auth.currentUser?.uid

    private fun isGuest(): Boolean = !UserProfileObject.isLoggedIn

    private fun ensureAuth(): String =
        currentUid ?: throw IllegalStateException("Not authenticated")

    // ── Auth ──────────────────────────────────────────────────────────────

    suspend fun signIn(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user ?: error("Sign-in returned null user")
    }

    suspend fun signUp(email: String, password: String, userName: String): FirebaseUser {
        if (userName.equals(GUEST_USERNAME, ignoreCase = true))
            throw IllegalArgumentException("Username 'Guest' is reserved.")
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("Registration returned null user")
        db.child("usernames").child(userName.lowercase()).setValue(email)
        db.child("uids").child(user.uid).setValue(userName)
        db.child("users").child(user.uid).setValue(
            mapOf(
                "userName" to userName,
                "email" to email,
                "userPfpReference" to DEFAULT_PFP,
                "darkmode" to true
            )
        )
        return user
    }

    fun signOut() = auth.signOut()

    // ── User Profile ──────────────────────────────────────────────────────

    suspend fun fetchUserProfile(uid: String): UserProfileData? =
        db.child("users").child(uid).get().await().toUserProfileData()

    suspend fun saveUserProfile(data: UserProfileData) {
        val uid = ensureAuth()
        val updates = mapOf(
            "userName" to data.userName,
            "email" to data.email,
            "userPfpReference" to data.userPfpReference,
            "darkmode" to data.darkmode
        )
        db.child("users").child(uid).updateChildren(updates).await()
    }

    fun observeUserProfile(uid: String): Flow<UserProfileData?> = callbackFlow {
        val ref = db.child("users").child(uid)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.toUserProfileData()) }
            override fun onCancelled(e: DatabaseError) { close(e.toException()) }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Resolves a UID to a display username. Falls back to "Unknown". */
    suspend fun getUsername(uid: String): String =
        db.child("uids").child(uid).get().await().getValue(String::class.java) ?: "Unknown"

    // ── Groups ────────────────────────────────────────────────────────────

    /**
     * Save or update a group. All member keys are UIDs.
     * New groups: groupId is null, Firebase generates one.
     * Updates: pass the existing groupId.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun saveGroup(group: GroupEntry, groupId: String? = null): String {
        Log.d("saveGroup", "entered — currentUid=${currentUid}, isLoggedIn=${UserProfileObject.isLoggedIn}, title=${group.title}")

        ensureAuth()
        val id = groupId ?: db.child("groups").push().key
        ?: error("Could not generate group key")

        val payload = mapOf(
            "title" to group.title,
            "description" to group.description,
            "eventDateTimeEpoch" to group.eventDateTime
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            "memberIds" to group.groupMemberNames.associateWith { true },
            "memberRoles" to group.memberRoles.mapValues { it.value.name },
            "locationSharingEnabled" to group.locationSharingEnabled,
            "canToggleSharing" to group.canToggleSharing,
            "tripStatus" to group.tripStatus.mapValues { it.value.name },
            "adminApplications" to group.adminApplications.associateWith { true },
            "adminApplicationVotes" to group.adminApplicationVotes
                .mapValues { it.value.associateWith { true } },
            "votesToRemoveAdmin" to group.votesToRemoveAdmin
                .mapValues { it.value.associateWith { true } }
        )

        db.child("groups").child(id).setValue(payload).await()

        // index this group for every member
        val updates = mutableMapOf<String, Any>()
        for (memberUid in group.groupMemberNames) {
            updates["users/$memberUid/groups/$id"] = true
        }

        db.updateChildren(updates).await()

        Log.d("FirebaseRepository", "saveGroup SUCCESS id=$id")

        return id
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun deleteGroup(groupId: String) {
        ensureAuth()

        // Read member list while group still exists
        val snap = db.child("groups").child(groupId).get().await()
        val memberIds = snap.child("memberIds").children.mapNotNull { it.key }

        // Phase 1: delete group and groupLocations while memberRoles still readable
        db.updateChildren(mapOf<String, Any?>(
            "groups/$groupId" to null,
            "groupLocations/$groupId" to null
        )).await()

        // Phase 2: clean up user index entries (group already gone, just needs auth != null)
        if (memberIds.isNotEmpty()) {
            val userIndexCleanup = memberIds.associate { uid ->
                "users/$uid/groups/$groupId" to null
            }
            db.updateChildren(userIndexCleanup).await()
        }
    }

    /**
     * Observe all groups the given UID is a member of.
     * Keeps the listener alive even on permission errors so it recovers
     * once auth resolves — never crashes the app.
     */
    fun observeMyGroups(uid: String): Flow<List<Pair<String, GroupEntry>>> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val indexRef = db.child("users").child(uid).child("groups")


        val listener = indexRef.addValueEventListener(object : ValueEventListener {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("FIREBASE", "Children count: ${snapshot.childrenCount}")
                val groupIds = snapshot.children.mapNotNull { it.key }

                if (groupIds.isEmpty()) {
                    trySend(emptyList())
                    return
                }

                val groups = mutableListOf<Pair<String, GroupEntry>>()

                groupIds.forEach { id ->
                    db.child("groups").child(id).get()
                        .addOnSuccessListener { snap ->
                            val entry = snap.toGroupEntry()
                            if (entry != null) {
                                groups.add(id to entry)
                                trySend(groups.toList())
                            }
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("FirebaseRepository", "observeMyGroups cancelled: ${error.message}")
                trySend(emptyList())
            }
        })

        awaitClose { indexRef.removeEventListener(listener) }
    }
    // ── Group Actions ─────────────────────────────────────────────────────

    /** Toggle location sharing for a UID. Enforces canToggleSharing. */
    suspend fun toggleLocationSharing(groupId: String, uid: String, enabled: Boolean) {
        ensureAuth()
        val canToggle = db.child("groups").child(groupId)
            .child("canToggleSharing").child(uid).get().await()
            .getValue(Boolean::class.java) ?: false
        if (!canToggle) throw IllegalStateException("Permission denied: sharing is locked for this user.")
        db.child("groups").child(groupId)
            .child("locationSharingEnabled").child(uid).setValue(enabled).await()
    }

    /** Set canToggleSharing for a member. Only Admins/SuperAdmins should call this. */
    suspend fun setCanToggleSharing(groupId: String, targetUid: String, allowed: Boolean) {
        ensureAuth()
        db.child("groups").child(groupId)
            .child("canToggleSharing").child(targetUid).setValue(allowed).await()
    }

    /** Member applies for admin role. */
    suspend fun applyForAdmin(groupId: String, uid: String) {
        ensureAuth()
        db.child("groups").child(groupId)
            .child("adminApplications").child(uid).setValue(true).await()
    }

    /** Vote to approve an admin application. Promotes if threshold met or voter is SuperAdmin. */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun voteForAdminApplication(groupId: String, applicantUid: String, voterUid: String) {
        ensureAuth()
        val groupRef = db.child("groups").child(groupId)

        // Record vote
        groupRef.child("adminApplicationVotes").child(applicantUid)
            .child(voterUid).setValue(true).await()

        // Check if threshold met
        val snap = groupRef.get().await()
        val entry = snap.toGroupEntry() ?: return
        val promoted = entry.voteForAdminApplication(applicantUid, voterUid)
        if (promoted) {
            val updates = mapOf(
                "memberRoles/$applicantUid" to GroupRole.ADMIN.name,
                "canToggleSharing/$applicantUid" to true,
                "adminApplications/$applicantUid" to null,
                "adminApplicationVotes/$applicantUid" to null
            )
            groupRef.updateChildren(updates).await()
        }
    }

    /** Cast a vote to remove an admin. Demotes if threshold met. SuperAdmin is protected. */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun voteToRemoveAdmin(groupId: String, targetUid: String, voterUid: String) {
        ensureAuth()
        val groupRef = db.child("groups").child(groupId)

        val snap = groupRef.get().await()
        val entry = snap.toGroupEntry() ?: return
        if (entry.isSuperAdmin(targetUid))
            throw IllegalStateException("Super-Admins cannot be removed.")

        // Record vote
        groupRef.child("votesToRemoveAdmin").child(targetUid)
            .child(voterUid).setValue(true).await()

        // Re-fetch and check threshold
        val updated = groupRef.get().await().toGroupEntry() ?: return
        if (updated.voteCountToRemove(targetUid) >= updated.votesNeededToRemove(targetUid)) {
            val updates = mapOf(
                "memberRoles/$targetUid" to GroupRole.MEMBER.name,
                "canToggleSharing/$targetUid" to false,
                "votesToRemoveAdmin/$targetUid" to null
            )
            groupRef.updateChildren(updates).await()
        }
    }

    /** Remove a member from a group. SuperAdmins cannot be removed. */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun removeMemberFromGroup(groupId: String, targetUid: String) {
        ensureAuth()
        val groupRef = db.child("groups").child(groupId)
        val entry = groupRef.get().await().toGroupEntry() ?: return
        if (entry.isSuperAdmin(targetUid))
            throw IllegalStateException("Super-Admins cannot be removed from groups.")

        val updates = mutableMapOf<String, Any?>(
            "memberIds/$targetUid" to null,
            "memberRoles/$targetUid" to null,
            "locationSharingEnabled/$targetUid" to null,
            "canToggleSharing/$targetUid" to null,
            "tripStatus/$targetUid" to null
        )
        groupRef.updateChildren(updates).await()
        db.child("groupLocations").child(groupId).child(targetUid).removeValue().await()
    }

    /** Set trip status. ARRIVED automatically stops location sharing. */
    suspend fun setTripStatus(groupId: String, uid: String, status: TripStatus) {
        ensureAuth()
        val updates = mutableMapOf<String, Any?>(
            "groups/$groupId/tripStatus/$uid" to status.name
        )
        if (status == TripStatus.ARRIVED) {
            updates["groups/$groupId/locationSharingEnabled/$uid"] = false
        }
        db.updateChildren(updates).await()
    }

    fun observeTripStatus(groupId: String): Flow<Map<String, TripStatus>> = callbackFlow {
        val ref = db.child("groups").child(groupId).child("tripStatus")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = snapshot.children.associate { child ->
                    val uid = child.key ?: ""
                    val status = try {
                        TripStatus.valueOf(child.getValue(String::class.java) ?: "INACTIVE")
                    } catch (e: Exception) { TripStatus.INACTIVE }
                    uid to status
                }
                trySend(map)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w("FirebaseRepository", "observeTripStatus cancelled: ${error.message}")
                trySend(emptyMap())
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Search for a user by username or email. Returns their UID or null if not found. */
    suspend fun findUserByUsernameOrEmail(query: String): Pair<String, String>? {
        return try {
            val trimmed = query.trim()
            val byUsername = db.child("usernames").child(trimmed.lowercase()).get().await()
            if (byUsername.exists()) {
                val uidsSnap = db.child("uids").get().await()
                val uid = uidsSnap.children.firstOrNull { snap ->
                    snap.getValue(String::class.java).equals(trimmed, ignoreCase = true)
                }?.key ?: return null
                return uid to trimmed
            }
            val usernamesSnap = db.child("usernames").get().await()
            val matchEntry = usernamesSnap.children.firstOrNull { snap ->
                snap.getValue(String::class.java).equals(trimmed, ignoreCase = true)
            } ?: return null
            val userName = matchEntry.key ?: return null
            val uidsSnap = db.child("uids").get().await()
            val uid = uidsSnap.children.firstOrNull { snap ->
                snap.getValue(String::class.java).equals(userName, ignoreCase = true)
            }?.key ?: return null
            uid to (uidsSnap.child(uid).getValue(String::class.java) ?: userName)
        } catch (e: Exception) {
            null
        }
    }


    /** Add a member to a group atomically. */
    suspend fun addMemberToGroup(groupId: String, targetUid: String) {
        ensureAuth()
        Log.d("addMember", "adding $targetUid to group $groupId, caller=${currentUid}")
        val updates = mapOf(
            "groups/$groupId/memberIds/$targetUid" to true,
            "groups/$groupId/memberRoles/$targetUid" to GroupRole.MEMBER.name,
            "groups/$groupId/locationSharingEnabled/$targetUid" to false,
            "groups/$groupId/canToggleSharing/$targetUid" to false,
            "groups/$groupId/tripStatus/$targetUid" to TripStatus.INACTIVE.name,
            "users/$targetUid/groups/$groupId" to true
        )
        try {
            db.updateChildren(updates).await()
            Log.d("addMember", "SUCCESS")
        } catch (e: Exception) {
            Log.e("addMember", "FAILED: ${e.message}", e)
        }
    }

    // ── Live Location ─────────────────────────────────────────────────────

    fun pushLocation(groupId: String, uid: String, lat: Double, lng: Double) {
        if (isGuest()) return
        db.child("groupLocations").child(groupId).child(uid).setValue(
            mapOf("lat" to lat, "lng" to lng, "ts" to ServerValue.TIMESTAMP)
        )
    }

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
            override fun onCancelled(error: DatabaseError) {
                Log.w("FirebaseRepository", "observeGroupLocations cancelled: ${error.message}")
                trySend(emptyMap())
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Geo Alarms ────────────────────────────────────────────────────────

    fun observeGeoAlarms(uid: String): Flow<List<GeoAlarm>> = callbackFlow {
        if (uid.isEmpty()) { trySend(emptyList()); close(); return@callbackFlow }
        val ref = db.child("geoAlarms").child(uid)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.toGeoAlarm() })
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w("FirebaseRepository", "observeGeoAlarms cancelled: ${error.message}")
                trySend(emptyList())
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun saveGeoAlarm(uid: String, alarm: GeoAlarm) {
        val alarmData = alarm.toMap()
        val updates = mapOf(
            "geoAlarms/$uid/${alarm.id}" to alarmData,
            "users/$uid/geoAlarms/${alarm.id}" to alarmData
        )
        db.updateChildren(updates).await()
    }

    suspend fun deleteGeoAlarm(uid: String, alarmId: String) {
        val updates = mapOf(
            "geoAlarms/$uid/$alarmId" to null,
            "users/$uid/geoAlarms/$alarmId" to null
        )
        db.updateChildren(updates).await()
    }

    // ── User Geofences ────────────────────────────────────────────────────

    fun observeUserGeofences(uid: String): Flow<List<DataSnapshot>> = callbackFlow {
        if (uid.isEmpty()) { trySend(emptyList()); close(); return@callbackFlow }
        val ref = db.child("geofences").child(uid)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.toList())
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w("FirebaseRepository", "observeUserGeofences cancelled: ${error.message}")
                trySend(emptyList())
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun saveGeofence(uid: String, geofence: GeofenceArea) {
        val data = mapOf(
            "id" to geofence.id,
            "name" to geofence.name,
            "center" to mapOf("lat" to geofence.center.latitude, "lng" to geofence.center.longitude),
            "typeId" to geofence.typeId,
            "radius" to geofence.radius,
            "points" to geofence.points.map { mapOf("lat" to it.latitude, "lng" to it.longitude) },
            "geoAlarmId" to geofence.geoAlarmId
        )
        db.child("geofences").child(uid).child(geofence.id).setValue(data).await()
    }

    suspend fun saveUserGeofences(uid: String, data: Any) {
        ensureAuth()
        db.child("geofences").child(uid).setValue(data).await()
    }

    /**
     * Saves geofence to Firebase for cloud sync.
     */
    fun saveGeofenceToFirebase(
        database: DatabaseReference,
        geofence: GeofenceArea,
        context: Context
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, "Log in to save Geofence to Cloud", Toast.LENGTH_SHORT).show()
            return
        }

        val key = geofence.id

        database.child("geofences")
            .child(uid)
            .child(key)
            .setValue(geofence)
            .addOnSuccessListener {
                Toast.makeText(context, "Geofence Saved to Cloud", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to save geofence", Toast.LENGTH_SHORT).show()
                Log.e("FirebaseRepository", "Geofence upload failed", it)
            }    }

    /**
     * Removes geofence from Firebase.
     */
    fun deleteGeofenceFromFirebase(
        database: DatabaseReference,
        userId: String,
        geofenceName: String,
        context: Context
    ) {
        if (userId.isEmpty()) {
            Toast.makeText(context, "Fatal error occurred. this toast should never be shown", Toast.LENGTH_SHORT).show()
            return
        }
        val key = geofenceName.filter { it.isLetterOrDigit() }
        database.child(userId).child(key).removeValue()

        Toast.makeText(context, "Geofence Deleted from Cloud", Toast.LENGTH_SHORT).show()
    }
}

// ── Data classes ──────────────────────────────────────────────────────────────

data class LatLngSnapshot(val lat: Double, val lng: Double)

// ── Snapshot extension helpers ────────────────────────────────────────────────

private fun DataSnapshot.toUserProfileData(): UserProfileData? {
    val userName = child("userName").getValue(String::class.java) ?: return null
    val email = child("email").getValue(String::class.java) ?: ""
    val pfp = child("userPfpReference").getValue(String::class.java) ?: DEFAULT_PFP
    val dark = child("darkmode").getValue(Boolean::class.java) ?: true
    return UserProfileData(userName = userName, email = email, userPfpReference = pfp, darkmode = dark)
}

@RequiresApi(Build.VERSION_CODES.O)
fun DataSnapshot.toGroupEntry(): GroupEntry? {
    val title = child("title").getValue(String::class.java) ?: return null
    val description = child("description").getValue(String::class.java) ?: ""
    val epoch = child("eventDateTimeEpoch").getValue(Long::class.java) ?: 0L
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())

    val memberIds = child("memberIds").children.mapNotNull { it.key }.toMutableList()

    val memberRoles = child("memberRoles").children.associate { snap ->
        (snap.key ?: "") to (try {
            GroupRole.valueOf(snap.getValue(String::class.java) ?: "MEMBER")
        } catch (e: Exception) { GroupRole.MEMBER })
    }.toMutableMap()

    val locationSharing = child("locationSharingEnabled").children.associate { snap ->
        (snap.key ?: "") to (snap.getValue(Boolean::class.java) ?: false)
    }.toMutableMap()

    val canToggle = child("canToggleSharing").children.associate { snap ->
        (snap.key ?: "") to (snap.getValue(Boolean::class.java) ?: false)
    }.toMutableMap()

    val tripStatuses = child("tripStatus").children.associate { snap ->
        (snap.key ?: "") to (try {
            TripStatus.valueOf(snap.getValue(String::class.java) ?: "INACTIVE")
        } catch (e: Exception) { TripStatus.INACTIVE })
    }.toMutableMap()

    val applications = child("adminApplications").children
        .mapNotNull { it.key }.toMutableSet()

    val applicationVotes = child("adminApplicationVotes").children.associate { snap ->
        (snap.key ?: "") to snap.children.mapNotNull { it.key }.toMutableSet()
    }.toMutableMap()

    val removalVotes = child("votesToRemoveAdmin").children.associate { targetSnap ->
        (targetSnap.key ?: "") to targetSnap.children.mapNotNull { it.key }.toMutableSet()
    }.toMutableMap()

    return GroupEntry(
        title = title,
        eventDateTime = dateTime,
        description = description,
        groupMemberNames = memberIds,
        memberRoles = memberRoles,
        locationSharingEnabled = locationSharing,
        canToggleSharing = canToggle,
        tripStatus = tripStatuses,
        adminApplications = applications,
        adminApplicationVotes = applicationVotes,
        votesToRemoveAdmin = removalVotes
    )
}

private fun GeoAlarm.toMap(): Map<String, Any?> = mapOf(
    "id" to id, "name" to name, "active" to active,
    "description" to description, "geofenceId" to geofenceId,
    "specificDate" to specificDate?.toString(), "dayOfWeek" to dayOfWeek?.name,
    "startTime" to startTime?.toString(), "endTime" to endTime?.toString()
)

@RequiresApi(Build.VERSION_CODES.O)
private fun DataSnapshot.toGeoAlarm(): GeoAlarm? {
    val id = child("id").getValue(String::class.java) ?: return null
    return GeoAlarm(
        id = id,
        name = child("name").getValue(String::class.java) ?: "",
        active = child("active").getValue(Boolean::class.java) ?: false,
        description = child("description").getValue(String::class.java) ?: "",
        geofenceId = child("geofenceId").getValue(String::class.java),
        specificDate = child("specificDate").getValue(String::class.java)
            ?.let { LocalDate.parse(it) },
        dayOfWeek = child("dayOfWeek").getValue(String::class.java)
            ?.let { DayOfWeek.valueOf(it) },
        startTime = child("startTime").getValue(String::class.java)
            ?.let { LocalTime.parse(it) },
        endTime = child("endTime").getValue(String::class.java)
            ?.let { LocalTime.parse(it) }
    )
}