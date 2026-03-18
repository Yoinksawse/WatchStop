package com.example.watchstop.model

import java.time.LocalDateTime

enum class GroupRole(val displayName: String) {
    SUPER_ADMIN("Super Admin"),
    ADMIN("Admin"),
    MEMBER("Member")
}

enum class TripStatus {
    INACTIVE, TRAVELLING, ARRIVED
}

data class GroupEntry(
    var title: String,
    var eventDateTime: LocalDateTime,
    var description: String,
    // All member keys are Firebase UIDs
    var groupMemberNames: MutableList<String> = mutableListOf(),
    val pendingInvitations: MutableSet<String> = mutableSetOf(),
    val memberRoles: MutableMap<String, GroupRole> = mutableMapOf(),
    val locationSharingEnabled: MutableMap<String, Boolean> = mutableMapOf(),
    val canToggleSharing: MutableMap<String, Boolean> = mutableMapOf(),
    val tripStatus: MutableMap<String, TripStatus> = mutableMapOf(),
    val adminApplications: MutableSet<String> = mutableSetOf(),
    val adminApplicationVotes: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val votesToRemoveAdmin: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    // New counter fields for security rules
    var memberCount: Int = 0,
    val voteCountsToRemoveAdmin: MutableMap<String, Int> = mutableMapOf()
) {
    /** Deep-copy constructor */
    constructor(other: GroupEntry) : this(
        title = other.title,
        eventDateTime = other.eventDateTime,
        description = other.description,
        groupMemberNames = other.groupMemberNames.toMutableList(),
        pendingInvitations = other.pendingInvitations.toMutableSet(),
        memberRoles = other.memberRoles.toMutableMap(),
        locationSharingEnabled = other.locationSharingEnabled.toMutableMap(),
        canToggleSharing = other.canToggleSharing.toMutableMap(),
        tripStatus = other.tripStatus.toMutableMap(),
        adminApplications = other.adminApplications.toMutableSet(),
        adminApplicationVotes = other.adminApplicationVotes
            .mapValues { it.value.toMutableSet() }.toMutableMap(),
        votesToRemoveAdmin = other.votesToRemoveAdmin
            .mapValues { it.value.toMutableSet() }.toMutableMap(),
        memberCount = other.memberCount,
        voteCountsToRemoveAdmin = other.voteCountsToRemoveAdmin.toMutableMap()
    )

    // ─── Member Management ─────────────────────────────────────────────────

    fun addMember(uid: String, role: GroupRole) {
        if (!groupMemberNames.contains(uid)) {
            groupMemberNames.add(uid)
            memberCount = groupMemberNames.size
        }
        pendingInvitations.remove(uid)
        memberRoles[uid] = role
        locationSharingEnabled[uid] = false
        // Requirement: Administrators or Super-Administrators can disallow a user from disabling location sharing.
        // Members by default might not be allowed to toggle if an admin says so.
        // Initial state: Members cannot toggle, Admins can.
        canToggleSharing[uid] = role != GroupRole.MEMBER
        tripStatus[uid] = TripStatus.INACTIVE
    }

    fun removeMember(uid: String) {
        groupMemberNames.remove(uid)
        memberCount = groupMemberNames.size
        memberRoles.remove(uid)
        locationSharingEnabled.remove(uid)
        canToggleSharing.remove(uid)
        tripStatus.remove(uid)
        adminApplications.remove(uid)
        adminApplicationVotes.remove(uid)
        votesToRemoveAdmin.remove(uid)
        voteCountsToRemoveAdmin.remove(uid)
    }

    fun inviteMember(uid: String) {
        if (!groupMemberNames.contains(uid)) {
            pendingInvitations.add(uid)
        }
    }

    fun setCanToggleSharing(uid: String, allowed: Boolean) {
        canToggleSharing[uid] = allowed
    }

    // ─── Location Sharing ──────────────────────────────────────────────────

    fun toggleSharing(uid: String): Boolean {
        // Requirement: A user may stop sharing their location only if permitted by an Administrator or Super-Administrator.
        if (canToggleSharing[uid] != true) return false
        val current = locationSharingEnabled[uid] ?: false
        locationSharingEnabled[uid] = !current
        return true
    }

    fun setSharing(uid: String, enabled: Boolean) {
        locationSharingEnabled[uid] = enabled
    }

    // ─── Trip Status ───────────────────────────────────────────────────────

    fun setTripStatus(uid: String, status: TripStatus) {
        tripStatus[uid] = status
        // Requirement: During active trips, every member’s live location is shared with the group.
        if (status == TripStatus.TRAVELLING) {
            locationSharingEnabled[uid] = true
        }
        // Arriving stops sharing automatically
        if (status == TripStatus.ARRIVED) {
            locationSharingEnabled[uid] = false
        }
    }

    // ─── Admin Applications ────────────────────────────────────────────────

    fun applyForAdmin(uid: String): Boolean {
        val role = memberRoles[uid] ?: return false
        if (role != GroupRole.MEMBER) return false
        if (adminApplications.contains(uid)) return false
        adminApplications.add(uid)
        adminApplicationVotes[uid] = mutableSetOf()
        return true
    }

    fun voteForAdminApplication(applicant: String, voterUid: String): Boolean {
        if (!adminApplications.contains(applicant)) return false
        val voterRole = memberRoles[voterUid] ?: return false

        val votes = adminApplicationVotes.getOrPut(applicant) { mutableSetOf() }
        votes.add(voterUid)

        // Requirement: Super-Administrator directly approves the request.
        if (voterRole == GroupRole.SUPER_ADMIN) {
            promoteToAdmin(applicant)
            return true
        }

        // Requirement: A majority of group members vote in favor
        val eligibleVoters = groupMemberNames.filter { it != applicant }
        if (votes.size > eligibleVoters.size / 2) {
            promoteToAdmin(applicant)
            return true
        }
        return false
    }

    private fun promoteToAdmin(uid: String) {
        memberRoles[uid] = GroupRole.ADMIN
        canToggleSharing[uid] = true
        adminApplications.remove(uid)
        adminApplicationVotes.remove(uid)
    }

    // ─── Admin Removal Voting ──────────────────────────────────────────────

    fun voteToRemoveAdmin(targetUid: String, voterUid: String): Boolean {
        val targetRole = memberRoles[targetUid] ?: return false
        // Requirement: Super-Administrator cannot be removed or kicked from the group.
        if (targetRole == GroupRole.SUPER_ADMIN) return false
        if (targetRole != GroupRole.ADMIN) return false
        if (targetUid == voterUid) return false

        val votes = votesToRemoveAdmin.getOrPut(targetUid) { mutableSetOf() }
        votes.add(voterUid)

        // Update vote count
        voteCountsToRemoveAdmin[targetUid] = votes.size

        // Requirement: If a majority agrees, the administrator role is revoked.
        val eligibleVoters = groupMemberNames.filter { it != targetUid }
        if (votes.size > eligibleVoters.size / 2) {
            memberRoles[targetUid] = GroupRole.MEMBER
            canToggleSharing[targetUid] = false
            votesToRemoveAdmin.remove(targetUid)
            voteCountsToRemoveAdmin.remove(targetUid)
            return true
        }
        return false
    }

    fun votesNeededToRemove(targetUid: String): Int {
        val eligibleVoters = groupMemberNames.filter { it != targetUid }
        return (eligibleVoters.size / 2) + 1
    }

    fun voteCountToRemove(targetUid: String): Int =
        votesToRemoveAdmin[targetUid]?.size ?: 0

    fun hasVotedToRemove(targetUid: String, voterUid: String): Boolean =
        votesToRemoveAdmin[targetUid]?.contains(voterUid) == true

    // ─── Utility ───────────────────────────────────────────────────────────

    fun getRole(uid: String): GroupRole = memberRoles[uid] ?: GroupRole.MEMBER
    fun isAdmin(uid: String): Boolean {
        val r = memberRoles[uid] ?: return false
        return r == GroupRole.ADMIN || r == GroupRole.SUPER_ADMIN
    }
    fun isSuperAdmin(uid: String): Boolean = memberRoles[uid] == GroupRole.SUPER_ADMIN

    fun adminApplicationVoteCount(applicant: String): Int =
        adminApplicationVotes[applicant]?.size ?: 0

    fun votesNeededForApplication(applicant: String): Int {
        val eligible = groupMemberNames.filter { it != applicant }
        return (eligible.size / 2) + 1
    }
}