package com.example.watchstop.model

import com.example.watchstop.data.GroupsDatabase
import java.time.LocalDateTime


data class GroupEntry (
    var title: String,
    var dueDate: LocalDateTime,
    var description: String,
    val groupMemberIds: MutableList<Int> = mutableListOf(),
    //TODO: add polyline points storage data structure
    ) {
    constructor(other: GroupEntry) : this(
        title = other.title,
        dueDate = other.dueDate,
        description = other.description,
        groupMemberIds = other.groupMemberIds
    )

    fun loadAssignmentEntry(otherAssignmentEntry: GroupEntry) {
        title = otherAssignmentEntry.title
        description = otherAssignmentEntry.description
        dueDate = otherAssignmentEntry.dueDate
    }

    init {
        GroupsDatabase.addGroup(this)
    }
}