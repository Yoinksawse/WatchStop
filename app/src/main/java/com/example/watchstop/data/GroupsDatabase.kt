package com.example.watchstop.data

import com.example.watchstop.model.GroupEntry

object GroupsDatabase {
    private val groups = mutableListOf<GroupEntry>()
    private val idToGroupMap = mutableMapOf<Int, GroupEntry>()
    private val groupToIdMap = mutableMapOf<GroupEntry, Int>()

    fun getIdByGroup(groupEntry: GroupEntry): Int? {
        return groupToIdMap[groupEntry]
    }

    fun getGroupById(id: Int): GroupEntry? {
        return idToGroupMap[id]
    }

    fun addGroup(groupEntry: GroupEntry) {
        groups.add(groupEntry)
        val id = groups.size
        idToGroupMap[id] = groupEntry
        groupToIdMap[groupEntry] = id
    }

    fun deleteGroup(id: Int) {
        groups.remove(idToGroupMap[id])
        idToGroupMap.remove(id)
        groupToIdMap.remove(idToGroupMap[id])
    }
}