package com.example.watchstop.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import java.io.File

const val GUEST_USERNAME = "Guest"
const val DEFAULT_PFP = "defaultpfp"

object UserProfileObject {

    var userName: String by mutableStateOf(GUEST_USERNAME)
    var password: String by mutableStateOf("")
    var userPfpReference: String by mutableStateOf(DEFAULT_PFP)
    var darkmode: Boolean by mutableStateOf(true)

    // groupId -> role (0: superadmin, 1: admin, 2: user)
    private val groupIdToRoleMap: MutableMap<Int, Int> = mutableMapOf()

    private val json = Json { ignoreUnknownKeys = true }

    fun loadUserProfile(context: Context, username: String) {
        val file = File(context.filesDir, "user_profiles.json")
        if (!file.exists()) return

        val profiles = try {
            json.decodeFromString<List<UserProfileData>>(file.readText())
        } catch (e: Exception) {
            emptyList()
        }

        val profile = profiles.find { it.userName == username }
        profile?.let {
            this.userName = it.userName
            this.password = it.password
            this.userPfpReference = it.userPfpReference
            this.darkmode = it.darkmode
        }
    }

    fun saveUserProfile(context: Context) {
        if (userName == GUEST_USERNAME) return

        val file = File(context.filesDir, "user_profiles.json")
        val profiles = if (file.exists()) {
            try {
                json.decodeFromString<List<UserProfileData>>(file.readText()).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }

        val index = profiles.indexOfFirst { it.userName == userName }
        val currentData = UserProfileData(userName, userPfpReference, password, darkmode)
        
        if (index != -1) {
            profiles[index] = currentData
        } else {
            profiles.add(currentData)
        }

        file.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(UserProfileData.serializer()), profiles))
    }

    fun saveCurrentUser(context: Context) {
        val file = File(context.filesDir, "current_user.json")
        file.writeText(json.encodeToString(AppSession.serializer(), AppSession(userName)))
    }

    fun loadCurrentUser(context: Context) {
        val file = File(context.filesDir, "current_user.json")
        if (file.exists()) {
            try {
                val session = json.decodeFromString(AppSession.serializer(), file.readText())
                if (session.currentUser != GUEST_USERNAME) {
                    loadUserProfile(context, session.currentUser)
                    UserProfile.loggedIn = true
                }
            } catch (e: Exception) {
                userName = GUEST_USERNAME
            }
        }
    }

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

    // Placeholder for purchased items if needed, or remove if not
    fun getPurchasedItems(): List<DummyProduct> = emptyList()
}

@kotlinx.serialization.Serializable
data class DummyProduct(val name: String)
