package com.bakhawone.thesis_bakhawone

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

object SessionManager {
    private const val PREFS_NAME = "session_prefs"
    private const val KEY_SESSION_ID = "session_id"

    /**
     * Get or create a persistent session ID.
     * This ID persists across app launches until the app is uninstalled.
     */
    fun getOrCreateSessionId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingId = prefs.getString(KEY_SESSION_ID, null)
        
        return if (existingId != null) {
            existingId
        } else {
            // Generate new UUID and save it
            val newId = "session_${UUID.randomUUID().toString().replace("-", "")}"
            prefs.edit().putString(KEY_SESSION_ID, newId).apply()
            newId
        }
    }

    /**
     * Get the current session ID without creating a new one.
     * Returns null if no session exists.
     */
    fun getSessionId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SESSION_ID, null)
    }

    /**
     * Clear the session ID (useful for testing or logout scenarios).
     * Note: This will create a new session on next app launch.
     */
    fun clearSessionId(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SESSION_ID).apply()
    }
}

