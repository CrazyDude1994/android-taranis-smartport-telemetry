package crazydude.com.telemetry.manager

import android.content.Context
import com.google.android.gms.maps.GoogleMap
import crazydude.com.telemetry.R

class PreferenceManager(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val defaultHeadlineColor = context.resources.getColor(R.color.colorHeadline)
    private val defaultPlaneColor = context.resources.getColor(R.color.colorPlane)
    private val defaultRouteColor = context.resources.getColor(R.color.colorRoute)

    companion object {
        val sensors = setOf(
            SensorSetting("Satellites", 1),
            SensorSetting("Battery", 2),
            SensorSetting("Voltage", 3),
            SensorSetting("Amperage", 4),
            SensorSetting("Speed", 0, "bottom"),
            SensorSetting("Distance", 1, "bottom"),
            SensorSetting("TraveledDistance", 2, "bottom"),
            SensorSetting("Altitude", 3, "bottom"),
            SensorSetting("Phone Battery", 5),
            SensorSetting("RC Channels", 4, "bottom", false),
            SensorSetting("Rssi", 0 ),
            SensorSetting("Uplink SNR", 6, "top", false ),
            SensorSetting("Downlink SNR", 7, "top", false ),
            SensorSetting("Uplink LQ", 8, "top", false ),
            SensorSetting("Downlink LQ", 9, "top", false ),
            SensorSetting("ELRS Rate", 10, "top", false ),
            SensorSetting("Active Antena", 11, "top", false ),
            SensorSetting("Uplink Power", 12, "top", false ),
            SensorSetting("Uplink Antena 1 RSSI, dbm", 13, "top", false ),
            SensorSetting("Uplink Antena 2 RSSI, dbm", 14, "top", false ),
            SensorSetting("Downlink Antena RSSI, dbm", 15, "top", false ),
            SensorSetting("AirSpeed", 5, "bottom", false),
            SensorSetting("Vertical Speed", 6, "bottom", false),
            SensorSetting("Cell Voltage", 16, "top", false ),
            SensorSetting("Altitude above MSL", 7, "bottom", false),
            SensorSetting("Throttle", 8, "bottom", false),
            SensorSetting("Telemetry rate", 17, "top", false )
        )
    }

    fun isLoggingEnabled(): Boolean {
        return sharedPreferences.getBoolean("logging_enabled", true)
    }

    fun isCSVLoggingEnabled(): Boolean {
        return sharedPreferences.getBoolean("csv_logging_enabled", true)
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

    fun getReportVoltage(): String {
        return sharedPreferences.getString("report_voltage", "Battery") ?: "Battery"
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

    fun getUsbSerialBaudrate() : Int {
       return sharedPreferences.getString("usb_serial_baudrate", "57600")?.toInt() ?: 57600
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

    fun getConnectionVoiceMessagesEnabled(): Boolean {
        return sharedPreferences.getBoolean("connection_voice_messages", true)
    }

    fun getMaxRoutePoints() : Int {
        return sharedPreferences.getString("route_max_points", "-1")?.toInt() ?: -1
    }

}