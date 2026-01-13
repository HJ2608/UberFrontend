package com.example.uberfrontend.session

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

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        token = prefs.getString(KEY_TOKEN, null)
        userId = prefs.getInt(KEY_USER_ID, -1).takeIf { it != -1 }
        firstName = prefs.getString(KEY_FIRST_NAME, null)
        lastName = prefs.getString(KEY_LAST_NAME, null)
        mobile = prefs.getString(KEY_MOBILE, null)
        email = prefs.getString(KEY_EMAIL, null)
    }

    fun saveLogin(
        context: Context,
        jwt: String,
        userId: Int,
        firstName: String,
        lastName: String,
        mobile: String,
        email: String
    ) {
        token = jwt
        this.userId = userId
        this.firstName = firstName
        this.lastName = lastName
        this.mobile = mobile
        this.email = email

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TOKEN, jwt)
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_FIRST_NAME, firstName)
            .putString(KEY_LAST_NAME, lastName)
            .putString(KEY_MOBILE, mobile)
            .putString(KEY_EMAIL, email)
            .apply()
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
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}