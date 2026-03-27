package com.example.watchstop.data

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.watchstop.model.GroupEntry
import java.time.LocalDateTime

// holds the group currently being edited in EditGroupActivity
// bridges GroupCard (launcher) and EditGroupActivity (editor)
object CurrentGroupObject {

    @RequiresApi(Build.VERSION_CODES.O)
    private var _groupEntry: GroupEntry = GroupEntry(
        title = "",
        eventDateTime = LocalDateTime.now(),
        description = ""
    )

    private var _groupId: String = ""

    var activated: Boolean = false

    @RequiresApi(Build.VERSION_CODES.O)
    fun getCurrentGroupEntry(): GroupEntry = _groupEntry

    fun getCurrentGroupId(): String = _groupId

    @RequiresApi(Build.VERSION_CODES.O)
    fun loadCurrentGroupEntry(other: GroupEntry, groupId: String = "") {
        _groupEntry = GroupEntry(other)
        _groupId = groupId
        activated = true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun clear() {
        _groupEntry = GroupEntry(
            title = "",
            eventDateTime = LocalDateTime.now(),
            description = ""
        )
        _groupId = ""
        activated = false
    }
}