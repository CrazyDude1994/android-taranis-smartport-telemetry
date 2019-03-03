package crazydude.com.telemetry.manager

import android.content.Context

class PreferenceManager(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun isLoggingEnabled(): Boolean {
        return sharedPreferences.getBoolean("logging_enabled", true)
    }

    fun isLoggingSet(): Boolean {
        return sharedPreferences.contains("logging_enabled")
    }

    fun setLoggingEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("logging_enabled", enabled)
            .apply()
    }
}