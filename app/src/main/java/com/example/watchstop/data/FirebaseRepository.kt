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
import kotlinx.coroutines.flow.combine
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
     * Save a NEW group only. groupId must be null — Firebase generates the key.
     * Never call this to update an existing group; use updateGroupMetadata instead.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun saveGroup(group: GroupEntry, groupId: String? = null): String {
        Log.d("saveGroup", "entered — currentUid=${currentUid}, title=${group.title}")
        ensureAuth()

        val id = groupId ?: db.child("groups").push().key
        ?: error("Could not generate group key")

        val updates = mutableMapOf<String, Any?>(
            "groups/$id/title"              to group.title,
            "groups/$id/description"        to group.description,
            "groups/$id/eventDateTimeEpoch" to group.eventDateTime
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            "groups/$id/memberRoles"        to group.memberRoles.mapValues { it.value.name },
            "groups/$id/locationSharingEnabled" to group.locationSharingEnabled,
            "groups/$id/canToggleSharing"   to group.canToggleSharing,
            "groups/$id/tripStatus"         to group.tripStatus.mapValues { it.value.name },
            "groups/$id/adminApplications"  to group.adminApplications.associateWith { true },
            "groups/$id/adminApplicationVotes" to group.adminApplicationVotes
                .mapValues { it.value.associateWith { true } },
            "groups/$id/votesToRemoveAdmin" to group.votesToRemoveAdmin
                .mapValues { it.value.associateWith { true } }
        )

        for (uid in group.groupMemberNames) {
            updates["groups/$id/memberIds/$uid"] = true
        }
        for (uid in group.pendingInvitations) {
            updates["groups/$id/pendingInvitations/$uid"] = true
        }
        for (memberUid in group.groupMemberNames) {
            updates["users/$memberUid/groups/$id"] = true
        }
        for (invitedUid in group.pendingInvitations) {
            updates["users/$invitedUid/invitations/$id"] = true
        }

        db.updateChildren(updates).await()
        Log.d("FirebaseRepository", "saveGroup SUCCESS id=$id")
        return id
    }

    /**
     * Update only the metadata fields of an existing group.
     *
     * [explicitlyRemovedMembers] is the set of UIDs the admin explicitly kicked via the UI.
     * We ONLY remove those — we never diff against live Firebase state. This prevents the
     * bug where a member who accepted an invitation between the admin opening the screen and
     * pressing Save gets incorrectly evicted.
     *
     * Uses groupRef.updateChildren with RELATIVE paths so the root group .write rule is
     * evaluated against the group node (where memberRoles exists), not the database root.
     *
     * pendingInvitations is never touched — managed exclusively by inviteToGroup /
     * cancelInvitation.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun updateGroupMetadata(
        groupId: String,
        group: GroupEntry,
        explicitlyRemovedMembers: Set<String> = emptySet()
    ) {
        ensureAuth()
        val groupRef = db.child("groups").child(groupId)

        // Build updates for the main group metadata
        val updates = mutableMapOf<String, Any?>(
            "title" to group.title,
            "description" to group.description,
            "eventDateTimeEpoch" to group.eventDateTime
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            "adminApplications" to group.adminApplications.associateWith { true },
            "adminApplicationVotes" to group.adminApplicationVotes
                .mapValues { it.value.associateWith { true } },
            "votesToRemoveAdmin" to group.votesToRemoveAdmin
                .mapValues { it.value.associateWith { true } }
        )

        // Instead of updating the entire maps, update each child individually
        // to avoid parent/child path conflicts

        // Update memberRoles individually
        group.memberRoles.forEach { (uid, role) ->
            updates["memberRoles/$uid"] = role.name
        }

        // Update canToggleSharing individually
        group.canToggleSharing.forEach { (uid, canToggle) ->
            updates["canToggleSharing/$uid"] = canToggle
        }

        // Only remove members the admin explicitly kicked
        for (uid in explicitlyRemovedMembers) {
            updates["memberIds/$uid"] = null
            updates["memberRoles/$uid"] = null
            updates["locationSharingEnabled/$uid"] = null
            updates["canToggleSharing/$uid"] = null
            updates["tripStatus/$uid"] = null
        }

        groupRef.updateChildren(updates).await()

        if (explicitlyRemovedMembers.isNotEmpty()) {
            val cleanup = mutableMapOf<String, Any?>()
            for (uid in explicitlyRemovedMembers) {
                cleanup["users/$uid/groups/$groupId"] = null
            }
            db.updateChildren(cleanup).await()
            for (uid in explicitlyRemovedMembers) {
                db.child("groupLocations").child(groupId).child(uid).removeValue().await()
            }
        }

        Log.d("FirebaseRepository",
            "updateGroupMetadata SUCCESS id=$groupId, removed=$explicitlyRemovedMembers")
    }

    /**
     * Admin declines a member's admin application — removes it without promoting.
     *
     * FIX: uses groupRef.updateChildren with RELATIVE paths instead of db.updateChildren
     * with full absolute paths. When db.updateChildren is called with paths like
     * "groups/$groupId/adminApplications/...", Firebase evaluates the root ".write" rule
     * against data at "/", where data.child('memberRoles') is null — causing permission
     * denied even for group admins. Using groupRef scopes the rule evaluation to the
     * group node where memberRoles actually exists.
     */
    suspend fun declineAdminApplication(groupId: String, applicantUid: String) {
        ensureAuth()
        val groupRef = db.child("groups").child(groupId)
        val updates = mapOf<String, Any?>(
            "adminApplications/$applicantUid" to null,
            "adminApplicationVotes/$applicantUid" to null
        )
        groupRef.updateChildren(updates).await()
        Log.d("FirebaseRepository", "declineAdminApplication: removed $applicantUid from $groupId")
    }

    /**
     * Cancel a pending invitation. Atomically removes the entry from both the group
     * and the invitee's user index so the notification disappears on their screen.
     */
    suspend fun cancelInvitation(groupId: String, targetUid: String) {
        ensureAuth()
        val updates = mapOf<String, Any?>(
            "groups/$groupId/pendingInvitations/$targetUid" to null,
            "users/$targetUid/invitations/$groupId" to null
        )
        db.updateChildren(updates).await()
        Log.d("FirebaseRepository", "cancelInvitation: removed $targetUid from $groupId")
    }

    /** Directly promote a member to Admin. Only SuperAdmins should call this. */
    suspend fun promoteToAdmin(groupId: String, targetUid: String) {
        ensureAuth()
        val groupRef = db.child("groups").child(groupId)
        val updates = mapOf<String, Any?>(
            "memberRoles/$targetUid"      to GroupRole.ADMIN.name,
            "canToggleSharing/$targetUid" to true
        )
        groupRef.updateChildren(updates).await()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun deleteGroup(groupId: String) {
        ensureAuth()

        //TODO: fixing
        // Get all related data first
        val groupSnap = db.child("groups").child(groupId).get().await()

        // Clean up locations
        db.child("groupLocations").child(groupId).removeValue().await()

        // Clean up any geofences related to this group
        val geofencesSnap = db.child("geofences").get().await()
        geofencesSnap.children.forEach { userGeofences ->
            userGeofences.children.forEach { geofence ->
                if (geofence.child("groupId").getValue(String::class.java) == groupId) {
                    geofence.ref.removeValue().await()
                }
            }
        }
        //TODO: fix END EXTRA

        val groupRef = db.child("groups").child(groupId)

        val snap = groupRef.get().await()
        val memberIds = snap.child("memberIds").children.mapNotNull { it.key }
        val invitedIds = snap.child("pendingInvitations").children.mapNotNull { it.key }

        val cleanup = mutableMapOf<String, Any?>()
        memberIds.forEach { uid -> cleanup["users/$uid/groups/$groupId"] = null }
        invitedIds.forEach { uid -> cleanup["users/$uid/invitations/$groupId"] = null }

        //cleanup BEFORE deleting group (so rules still see memberRoles)
        if (cleanup.isNotEmpty()) {
            db.updateChildren(cleanup).await()
        }

        //now safe to delete
        groupRef.removeValue().await()
        db.child("groupLocations").child(groupId).removeValue().await()
    }

    /**
     * A non-SuperAdmin member or admin leaves the group.
     *
     * - SuperAdmins cannot leave — they must Disband. The UI enforces this by hiding
     *   the Leave button for SuperAdmins.
     * - Regular Admins can only leave if a SuperAdmin exists in the group (the UI also
     *   enforces this). If the admin is the last leader, the Leave button is hidden.
     * - If the group would become empty after leaving, it is disbanded automatically.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun leaveGroup(groupId: String, uid: String) {
        ensureAuth()
        val snap = db.child("groups").child(groupId).get().await()
        val entry = snap.toGroupEntry() ?: return

        if (entry.isSuperAdmin(uid)) {
            throw IllegalStateException("Super-Admins must use Disband instead of Leave.")
        }

        val remainingMembers = entry.groupMemberNames.filter { it != uid }
        if (remainingMembers.isEmpty()) {
            deleteGroup(groupId)
            return
        }

        val groupRef = db.child("groups").child(groupId)

        // FIRST: Remove from groupLocations (requires being in memberIds)
        db.child("groupLocations").child(groupId).child(uid).removeValue().await()

        // THEN: Remove from memberIds (allowed by rules for the user)
        groupRef.child("memberIds").child(uid).removeValue().await()

        // Try to remove role (if rules allow)
        try {
            groupRef.child("memberRoles").child(uid).removeValue().await()
        } catch (e: DatabaseException) {
            Log.w("FirebaseRepository", "Could not remove role: ${e.message}")
        }

        // Remove location sharing settings
        groupRef.child("locationSharingEnabled").child(uid).removeValue().await()
        groupRef.child("canToggleSharing").child(uid).removeValue().await()

        // Remove trip status
        groupRef.child("tripStatus").child(uid).removeValue().await()

        // Remove from user's groups list
        db.child("users").child(uid).child("groups").child(groupId).removeValue().await()

        Log.d("FirebaseRepository", "leaveGroup: $uid left group $groupId")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getGroup(groupId: String): GroupEntry? {
        val snap = db.child("groups").child(groupId).get().await()
        return snap.toGroupEntry()
    }

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
                val groupIds = snapshot.children.mapNotNull { it.key }
                if (groupIds.isEmpty()) {
                    trySend(emptyList())
                    return
                }

                val groups = mutableListOf<Pair<String, GroupEntry>>()
                var loadedCount = 0
                groupIds.forEach { id ->
                    db.child("groups").child(id).get()
                        .addOnSuccessListener { snap ->
                            val entry = snap.toGroupEntry()
                            if (entry != null) {
                                groups.add(id to entry)
                            }
                            loadedCount++
                            if (loadedCount == groupIds.size) {
                                trySend(groups.toList())
                            }
                        }
                        .addOnFailureListener {
                            loadedCount++
                            if (loadedCount == groupIds.size) {
                                trySend(groups.toList())
                            }
                        }
                }
            }
            override fun onCancelled(error: DatabaseError) { trySend(emptyList()) }
        })
        awaitClose { indexRef.removeEventListener(listener) }
    }

    fun observeMyInvitations(uid: String): Flow<List<Pair<String, GroupEntry>>> = callbackFlow {
        if (uid.isEmpty()) { trySend(emptyList()); awaitClose {}; return@callbackFlow }
        val ref = db.child("users").child(uid).child("invitations")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onDataChange(snapshot: DataSnapshot) {
                val ids = snapshot.children.mapNotNull { it.key }
                if (ids.isEmpty()) { trySend(emptyList()); return }
                val list = mutableListOf<Pair<String, GroupEntry>>()
                var count = 0
                ids.forEach { id ->
                    db.child("groups").child(id).get().addOnSuccessListener { snap ->
                        snap.toGroupEntry()?.let { list.add(id to it) }
                        if (++count == ids.size) trySend(list.toList())
                    }.addOnFailureListener { if (++count == ids.size) trySend(list.toList()) }
                }
            }
            override fun onCancelled(e: DatabaseError) { trySend(emptyList()) }
        })
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observePendingInvitationsSentByMe(groupId: String): Flow<List<String>> = callbackFlow {
        val ref = Firebase.database.reference
            .child("groups")
            .child(groupId)
            .child("pendingInvitations")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.key }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Notifications ─────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    fun observeAllNotifications(uid: String): Flow<List<NotificationItem>> {
        return combine(
            observeMyInvitations(uid),
            observeMyGroups(uid)
        ) { invitations, groups ->
            val list = mutableListOf<NotificationItem>()

            invitations.forEach { (id, group) ->
                list.add(NotificationItem.Invitation(id, group.title))
            }

            groups.forEach { (groupId, group) ->
                val myRole = group.getRole(uid)
                val isAdmin = myRole == GroupRole.ADMIN || myRole == GroupRole.SUPER_ADMIN

                if (isAdmin) {
                    group.adminApplications.filter { it != uid }.forEach { applicantUid ->
                        list.add(NotificationItem.AdminApplication(groupId, group.title, applicantUid))
                    }
                }

                group.votesToRemoveAdmin.forEach { (targetUid, voters) ->
                    if (targetUid != uid && !voters.contains(uid)) {
                        list.add(NotificationItem.RemovalVote(groupId, group.title, targetUid))
                    }
                }
            }
            list
        }
    }

    // ── Group Actions ─────────────────────────────────────────────────────

    suspend fun inviteToGroup(groupId: String, targetUid: String) {
        ensureAuth()
        val updates = mapOf(
            "groups/$groupId/pendingInvitations/$targetUid" to true,
            "users/$targetUid/invitations/$groupId" to true
        )
        db.updateChildren(updates).await()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun acceptInvitation(groupId: String) {
        val uid = ensureAuth()
        val snap = db.child("groups").child(groupId).get().await()
        snap.toGroupEntry() ?: throw IllegalStateException("Group not found")

        val updates = mapOf<String, Any?>(
            "groups/$groupId/memberIds/$uid"              to true,
            "groups/$groupId/pendingInvitations/$uid"     to null,
            "groups/$groupId/memberRoles/$uid"            to GroupRole.MEMBER.name,
            "groups/$groupId/locationSharingEnabled/$uid" to false,
            "groups/$groupId/canToggleSharing/$uid"       to false,
            "groups/$groupId/tripStatus/$uid"             to TripStatus.INACTIVE.name,
            "users/$uid/groups/$groupId"                  to true,
            "users/$uid/invitations/$groupId"             to null
        )
        db.updateChildren(updates).await()
    }

    suspend fun declineInvitation(groupId: String) {
        val uid = ensureAuth()
        val updates = mapOf<String, Any?>(
            "groups/$groupId/pendingInvitations/$uid" to null,
            "users/$uid/invitations/$groupId" to null
        )
        db.updateChildren(updates).await()
    }

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

    /** Member applies for admin role. Writes only to adminApplications/$uid — member safe. */
    suspend fun applyForAdmin(groupId: String, uid: String) {
        ensureAuth()
        db.child("groups").child(groupId)
            .child("adminApplications").child(uid).setValue(true).await()
    }

    /**
     * Vote to approve an admin application. Promotes if threshold met or voter is SuperAdmin.
     *
     * Uses groupRef.updateChildren with RELATIVE paths for the promotion step so the
     * root group .write rule is evaluated against the correct group node (where memberRoles
     * exists), not the database root.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun voteForAdminApplication(groupId: String, applicantUid: String, voterUid: String) {
        ensureAuth()
        val groupRef = db.child("groups").child(groupId)
        val snap = groupRef.get().await()
        val entry = snap.toGroupEntry() ?: return

        // 1. Increment vote count
        val currentVotes = entry.adminApplicationVotes[applicantUid]?.size ?: 0
        val voteUpdates = mapOf(
            voterUid to true,
            "count" to currentVotes + 1
        )
        groupRef.child("adminApplicationVotes").child(applicantUid).updateChildren(voteUpdates).await()

        // 2. Re-evaluate threshold
        val freshSnap = groupRef.get().await()
        val totalMembers = freshSnap.child("memberCount").getValue(Int::class.java) ?: 1
        val updatedVotes = freshSnap.child("adminApplicationVotes").child(applicantUid).child("count").getValue(Int::class.java) ?: 0

        if (updatedVotes >= (totalMembers / 2)) {
            val promoteUpdates = mapOf<String, Any?>(
                "memberRoles/$applicantUid" to "ADMIN",
                "canToggleSharing/$applicantUid" to true,
                "adminApplications/$applicantUid" to null,
                "adminApplicationVotes/$applicantUid" to null
            )
            groupRef.updateChildren(promoteUpdates).await()
        }
    }

    /** Cast a vote to remove an admin. Demotes if threshold met. SuperAdmin is protected. */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun voteToRemoveAdmin(groupId: String, targetUid: String, voterUid: String): Boolean {
        ensureAuth()
        val groupRef = db.child("groups").child(groupId)
        val snap = groupRef.get().await()
        val entry = snap.toGroupEntry() ?: return false

        if (entry.isSuperAdmin(targetUid)) throw IllegalStateException("Cannot vote to remove a Super Admin")

        if (entry.isSuperAdmin(voterUid)) {
            // SuperAdmin demotes immediately
            val updates = mapOf(
                "memberRoles/$targetUid" to GroupRole.MEMBER.name,
                "canToggleSharing/$targetUid" to false,
                "votesToRemoveAdmin/$targetUid" to null
            )
            groupRef.updateChildren(updates).await()
            Log.d("FirebaseRepository", "SuperAdmin demoted $targetUid")
            return true // Demotion happened immediately
        } else {
            // Regular vote - add the vote and increment count
            val voteRef = groupRef.child("votesToRemoveAdmin").child(targetUid)

            // Get current vote count
            val countSnapshot = voteRef.child("count").get().await()
            val currentCount = countSnapshot.getValue(Int::class.java) ?: 0
            val newCount = currentCount + 1

            // Prepare updates
            val updates = mapOf(
                "$voterUid" to true,
                "count" to newCount
            )

            // Update both the vote and the count
            voteRef.updateChildren(updates).await()
            Log.d("FirebaseRepository", "Vote cast by $voterUid to remove $targetUid. Count: $newCount")

            // Check if threshold is now met
            val totalMembers = entry.groupMemberNames.size
            val needed = (totalMembers / 2) + 1

            // If threshold met, DEMOTE AUTOMATICALLY
            if (newCount >= needed) {
                Log.d("FirebaseRepository", "Threshold met! Demoting $targetUid to MEMBER")

                val demoteUpdates = mapOf(
                    "memberRoles/$targetUid" to GroupRole.MEMBER.name,
                    "canToggleSharing/$targetUid" to false,
                    "votesToRemoveAdmin/$targetUid" to null
                )
                groupRef.updateChildren(demoteUpdates).await()
                Log.d("FirebaseRepository", "Auto-demoted $targetUid after vote threshold met")
                return true
            }

            return false
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
        db.child("users").child(targetUid).child("groups").child(groupId).removeValue().await()
    }

    /** Set trip status. TRAVELLING auto-enables sharing; ARRIVED auto-stops it. */
    suspend fun setTripStatus(groupId: String, uid: String, status: TripStatus) {
        ensureAuth()
        // tripStatus/$uid: rule allows $uid === auth.uid — safe for any member.
        // locationSharingEnabled/$uid when TRAVELLING: rule allows newData.val() === true — safe.
        // locationSharingEnabled/$uid when ARRIVED (setting false): rule allows
        //   canToggleSharing === true OR admin. If canToggleSharing is false for this member,
        //   the ARRIVED case would be denied. We skip writing false in that case since
        //   the user wasn't sharing to begin with.
        val updates = mutableMapOf<String, Any?>(
            "groups/$groupId/tripStatus/$uid" to status.name
        )
        if (status == TripStatus.TRAVELLING) {
            updates["groups/$groupId/locationSharingEnabled/$uid"] = true
        }
        if (status == TripStatus.ARRIVED) {
            // Only write the false if they were sharing (canToggleSharing must be true for
            // them to have been sharing, so this write will be permitted).
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

    /** Search for a user by username or email. Returns (uid, displayName) or null. */
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
        database.child("geofences").child(uid).child(key)
            .setValue(geofence)
            .addOnSuccessListener {
                Toast.makeText(context, "Geofence Saved to Cloud", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to save geofence", Toast.LENGTH_SHORT).show()
                Log.e("FirebaseRepository", "Geofence upload failed", it)
            }
    }

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


// ── Notification Item Sealed Class ───────────────────────────────────────────

sealed class NotificationItem {
    data class Invitation(val groupId: String, val groupTitle: String) : NotificationItem()
    data class AdminApplication(val groupId: String, val groupTitle: String, val applicantUid: String) : NotificationItem()
    data class RemovalVote(val groupId: String, val groupTitle: String, val targetUid: String) : NotificationItem()
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
    val invitations = child("pendingInvitations").children.mapNotNull { it.key }.toMutableSet()

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
        val targetUid = targetSnap.key ?: ""
        // Get all voter UIDs (excluding the count field)
        val voters = targetSnap.children
            .filter { it.key != "count" }
            .mapNotNull { it.key }
            .toMutableSet()
        targetUid to voters
    }.toMutableMap()

    return GroupEntry(
        title = title,
        eventDateTime = dateTime,
        description = description,
        groupMemberNames = memberIds,
        pendingInvitations = invitations,
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