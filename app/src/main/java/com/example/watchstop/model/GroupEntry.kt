package com.example.watchstop.model

import java.time.LocalDateTime

enum class GroupRole(val displayName: String) {
    SUPER_ADMIN("Super Admin"),
    ADMIN("Admin"),
    MEMBER("Member")
}

data class GroupEntry(
    var title: String,
    var eventDateTime: LocalDateTime,
    var description: String,
    val groupMemberNames: MutableList<String> = mutableListOf(),
    val memberRoles: MutableMap<String, GroupRole> = mutableMapOf(),
    val locationSharingEnabled: MutableMap<String, Boolean> = mutableMapOf(),
    val canToggleSharing: MutableMap<String, Boolean> = mutableMapOf(),
    // Usernames who have applied for Admin role
    val adminApplications: MutableSet<String> = mutableSetOf(),
    // Application votes: applicant -> set of voters who approved
    val adminApplicationVotes: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    // Removal votes: admin username -> set of voter usernames who voted to remove
    val votesToRemoveAdmin: MutableMap<String, MutableSet<String>> = mutableMapOf()
) {
    /** Copy constructor — deep copies all mutable collections */
    constructor(other: GroupEntry) : this(
        title = other.title,
        eventDateTime = other.eventDateTime,
        description = other.description,
        groupMemberNames = other.groupMemberNames.toMutableList(),
        memberRoles = other.memberRoles.toMutableMap(),
        locationSharingEnabled = other.locationSharingEnabled.toMutableMap(),
        canToggleSharing = other.canToggleSharing.toMutableMap(),
        adminApplications = other.adminApplications.toMutableSet(),
        adminApplicationVotes = other.adminApplicationVotes
            .mapValues { it.value.toMutableSet() }.toMutableMap(),
        votesToRemoveAdmin = other.votesToRemoveAdmin
            .mapValues { it.value.toMutableSet() }.toMutableMap()
    )

    // ─── Member Management ─────────────────────────────────────────────────

    /**
     * Add a new member with a given role.
     * - SUPER_ADMIN and ADMIN can always toggle their own sharing.
     * - MEMBERs cannot toggle sharing by default (admin controls it for them).
     */
    fun addMember(userName: String, role: GroupRole) {
        if (!groupMemberNames.contains(userName)) {
            groupMemberNames.add(userName)
        }
        memberRoles[userName] = role
        locationSharingEnabled[userName] = false
        canToggleSharing[userName] = role != GroupRole.MEMBER
    }

    /**
     * Grant or revoke the ability for a member to toggle their own location sharing.
     * Only an Admin/SuperAdmin should call this.
     */
    fun setCanToggleSharing(userName: String, allowed: Boolean) {
        canToggleSharing[userName] = allowed
    }

    // ─── Location Sharing ──────────────────────────────────────────────────

    /**
     * Toggle location sharing for [userName].
     * Only succeeds if that user has permission (canToggleSharing == true).
     */
    fun toggleSharing(userName: String): Boolean {
        if (canToggleSharing[userName] != true) return false
        val current = locationSharingEnabled[userName] ?: false
        locationSharingEnabled[userName] = !current
        return true
    }

    /** Admin/SuperAdmin can forcibly set any member's sharing state. */
    fun setSharing(userName: String, enabled: Boolean) {
        locationSharingEnabled[userName] = enabled
    }

    // ─── Admin Applications ────────────────────────────────────────────────

    /**
     * A MEMBER applies for admin.
     * Returns false if they are already admin/super-admin or already applied.
     */
    fun applyForAdmin(userName: String): Boolean {
        val role = memberRoles[userName] ?: return false
        if (role != GroupRole.MEMBER) return false
        if (adminApplications.contains(userName)) return false
        adminApplications.add(userName)
        adminApplicationVotes[userName] = mutableSetOf()
        return true
    }

    /**
     * A voter approves an admin application.
     * Application auto-approved if:
     *   - A SUPER_ADMIN approves, OR
     *   - A majority of non-applicant members have voted yes.
     * Returns true if the applicant was promoted.
     */
    fun voteForAdminApplication(applicant: String, voterName: String): Boolean {
        if (!adminApplications.contains(applicant)) return false
        val voterRole = memberRoles[voterName] ?: return false

        val votes = adminApplicationVotes.getOrPut(applicant) { mutableSetOf() }
        votes.add(voterName)

        // SuperAdmin single-handedly approves
        if (voterRole == GroupRole.SUPER_ADMIN) {
            promoteToAdmin(applicant)
            return true
        }

        // Majority vote among all members except the applicant
        val eligibleVoters = groupMemberNames.filter { it != applicant }
        if (votes.size > eligibleVoters.size / 2) {
            promoteToAdmin(applicant)
            return true
        }
        return false
    }

    private fun promoteToAdmin(userName: String) {
        memberRoles[userName] = GroupRole.ADMIN
        canToggleSharing[userName] = true
        adminApplications.remove(userName)
        adminApplicationVotes.remove(userName)
    }

    // ─── Admin Removal Voting ──────────────────────────────────────────────

    /**
     * Cast a vote to remove [targetAdmin] from admin role.
     * - SUPER_ADMIN cannot be removed this way.
     * - The voter must not be the target.
     * - Auto-removes if a majority of non-target members vote to remove.
     * Returns true if the admin was demoted.
     */
    fun voteToRemoveAdmin(targetAdmin: String, voterName: String): Boolean {
        val targetRole = memberRoles[targetAdmin] ?: return false
        if (targetRole == GroupRole.SUPER_ADMIN) return false // Immovable
        if (targetRole != GroupRole.ADMIN) return false
        if (targetAdmin == voterName) return false

        val votes = votesToRemoveAdmin.getOrPut(targetAdmin) { mutableSetOf() }
        votes.add(voterName)

        // Majority of all other members
        val eligibleVoters = groupMemberNames.filter { it != targetAdmin }
        if (votes.size > eligibleVoters.size / 2) {
            memberRoles[targetAdmin] = GroupRole.MEMBER
            canToggleSharing[targetAdmin] = false
            votesToRemoveAdmin.remove(targetAdmin)
            return true
        }
        return false
    }

    /** How many votes are needed to remove [targetAdmin]. */
    fun votesNeededToRemove(targetAdmin: String): Int {
        val eligibleVoters = groupMemberNames.filter { it != targetAdmin }
        return (eligibleVoters.size / 2) + 1
    }

    fun voteCountToRemove(targetAdmin: String): Int =
        votesToRemoveAdmin[targetAdmin]?.size ?: 0

    fun hasVotedToRemove(targetAdmin: String, voterName: String): Boolean =
        votesToRemoveAdmin[targetAdmin]?.contains(voterName) == true

    // ─── Utility ───────────────────────────────────────────────────────────

    fun getRole(userName: String): GroupRole =
        memberRoles[userName] ?: GroupRole.MEMBER

    fun isAdmin(userName: String): Boolean {
        val r = memberRoles[userName] ?: return false
        return r == GroupRole.ADMIN || r == GroupRole.SUPER_ADMIN
    }

    fun isSuperAdmin(userName: String): Boolean =
        memberRoles[userName] == GroupRole.SUPER_ADMIN

    fun adminApplicationVoteCount(applicant: String): Int =
        adminApplicationVotes[applicant]?.size ?: 0

    fun votesNeededForApplication(applicant: String): Int {
        val eligible = groupMemberNames.filter { it != applicant }
        return (eligible.size / 2) + 1
    }
}