package crazydude.com.telemetry.manager

import android.content.Context
import com.google.android.gms.maps.GoogleMap
import crazydude.com.telemetry.R

class PreferenceManager(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val defaultHeadlineColor = context.resources.getColor(R.color.colorHeadline)
    private val defaultPlaneColor = context.resources.getColor(R.color.colorPlane)
    private val defaultRouteColor = context.resources.getColor(R.color.colorPlane)

    companion object {
        val sensors = setOf(
            SensorSetting("Satellites", 0),
            SensorSetting("Battery", 1),
            SensorSetting("Voltage", 2),
            SensorSetting("Amperage", 3),
            SensorSetting("Speed", 0, "bottom"),
            SensorSetting("Distance", 1, "bottom"),
            SensorSetting("Altitude", 2, "bottom"),
            SensorSetting("Phone Battery", 4),
            SensorSetting("RC Channels", 3, "bottom", false)
        )
    }

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

    fun getMapType(): Int {
        return sharedPreferences.getInt("map_type", GoogleMap.MAP_TYPE_NORMAL)
    }

    fun setMapType(mapType: Int) {
        sharedPreferences.edit().putInt("map_type", mapType).apply()
    }

    fun isSendDataEnabled(): Boolean {
        return sharedPreferences.getBoolean("send_telemetry_data", false)
    }

    fun isSendDataDialogShown(): Boolean {
        return sharedPreferences.contains("send_telemetry_data")
    }

    fun getModel(): String {
        return sharedPreferences.getString("model", "") ?: ""
    }

    fun getCallsign(): String {
        return sharedPreferences.getString("callsign", "") ?: ""
    }

    fun setTelemetrySendingEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("send_telemetry_data", enabled).apply()
    }

    fun getRouteColor(): Int {
        return sharedPreferences.getInt("route_color", defaultRouteColor)
    }

    fun getSensorsSettings(): List<SensorSetting> {
        return sensors.map {
            SensorSetting(
                it.name,
                sharedPreferences.getInt(it.name + "_index", it.index),
                sharedPreferences.getString(it.name + "_position", it.position),
                sharedPreferences.getBoolean(it.name + "_shown", it.shown)
            )
        }
    }

    fun setSensorsSettings(data: List<SensorSetting>) {
        val commit = sharedPreferences.edit()
        data.forEach {
            commit.putInt(it.name + "_index", it.index)
            commit.putString(it.name + "_position", it.position)
            commit.putBoolean(it.name + "_shown", it.shown)
        }
        commit.apply()
    }

    data class SensorSetting(
        val name: String,
        val index: Int,
        val position: String = "top",
        val shown: Boolean = true
    )

    //0 - map
    //1 - video+map
    //2- video
    fun getMainLayout(): Int {
        return sharedPreferences.getInt("main_layout", 0)
    }

    fun setMainLayout(layout : Int) {
        sharedPreferences.edit().putInt("main_layout", layout).apply()
    }

    fun isFullscreenWindow(): Boolean {
        return sharedPreferences.getBoolean("fullscreen_window", false)
    }

    fun setFullscreenWindow( state: Boolean ) {
        sharedPreferences.edit().putBoolean("fullscreen_window", state).apply()
    }

    fun getScreenOrientationLock() : String {
        return sharedPreferences.getString("screen_orientation_lock", "No lock") ?: "No lock"
    }

    fun getCompressionQuality() : String {
        return sharedPreferences.getString("compression_quality", "Normal") ?: "Normal"
    }

}