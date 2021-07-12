package crazydude.com.telemetry.protocol.decoder

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import crazydude.com.telemetry.protocol.Protocol
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MAVLinkDataDecoder(listener: Listener) : DataDecoder(listener) {

    private var newLatitude = false
    private var newLongitude = false
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var homeLatitude: Double = 0.0
    private var homeLongitude: Double = 0.0
    private var armedLatitude: Double = 0.0
    private var armedLongitude: Double = 0.0
    private var originLatitude: Double = 0.0
    private var originLongitude: Double = 0.0
    private var fix = false
    private var satellites = 0
    private var armed = false;
    private var armedOnce = false;
    private var rcChannels = IntArray(8) {1500};

    companion object {
        private const val MAV_MODE_FLAG_STABILIZE_ENABLED = 16
        private const val MAV_MODE_FLAG_GUIDED_ENABLED = 8
        private const val MAV_MODE_FLAG_SAFETY_ARMED = 128
        private const val MAV_MODE_FLAG_CUSTOM_MODE_ENABLED = 1

        private const val MAV_TYPE_FIXED_WING = 1
        private const val MAV_TYPE_GROUND_ROVER = 10
        private const val MAV_TYPE_SURFACE_BOAT = 11

        private const val PLANE_MODE_MANUAL = 0
        private const val PLANE_MODE_CIRCLE = 1
        private const val PLANE_MODE_STABILIZE = 2
        private const val PLANE_MODE_TRAINING = 3
        private const val PLANE_MODE_ACRO = 4
        private const val PLANE_MODE_FLY_BY_WIRE_A = 5
        private const val PLANE_MODE_FLY_BY_WIRE_B = 6
        private const val PLANE_MODE_CRUISE = 7
        private const val PLANE_MODE_AUTOTUNE = 8
        private const val PLANE_MODE_AUTO = 10
        private const val PLANE_MODE_RTL = 11
        private const val PLANE_MODE_LOITER = 12
        private const val PLANE_MODE_TAKEOFF = 13
        private const val PLANE_MODE_AVOID_ADSB = 14
        private const val PLANE_MODE_GUIDED = 15
        private const val PLANE_MODE_INITIALIZING = 16
        private const val PLANE_MODE_QSTABILIZE = 17
        private const val PLANE_MODE_QHOVER = 18
        private const val PLANE_MODE_QLOITER = 19
        private const val PLANE_MODE_QLAND = 20
        private const val PLANE_MODE_QRTL = 21
        private const val PLANE_MODE_QAUTOTUNE = 22
        private const val PLANE_MODE_ENUM_END = 23

        private const val COPTER_MODE_STABILIZE = 0
        private const val COPTER_MODE_ACRO = 1
        private const val COPTER_MODE_ALT_HOLD = 2
        private const val COPTER_MODE_AUTO = 3
        private const val COPTER_MODE_GUIDED = 4
        private const val COPTER_MODE_LOITER = 5
        private const val COPTER_MODE_RTL = 6
        private const val COPTER_MODE_CIRCLE = 7
        private const val COPTER_MODE_LAND = 9
        private const val COPTER_MODE_DRIFT = 11
        private const val COPTER_MODE_SPORT = 13
        private const val COPTER_MODE_FLIP = 14
        private const val COPTER_MODE_AUTOTUNE = 15
        private const val COPTER_MODE_POSHOLD = 16
        private const val COPTER_MODE_BRAKE = 17
        private const val COPTER_MODE_THROW = 18
        private const val COPTER_MODE_AVOID_ADSB = 19
        private const val COPTER_MODE_GUIDED_NOGPS = 20
        private const val COPTER_MODE_SMART_RTL = 21
        private const val COPTER_MODE_ENUM_END = 22

        private const val MAV_STATE_CRITICAL = 5
    }

    override fun decodeData(data: Protocol.Companion.TelemetryData) {
        var decoded = true
        when (data.telemetryType) {
            Protocol.VBAT -> {
                val value = data.data / 1000f
                listener.onVBATData(value)
            }
            Protocol.CURRENT -> {
                val value = data.data / 100f
                listener.onCurrentData(value)
            }
            Protocol.GPS_LONGITUDE -> {
                longitude = data.data / 10000000.toDouble()
                newLongitude = true
            }
            Protocol.GPS_LATITUDE -> {
                latitude = data.data / 10000000.toDouble()
                newLatitude = true
            }
            Protocol.GPS_SATELLITES -> {
                satellites = data.data
                listener.onGPSState(satellites, fix)
            }
            Protocol.GPS_STATE -> {
                fix = data.data == 3
                listener.onGPSState(satellites, fix)
            }
            Protocol.ALTITUDE -> {
                val altitude = data.data / 100f
                listener.onAltitudeData(altitude)
            }
            Protocol.GSPEED -> {
                val speed = (data.data / 100f) * 3.6f
                listener.onGSpeedData(speed)
            }
            Protocol.FUEL -> {
                listener.onFuelData(data.data)
            }
            Protocol.FLYMODE -> {
                val rawMode = data.data

                val byteBuffer = ByteBuffer.wrap(data.rawData).order(ByteOrder.LITTLE_ENDIAN)

                val customMode = byteBuffer.int
                val aircraftType = byteBuffer.get().toUByte().toInt()
                val autopilotClass = byteBuffer.get()
                val mode = byteBuffer.get()
                val state = byteBuffer.get().toUByte().toInt()
                val version = byteBuffer.get()

                val isStabilized =
                    (rawMode and MAV_MODE_FLAG_STABILIZE_ENABLED) == MAV_MODE_FLAG_STABILIZE_ENABLED
                val isGuided =
                    (rawMode and MAV_MODE_FLAG_GUIDED_ENABLED) == MAV_MODE_FLAG_GUIDED_ENABLED
                val armed = (rawMode and MAV_MODE_FLAG_SAFETY_ARMED) == MAV_MODE_FLAG_SAFETY_ARMED
                this.armed = armed;
                val isFailsafe = state == MAV_STATE_CRITICAL;

                var flyMode: DataDecoder.Companion.FlyMode
                if (isGuided) {
                    flyMode = DataDecoder.Companion.FlyMode.AUTONOMOUS
                } else {
                    if (isStabilized) {
                        flyMode = DataDecoder.Companion.FlyMode.ACRO
                    } else {
                        flyMode = DataDecoder.Companion.FlyMode.MANUAL
                    }
                }

                if ((rawMode and MAV_MODE_FLAG_CUSTOM_MODE_ENABLED) == MAV_MODE_FLAG_CUSTOM_MODE_ENABLED) {
                    //try to decode specific flight mode for INAV
                    //https://github.com/iNavFlight/inav/blob/2.6.0/src/main/telemetry/mavlink.c
                    flyMode = DataDecoder.Companion.FlyMode.OTHER
                    if ( ( aircraftType == MAV_TYPE_FIXED_WING ) ||
                        ( aircraftType == MAV_TYPE_GROUND_ROVER) ||
                        ( aircraftType == MAV_TYPE_SURFACE_BOAT)){
                        when (customMode) {
                            PLANE_MODE_MANUAL -> flyMode = DataDecoder.Companion.FlyMode.MANUAL
                            PLANE_MODE_ACRO -> flyMode = DataDecoder.Companion.FlyMode.ACRO
                            PLANE_MODE_FLY_BY_WIRE_A -> flyMode = DataDecoder.Companion.FlyMode.ANGLE
                            PLANE_MODE_STABILIZE -> flyMode = DataDecoder.Companion.FlyMode.HORIZON
                            PLANE_MODE_FLY_BY_WIRE_B -> flyMode = DataDecoder.Companion.FlyMode.ALTHOLD
                            PLANE_MODE_LOITER -> flyMode = DataDecoder.Companion.FlyMode.LOITER
                            PLANE_MODE_RTL -> flyMode = DataDecoder.Companion.FlyMode.RTH
                            PLANE_MODE_AUTO -> if ( isFailsafe ) flyMode = DataDecoder.Companion.FlyMode.OTHER else flyMode = DataDecoder.Companion.FlyMode.MISSION //Can not decode Waypoint or RTH after mission - use Mission. Can not decode Landing or Mission on failsafe - show nothing.
                            PLANE_MODE_CRUISE -> flyMode = DataDecoder.Companion.FlyMode.CRUISE  //can not decode Cruise or Cruise3D, not enough data
                            PLANE_MODE_TAKEOFF -> flyMode = DataDecoder.Companion.FlyMode.TAKEOFF
                        }
                    }
                    else {
                        when (customMode) {
                            COPTER_MODE_ACRO -> flyMode = DataDecoder.Companion.FlyMode.ACRO
                            COPTER_MODE_STABILIZE -> flyMode = DataDecoder.Companion.FlyMode.STABILIZE  //can not decode Angle or Horizon, not enough data
                            COPTER_MODE_ALT_HOLD -> flyMode = DataDecoder.Companion.FlyMode.ALTHOLD
                            COPTER_MODE_POSHOLD -> flyMode = DataDecoder.Companion.FlyMode.HOLD
                            COPTER_MODE_RTL -> flyMode = DataDecoder.Companion.FlyMode.RTH
                            COPTER_MODE_AUTO -> if ( isFailsafe ) flyMode = DataDecoder.Companion.FlyMode.OTHER else flyMode = DataDecoder.Companion.FlyMode.MISSION
                            COPTER_MODE_THROW -> flyMode = DataDecoder.Companion.FlyMode.TAKEOFF
                        }
                    }
                }

                if ( isFailsafe ) {
                    if ( flyMode == DataDecoder.Companion.FlyMode.OTHER )
                        listener.onFlyModeData(armed, false, DataDecoder.Companion.FlyMode.FAILSAFE )
                        else listener.onFlyModeData(armed, false, flyMode, DataDecoder.Companion.FlyMode.FAILSAFE)
                }
                else {
                    listener.onFlyModeData(armed, false, flyMode )
                }
            }
            Protocol.ATTITUDE -> {
                val byteBuffer = ByteBuffer.wrap(data.rawData).order(ByteOrder.LITTLE_ENDIAN)
                val time = byteBuffer.int
                val roll = byteBuffer.float
                val pitch = -byteBuffer.float
                val yaw = byteBuffer.float
                val rollSpeed = byteBuffer.float
                val pitchSpeed = byteBuffer.float
                val yawSpeed = byteBuffer.float
                listener.onRollData(Math.toDegrees(roll.toDouble()).toFloat())
                listener.onPitchData(Math.toDegrees(pitch.toDouble()).toFloat())
                listener.onHeadingData(Math.toDegrees(yaw.toDouble()).toFloat())
            }

            Protocol.GPS_ORIGIN_LONGITUDE -> {
                originLongitude = data.data / 10000000.toDouble()
            }

            Protocol.GPS_ORIGIN_LATITUDE -> {
                originLatitude = data.data / 10000000.toDouble()
            }

            Protocol.GPS_HOME_LONGITUDE -> {
                homeLongitude = data.data / 10000000.toDouble()
            }

            Protocol.GPS_HOME_LATITUDE -> {
                homeLatitude = data.data / 10000000.toDouble()
            }

            Protocol.RSSI -> {
                //https://github.com/mavlink/mavlink/issues/1027
				//send 0..100% 
                listener.onRSSIData( if ( data.data == 255) -1 else data.data * 100 / 254);
            }
            in Protocol.RC_CHANNEL_0..Protocol.RC_CHANNEL_15 -> {
                val index = data.telemetryType - Protocol.RC_CHANNEL_0;
                if ( index >= rcChannels.size) rcChannels = IntArray(index+1) { i -> if (i < rcChannels.size) rcChannels[i] else 1500 }
                rcChannels[index] = data.data
                listener.onRCChannels(rcChannels)
            }
            else -> {
                decoded = false
            }
        }

        if (newLatitude && newLongitude) {
            if (latitude > 0 && longitude > 0) {

            	listener.onGPSData(latitude, longitude)

                if ( armed && !armedOnce ) {
                    armedLatitude = latitude
                    armedLongitude = longitude
                    armedOnce = true;
                }

                if (homeLatitude > 0 && homeLongitude > 0) {

                    val distance = SphericalUtil.computeDistanceBetween(
                        LatLng(
                            homeLatitude,
                            homeLongitude
                        ), LatLng(latitude, longitude)
                    )

                    listener.onDistanceData(distance.toInt())
                } else if (originLatitude > 0 && originLongitude > 0 ) {

                        val distance = SphericalUtil.computeDistanceBetween(
                            LatLng(
                                originLatitude,
                                originLongitude
                            ), LatLng(latitude, longitude)
                        )

                        listener.onDistanceData(distance.toInt())
                } else if (armedLatitude > 0 && armedLongitude > 0 ) {

                    val distance = SphericalUtil.computeDistanceBetween(
                        LatLng(
                            armedLatitude,
                            armedLongitude
                        ), LatLng(latitude, longitude)
                    )

                    listener.onDistanceData(distance.toInt())
                }
            }

            newLatitude = false
            newLongitude = false
        }

        if (decoded) {
            listener.onSuccessDecode()
        }
    }
}