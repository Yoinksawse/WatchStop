package com.example.watchstop.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

@Serializable
data class AppSession(
    val currentUser: String
)

@Serializable
data class UserProfileData(
    val userName: String,
    val userPfpReference: String = "",
    val password: String,
    val darkmode: Boolean = false,
)

class UserProfile(
    var userName: String,
    var password: String,
    var userPfpReference: String,
    darkmode: Boolean = true,
) {
    companion object {
        var loggedIn: Boolean = false
        private val json = Json { ignoreUnknownKeys = true }

        fun checkProfileExistsByUsername(context: Context, username: String): Boolean {
            val file = File(context.filesDir, "user_profiles.json")
            if (!file.exists()) return false

            val profiles = try {
                json.decodeFromString(
                    ListSerializer(UserProfileData.serializer()),
                    file.readText()
                )
            } catch (e: Exception) {
                emptyList<UserProfileData>()
            }

            return profiles.any { it.userName == username }
        }

        fun checkPasswordByUsername(context: Context, username: String, password: String): Boolean {
            val file = File(context.filesDir, "user_profiles.json")
            if (!file.exists()) return false

            val profiles = try {
                json.decodeFromString(
                    ListSerializer(UserProfileData.serializer()),
                    file.readText()
                )
            } catch (e: Exception) {
                emptyList<UserProfileData>()
            }

            val user = profiles.find { it.userName == username } ?: return false

            return user.password == password
        }
    }

    var darkmode by mutableStateOf(darkmode)

    fun toData(): UserProfileData = UserProfileData(
        userName = userName,
        userPfpReference = userPfpReference,
        darkmode = darkmode,
        password = password
    )

    fun applyData(data: UserProfileData) {
        userName = data.userName
        userPfpReference = data.userPfpReference
        darkmode = data.darkmode
        password = data.password
    }
}
