package com.example.watchstop.data

object UserProfileObject {

    var darkmode: Boolean = false

    // groupId -> role (0: superadmin, 1: admin, 2: user)
    private val groupIdToRoleMap: MutableMap<Int, Int> = mutableMapOf()

    //group operations
    fun addGroup(groupId: Int, role: Int) {
        require(role in 0..2) { "Invalid role value" }
        groupIdToRoleMap[groupId] = role
    }

    fun removeGroup(groupId: Int) {
        groupIdToRoleMap.remove(groupId)
    }

    //role methods (read-only)
    fun getRole(groupId: Int): Int? {
        return groupIdToRoleMap[groupId]
    }

    fun getGroupIdToRoleMap(): Map<Int, Int> {
        return groupIdToRoleMap.toMap() // returns immutable copy
    }

    fun isSuperAdmin(groupId: Int): Boolean {
        return groupIdToRoleMap[groupId] == 0
    }

    fun isAdmin(groupId: Int): Boolean {
        return groupIdToRoleMap[groupId] == 1
    }

    fun isMember(groupId: Int): Boolean {
        return groupIdToRoleMap[groupId] == 2
    }

    //dark mode
    fun inDarkMode(): Boolean {
        return darkmode
    }

    fun setDarkMode(tf: Boolean) {
        darkmode = tf
    }
}