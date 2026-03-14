package com.example.watchstop.model

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDateTime

/**
 * Singleton that holds the group currently being edited in EditGroupActivity.
 * Acts as a bridge between GroupCard (launcher) and EditGroupActivity (editor).
 */
object CurrentGroupObject {

    @RequiresApi(Build.VERSION_CODES.O)
    private var _groupEntry: GroupEntry = GroupEntry(
        title = "",
        eventDateTime = LocalDateTime.now(),
        description = ""
    )

    var activated: Boolean = false

    @RequiresApi(Build.VERSION_CODES.O)
    fun getCurrentGroupEntry(): GroupEntry = _groupEntry

    /**
     * Load a group entry for editing. Performs a full deep copy so the
     * original list in GroupsScreen is not mutated until Save is pressed.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun loadCurrentGroupEntry(other: GroupEntry) {
        _groupEntry = GroupEntry(other) // deep copy via copy constructor
        activated = true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun clear() {
        _groupEntry = GroupEntry(
            title = "",
            eventDateTime = LocalDateTime.now(),
            description = ""
        )
        activated = false
    }
}