package crazydude.com.telemetry.manager

import android.content.Context
import crazydude.com.telemetry.R

class PreferenceManager(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val defaultHeadlineColor = context.resources.getColor(R.color.colorHeadline)
    private val defaultPlaneColor = context.resources.getColor(R.color.colorPlane)

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

    fun isHeadingLineEnabled(): Boolean {
        return sharedPreferences.getBoolean("show_heading_line", true)
    }

    fun getHeadLineColor(): Int {
        return sharedPreferences.getInt("headline_color", defaultHeadlineColor)
    }

    fun getPlaneColor(): Int {
        return sharedPreferences.getInt("plane_color", defaultPlaneColor)
    }

    fun isYoutubeChannelShown(): Boolean {
        return sharedPreferences.getBoolean("youtube_shown", false)
    }

    fun setYoutubeShown() {
        sharedPreferences.edit().putBoolean("youtube_shown", true).apply()
    }

    fun getBatteryUnits(): String {
        return sharedPreferences.getString("battery_units", "mAh") ?: "mAh"
    }

    fun usePitotTube(): Boolean {
        return sharedPreferences.getBoolean("use_pitot_tube", false)
    }

    fun showArtificialHorizonView(): Boolean {
        return sharedPreferences.getBoolean("show_artificial_horizon", true)
    }
}