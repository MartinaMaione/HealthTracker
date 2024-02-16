package com.example.health_tracker

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object SaveSharedPreference {
    const val PREF_USER_NAME = "username"
    const val PREF_LOGIN_STATE = "login_state"

    fun getSharedPreferences(ctx: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(ctx)
    }

    fun setUserName(ctx: Context, userName: String) {
        val editor = getSharedPreferences(ctx).edit()
        editor.putString(PREF_USER_NAME, userName)
        editor.apply()
    }

    fun getUserName(ctx: Context): String {
        return getSharedPreferences(ctx).getString(PREF_USER_NAME, "") ?: ""
    }
    fun setLoginState(ctx: Context, isLoggedIn: Boolean) {
        val editor = getSharedPreferences(ctx).edit()
        editor.putBoolean(PREF_LOGIN_STATE, isLoggedIn)
        editor.apply()
    }

    fun getLoginState(ctx: Context): Boolean {
        return getSharedPreferences(ctx).getBoolean(PREF_LOGIN_STATE, false)
    }
}
