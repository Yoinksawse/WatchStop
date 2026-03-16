package com.example.watchstop.data

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.watchstop.model.GroupEntry
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

    private var _groupId: String = ""  // ADD THIS

    var activated: Boolean = false

    @RequiresApi(Build.VERSION_CODES.O)
    fun getCurrentGroupEntry(): GroupEntry = _groupEntry

    fun getCurrentGroupId(): String = _groupId  // ADD THIS

    @RequiresApi(Build.VERSION_CODES.O)
    fun loadCurrentGroupEntry(other: GroupEntry, groupId: String = "") {  // ADD groupId param
        _groupEntry = GroupEntry(other)
        _groupId = groupId  // ADD THIS
        activated = true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun clear() {
        _groupEntry = GroupEntry(
            title = "",
            eventDateTime = LocalDateTime.now(),
            description = ""
        )
        _groupId = ""  // ADD THIS
        activated = false
    }
}