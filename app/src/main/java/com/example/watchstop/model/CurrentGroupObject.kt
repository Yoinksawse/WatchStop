package com.example.watchstop.model

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDateTime

object CurrentGroupObject {
    @RequiresApi(Build.VERSION_CODES.O)
    private val groupEntry = GroupEntry(
        title = "",
        eventDateTime = LocalDateTime.now(),
        description = ""
    )
    var activated: Boolean = false;

    var title: String
        get() = groupEntry.title
        set(value) {
            groupEntry.title = value
            activated = true
        }
    var eventDateTime: LocalDateTime
        get() = groupEntry.eventDateTime
        set(value) {
            groupEntry.eventDateTime = value
            activated = true
        }
    var description: String
        get() = groupEntry.description
        set(value) {
            groupEntry.description = value
            activated = true
        }

    fun getCurrentAssignmentEntry(): GroupEntry {
        return groupEntry
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun clearCurrentAssignmentEntry() {
        groupEntry.title = ""
        groupEntry.eventDateTime = LocalDateTime.now()
        groupEntry.description = ""
    }

    fun loadCurrentAssignmentEntry(otherAssignmentEntry: GroupEntry) {
        groupEntry.title = otherAssignmentEntry.title
        groupEntry.eventDateTime = otherAssignmentEntry.eventDateTime
        groupEntry.description = otherAssignmentEntry.description
    }
}