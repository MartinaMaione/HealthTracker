package com.example.health_tracker

import android.content.Context

class AppPreferences(context: Context) {

    private val sharedPreferences =
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)

    fun setLoggedIn(loggedIn: Boolean) {
        sharedPreferences.edit().putBoolean("loggedIn", loggedIn).apply()
    }

    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean("loggedIn", false)
    }
}
