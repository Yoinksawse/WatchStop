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
    val groupMemberNames: MutableList<String> = mutableListOf(),
    val memberRoles: MutableMap<String, GroupRole> = mutableMapOf(),
    val locationSharingEnabled: MutableMap<String, Boolean> = mutableMapOf(),
    val canToggleSharing: MutableMap<String, Boolean> = mutableMapOf(),
    val tripStatus: MutableMap<String, TripStatus> = mutableMapOf(),
    val adminApplications: MutableSet<String> = mutableSetOf(),
    val adminApplicationVotes: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val votesToRemoveAdmin: MutableMap<String, MutableSet<String>> = mutableMapOf()
) {
    /** Deep-copy constructor */
    constructor(other: GroupEntry) : this(
        title = other.title,
        eventDateTime = other.eventDateTime,
        description = other.description,
        groupMemberNames = other.groupMemberNames.toMutableList(),
        memberRoles = other.memberRoles.toMutableMap(),
        locationSharingEnabled = other.locationSharingEnabled.toMutableMap(),
        canToggleSharing = other.canToggleSharing.toMutableMap(),
        tripStatus = other.tripStatus.toMutableMap(),
        adminApplications = other.adminApplications.toMutableSet(),
        adminApplicationVotes = other.adminApplicationVotes
            .mapValues { it.value.toMutableSet() }.toMutableMap(),
        votesToRemoveAdmin = other.votesToRemoveAdmin
            .mapValues { it.value.toMutableSet() }.toMutableMap()
    )

    // ─── Member Management ─────────────────────────────────────────────────

    fun addMember(uid: String, role: GroupRole) {
        if (!groupMemberNames.contains(uid)) groupMemberNames.add(uid)
        memberRoles[uid] = role
        locationSharingEnabled[uid] = false
        canToggleSharing[uid] = role != GroupRole.MEMBER
        tripStatus[uid] = TripStatus.INACTIVE
    }

    fun setCanToggleSharing(uid: String, allowed: Boolean) {
        canToggleSharing[uid] = allowed
    }

    // ─── Location Sharing ──────────────────────────────────────────────────

    fun toggleSharing(uid: String): Boolean {
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

        if (voterRole == GroupRole.SUPER_ADMIN) {
            promoteToAdmin(applicant)
            return true
        }

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
        if (targetRole == GroupRole.SUPER_ADMIN) return false
        if (targetRole != GroupRole.ADMIN) return false
        if (targetUid == voterUid) return false

        val votes = votesToRemoveAdmin.getOrPut(targetUid) { mutableSetOf() }
        votes.add(voterUid)

        val eligibleVoters = groupMemberNames.filter { it != targetUid }
        if (votes.size > eligibleVoters.size / 2) {
            memberRoles[targetUid] = GroupRole.MEMBER
            canToggleSharing[targetUid] = false
            votesToRemoveAdmin.remove(targetUid)
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