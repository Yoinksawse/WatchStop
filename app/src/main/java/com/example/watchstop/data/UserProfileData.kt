package com.example.watchstop.data

data class UserProfileData(
    val userName: String,
    val userPfpReference: String = "",
    val darkmode: Boolean = false,
)