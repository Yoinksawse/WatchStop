package com.example.watchstop.model

import java.time.LocalDateTime

object CurrentAssignmentObject {
    private val assignmentEntry = GroupEntry(
        title = "",
        dueDate = LocalDateTime.now(),
        description = ""
    )
    var activated: Boolean = false;

    var title: String
        get() = assignmentEntry.title
        set(value) {
            assignmentEntry.title = value
            activated = true
        }
    var dueDate: LocalDateTime
        get() = assignmentEntry.dueDate
        set(value) {
            assignmentEntry.dueDate = value
            activated = true
        }
    var description: String
        get() = assignmentEntry.description
        set(value) {
            assignmentEntry.description = value
            activated = true
        }

    fun getCurrentAssignmentEntry(): GroupEntry {
        return assignmentEntry
    }

    fun clearCurrentAssignmentEntry() {
        assignmentEntry.title = ""
        assignmentEntry.dueDate = LocalDateTime.now()
        assignmentEntry.description = ""
    }

    fun loadCurrentAssignmentEntry(otherAssignmentEntry: GroupEntry) {
        assignmentEntry.title = otherAssignmentEntry.title
        assignmentEntry.dueDate = otherAssignmentEntry.dueDate
        assignmentEntry.description = otherAssignmentEntry.description
    }
}