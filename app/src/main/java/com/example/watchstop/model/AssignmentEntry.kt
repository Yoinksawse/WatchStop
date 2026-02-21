package com.example.watchstop.model

import java.io.Serializable
import java.time.LocalDateTime


data class AssignmentEntry (
    var title: String,
    var dueDate: LocalDateTime,
    var description: String,
) {
    constructor(other: AssignmentEntry) : this(
        title = other.title,
        dueDate = other.dueDate,
        description = other.description
    )

    fun loadAssignmentEntry(otherAssignmentEntry: AssignmentEntry) {
        title = otherAssignmentEntry.title
        description = otherAssignmentEntry.description
        dueDate = otherAssignmentEntry.dueDate
    }
}