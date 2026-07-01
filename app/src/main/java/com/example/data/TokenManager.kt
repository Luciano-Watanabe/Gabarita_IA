package com.example.data

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_GEMINI_TOKEN = "gemini_api_key"
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_GEMINI_TOKEN, token.trim()).apply()
    }

    fun getToken(): String? {
        val token = prefs.getString(KEY_GEMINI_TOKEN, null)
        return if (token.isNullOrBlank()) null else token
    }
    
    fun hasToken(): Boolean {
        return !getToken().isNullOrBlank()
    }
}
