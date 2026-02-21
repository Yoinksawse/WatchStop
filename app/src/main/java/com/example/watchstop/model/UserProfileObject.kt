package com.example.watchstop.model

object UserProfileObject {
    var darkmode: Boolean = false

    fun inDarkMode(): Boolean {
        return darkmode
    }
    fun setDarkMode(tf: Boolean): Unit {
        darkmode = tf
    }

}