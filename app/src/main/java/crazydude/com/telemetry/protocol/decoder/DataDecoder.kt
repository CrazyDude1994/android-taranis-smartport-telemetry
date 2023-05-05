package crazydude.com.telemetry.protocol.decoder

import com.google.android.gms.maps.model.LatLng
import crazydude.com.telemetry.maps.Position
import crazydude.com.telemetry.protocol.Protocol

abstract class DataDecoder(protected val listener: Listener) {

    companion object {

        enum class FlyMode {
            ACRO, HORIZON, ANGLE, FAILSAFE, RTH, WAYPOINT, MANUAL, CRUISE, HOLD, HOME_RESET, CRUISE3D, ALTHOLD, ERROR,
            WAIT, AUTONOMOUS, CIRCLE, STABILIZE, TRAINING, FBWA, FBWB, AUTOTUNE, LOITER, TAKEOFF, AVOID_ADSB, GUIDED,
            INITIALISING, LANDING, MISSION, QSTABILIZE, QHOVER, QLOITER, QLAND, QRTL, QAUTOTUNE, QACRO, RATE
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

            override fun onGPSData(list: List<Position>, addToEnd: Boolean) {
            }

            override fun onVBATData(voltage: Float) {
            }

            override fun onCellVoltageData(voltage: Float) {
            }

            override fun onVBATOrCellData(voltage: Float) {
            }

            override fun onCurrentData(current: Float) {
            }

            override fun onHeadingData(heading: Float) {
            }

            override fun onRSSIData(rssi: Int) {
            }

            override fun onUpLqData(lq: Int) {
            }

            override fun onDnLqData(lq: Int) {
            }

            override fun onElrsModeModeData(mode: Int) {
            }

            override fun onDisconnected() {
            }

            override fun onGPSState(satellites: Int, gpsFix: Boolean) {
            }

            override fun onVSpeedData(vspeed: Float) {
            }

            override fun onThrottleData(throttle: Int) {
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

            override fun onRCChannels(rcChannels:IntArray){

            }


            override fun onAirSpeedData(speed: Float) {
            }

            override fun onStatusText(message:String) {
            }

            override fun onDNSNRData(snr: Int) {
            }

            override fun onUPSNRData(snr: Int) {
            }

            override fun onAntData(activeAntena: Int) {
            }

            override fun onPowerData(power: Int) {
            }

            override fun onRssiDbm1Data(rssi: Int) {
            }

            override fun onRssiDbm2Data(rssi: Int) {
            }

            override fun onRssiDbmdData(rssi: Int) {
            }

            override fun onTelemetryByte() {
            }

            override fun onSuccessDecode() {
            }
        }

    }

    open fun restart() {

    }

    abstract fun decodeData(data: Protocol.Companion.TelemetryData)

    fun onTelemetryByte() {
        this.listener.onTelemetryByte();
    }

    interface Listener {
        fun onConnectionFailed()
        fun onFuelData(fuel: Int)
        fun onConnected()
        fun onGPSData(latitude: Double, longitude: Double)
        fun onGPSData(list: List<Position>, addToEnd: Boolean)
        fun onVBATData(voltage: Float)
        fun onCellVoltageData(voltage: Float)
        fun onCurrentData(current: Float)
        fun onHeadingData(heading: Float)
        fun onRSSIData(rssi: Int) //-1 - unknown/invalid, or device-dependent value
        fun onUpLqData(lq: Int)
        fun onDnLqData(lq: Int)
        fun onElrsModeModeData(mode: Int)
        fun onDisconnected()
        fun onGPSState(satellites: Int, gpsFix: Boolean)
        fun onVSpeedData(vspeed: Float)
        fun onThrottleData(throttle: Int)
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

        fun onAirSpeedData(speed: Float)
        fun onRCChannels(rcChannels:IntArray)
        fun onStatusText(message: String)
        fun onDNSNRData(snr: Int)
        fun onUPSNRData(snr: Int)
        fun onAntData(activeAntena: Int)
        fun onPowerData(power: Int)
        fun onRssiDbm1Data(rssi: Int)
        fun onRssiDbm2Data(rssi: Int)
        fun onRssiDbmdData(rssi: Int)
        fun onVBATOrCellData(voltage: Float)
        fun onTelemetryByte()
        fun onSuccessDecode()
    }

    fun isGPSData( telemetryType : Int ) : Boolean {
        return telemetryType == Protocol.GPS ||
            telemetryType == Protocol.GPS_LATITUDE ||
            telemetryType == Protocol.GPS_LONGITUDE ||
            telemetryType == Protocol.GPS_ORIGIN_LATITUDE ||
            telemetryType == Protocol.GPS_ORIGIN_LONGITUDE ||
            telemetryType == Protocol.GPS_HOME_LATITUDE ||
            telemetryType == Protocol.GPS_HOME_LONGITUDE;
    }

}