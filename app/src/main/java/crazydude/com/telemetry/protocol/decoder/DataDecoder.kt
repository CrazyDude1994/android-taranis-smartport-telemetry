package crazydude.com.telemetry.protocol.decoder

import crazydude.com.telemetry.maps.Position
import crazydude.com.telemetry.protocol.Protocol

abstract class DataDecoder(protected val listener: Listener) {

    companion object {

        enum class FlyMode(var modeName: String) {
            ACRO("Acro"), HORIZON("Horizon"), ANGLE("Angle"), FAILSAFE("Failsafe"), RTH("RTH"), WAYPOINT(
                "WP"
            ),
            MANUAL(
                "Manual"
            ),
            CRUISE("Cruise"), HOLD("Hold"), HOME_RESET("Home reset"), CRUISE3D("Cruise 3D"), ALTHOLD(
                "ALT Hold"
            ),
            ERROR(
                "!ERROR!"
            ),
            WAIT("GPS wait"), AUTONOMOUS("Autonomous"), CIRCLE("Circle"), STABILIZE("Stabilize"), TRAINING(
                "Training"
            ),
            FBWA(
                "FBWA"
            ),
            FBWB("FBWB"), AUTOTUNE("Autotune"), LOITER("Loiter"), TAKEOFF("Takeoff"), AVOID_ADSB("AVOID_ADSB"), GUIDED(
                "Guided"
            ),
            INITIALISING("Initializing"), LANDING("Landing"), MISSION("Mission"), QSTABILIZE("QSTABILIZE"), QHOVER(
                "QHOVER"
            ),
            QLOITER(
                "QLOITER"
            ),
            QLAND("QLAND"), QRTL("QRTL"), QAUTOTUNE("QAUTOTUNE"), QACRO("QACRO");
        }

        open class DefaultDecodeListener : Listener {
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

            override fun onCurrentData(current: Float) {
            }

            override fun onHeadingData(heading: Float) {
            }

            override fun onRSSIData(rssi: Int) {
            }

            override fun onCrsfLqData(lq: Int) {
            }

            override fun onCrsfRfData(rf: Int) {
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
        fun onGPSData(list: List<Position>, addToEnd: Boolean)
        fun onVBATData(voltage: Float)
        fun onCellVoltageData(voltage: Float)
        fun onCurrentData(current: Float)
        fun onHeadingData(heading: Float)
        fun onRSSIData(rssi: Int) //-1 - unknown/invalid, or device-dependent value
        fun onCrsfLqData(lq: Int)
        fun onCrsfRfData(rf: Int)
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