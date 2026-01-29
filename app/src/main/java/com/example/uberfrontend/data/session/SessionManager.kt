package com.example.uberfrontend.data.session

import android.content.Context

object SessionManager {

    private const val PREF_NAME = "session_prefs"
    private const val KEY_TOKEN = "jwt"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_FIRST_NAME = "first_name"
    private const val KEY_LAST_NAME = "last_name"
    private const val KEY_MOBILE = "mobile"
    private const val KEY_EMAIL = "email"
    private const val PREFS = "uber_prefs"
    private const val KEY_ROLE = "role"

    private const val KEY_DRIVER_ID = "driver_id"

    var token: String? = null
        private set

    var userId: Int? = null
        private set

    var firstName: String? = null
        private set

    var lastName: String? = null
        private set

    var mobile: String? = null
        private set

    var email: String? = null
        private set

    var role: String? = null
        private set

    var driverId: Int? = null
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        token = prefs.getString(KEY_TOKEN, null)
        userId = prefs.getInt(KEY_USER_ID, -1).takeIf { it != -1 }
        firstName = prefs.getString(KEY_FIRST_NAME, null)
        lastName = prefs.getString(KEY_LAST_NAME, null)
        mobile = prefs.getString(KEY_MOBILE, null)
        email = prefs.getString(KEY_EMAIL, null)
        role = prefs.getString(KEY_ROLE,null)
        driverId = prefs.getInt(KEY_DRIVER_ID, -1).takeIf { it != -1 }
    }

    fun saveLogin(
        context: Context,
        jwt: String,
        userId: Int,
        firstName: String,
        lastName: String,
        mobile: String,
        email: String,
        role: String,
        driverId: Int? = null
    ) {
        token = jwt
        this.userId = userId
        this.firstName = firstName
        this.lastName = lastName
        this.mobile = mobile
        this.email = email
        this.role = role
        this.driverId = driverId

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(KEY_TOKEN, jwt)
        editor.putInt(KEY_USER_ID, userId)
        editor.putString(KEY_FIRST_NAME, firstName)
        editor.putString(KEY_LAST_NAME, lastName)
        editor.putString(KEY_MOBILE, mobile)
        editor.putString(KEY_EMAIL, email)
        editor.putString(KEY_ROLE, role)

        if (driverId != null) editor.putInt(KEY_DRIVER_ID, driverId)
        else editor.remove(KEY_DRIVER_ID)

        editor.apply()
    }

    fun saveRole(context: Context, role: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun getRole(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ROLE, null)

    fun clear(context: Context) {
        token = null
        userId = null
        firstName = null
        lastName = null
        mobile = null
        email = null
        role=null
        driverId = null
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun saveDriverId(context: Context, driverId: Int) {
        this.driverId = driverId
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DRIVER_ID, driverId)
            .apply()
    }


}