package crazydude.com.telemetry.protocol.decoder

import com.google.android.gms.maps.model.LatLng
import crazydude.com.telemetry.protocol.Protocol

abstract class DataDecoder(protected val listener: Listener) {

    companion object {

        enum class FlyMode {
            ACRO, HORIZON, ANGLE, FAILSAFE, RTH, WAYPOINT, MANUAL, CRUISE, HOLD, HOME_RESET, CRUISE3D, ALTHOLD, ERROR,
            WAIT,
            AUTONOMOUS
        }

        open class DefaultDecodeListener: Listener {
            override fun onConnectionFailed() {

            }

            override fun onFuelData(fuel: Int) {
            }

            override fun onConnected() {
            }

            override fun onGPSData(latitude: Double, longitude: Double) {
            }

            override fun onGPSData(list: List<LatLng>, addToEnd: Boolean) {
            }

            override fun onVBATData(voltage: Float) {
            }

            override fun onCellVoltageData(voltage: Float) {
            }

            override fun onCurrentData(current: Float) {
            }

            override fun onHeadingData(heading: Float) {
            }

            override fun onRSSIData(rssi: Int) {
            }

            override fun onDisconnected() {
            }

            override fun onGPSState(satellites: Int, gpsFix: Boolean) {
            }

            override fun onVSpeedData(vspeed: Float) {
            }

            override fun onAltitudeData(altitude: Float) {
            }

            override fun onGPSAltitudeData(altitude: Float) {
            }

            override fun onDistanceData(distance: Int) {
            }

            override fun onRollData(rollAngle: Float) {
            }

            override fun onPitchData(pitchAngle: Float) {
            }

            override fun onGSpeedData(speed: Float) {
            }

            override fun onFlyModeData(
                armed: Boolean,
                heading: Boolean,
                firstFlightMode: FlyMode?,
                secondFlightMode: FlyMode?
            ) {
            }

            override fun onAirSpeed(speed: Float) {
            }

            override fun onSuccessDecode() {
            }
        }

    }

    abstract fun decodeData(data: Protocol.Companion.TelemetryData)

    interface Listener {
        fun onConnectionFailed()
        fun onFuelData(fuel: Int)
        fun onConnected()
        fun onGPSData(latitude: Double, longitude: Double)
        fun onGPSData(list: List<LatLng>, addToEnd: Boolean)
        fun onVBATData(voltage: Float)
        fun onCellVoltageData(voltage: Float)
        fun onCurrentData(current: Float)
        fun onHeadingData(heading: Float)
        fun onRSSIData(rssi: Int)
        fun onDisconnected()
        fun onGPSState(satellites: Int, gpsFix: Boolean)
        fun onVSpeedData(vspeed: Float)
        fun onAltitudeData(altitude: Float)
        fun onGPSAltitudeData(altitude: Float)
        fun onDistanceData(distance: Int)
        fun onRollData(rollAngle: Float)
        fun onPitchData(pitchAngle: Float)
        fun onGSpeedData(speed: Float)
        fun onFlyModeData(
            armed: Boolean,
            heading: Boolean,
            firstFlightMode: FlyMode?,
            secondFlightMode: FlyMode? = null
        )

        fun onAirSpeed(speed: Float)
        fun onSuccessDecode()
    }

}