package com.example.watchstop.model

import com.example.watchstop.data.GroupsDatabase
import java.time.LocalDateTime

enum class GroupRole(val value: Int) {
    SUPER_ADMIN(0),
    ADMIN(1),
    MEMBER(2)
}

data class GroupEntry (
    var title: String,
    var eventDateTime: LocalDateTime,
    var description: String,
    val groupMemberNames: MutableList<String> = mutableListOf(),
    val memberRoles: MutableMap<String, GroupRole> = mutableMapOf(),
    val locationSharingEnabled: MutableMap<String, Boolean> = mutableMapOf(),
    val canToggleSharing: MutableMap<String, Boolean> = mutableMapOf(),
    val adminApplications: MutableSet<String> = mutableSetOf(), // Usernames applying for admin
    val votesToRemoveAdmin: MutableMap<String, MutableSet<String>> = mutableMapOf() // Admin Username -> Set of voter Usernames
    ) {
    constructor(other: GroupEntry) : this(
        title = other.title,
        eventDateTime = other.eventDateTime,
        description = other.description,
        groupMemberNames = other.groupMemberNames.toMutableList(),
        memberRoles = other.memberRoles.toMutableMap(),
        locationSharingEnabled = other.locationSharingEnabled.toMutableMap(),
        canToggleSharing = other.canToggleSharing.toMutableMap(),
        adminApplications = other.adminApplications.toMutableSet(),
        votesToRemoveAdmin = other.votesToRemoveAdmin.mapValues { it.value.toMutableSet() }.toMutableMap()
    )

    fun loadAssignmentEntry(otherAssignmentEntry: GroupEntry) {
        title = otherAssignmentEntry.title
        description = otherAssignmentEntry.description
        eventDateTime = otherAssignmentEntry.eventDateTime
    }

    init {
        GroupsDatabase.addGroup(this)
    }

    fun addMember(userName: String, role: GroupRole) {
        if (!groupMemberNames.contains(userName)) {
            groupMemberNames.add(userName)
        }
        memberRoles[userName] = role
        locationSharingEnabled[userName] = false
        // Members might not be able to toggle sharing if hierarchy dictates
        canToggleSharing[userName] = (role != GroupRole.MEMBER) 
    }
}