package crazydude.com.telemetry.protocol.decoder

import crazydude.com.telemetry.protocol.Protocol
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MAVLinkDataDecoder(listener: Listener) : DataDecoder(listener) {

    private var newLatitude = false
    private var newLongitude = false
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var fix = false
    private var satellites = 0

    companion object {
        private const val MAV_MODE_FLAG_STABILIZE_ENABLED = 16
        private const val MAV_MODE_FLAG_GUIDED_ENABLED = 8
        private const val MAV_MODE_FLAG_SAFETY_ARMED = 128
    }

    override fun decodeData(data: Protocol.Companion.TelemetryData) {
        var decoded = true
        when (data.telemetryType) {
            Protocol.VBAT -> {
                val value = data.data / 1000f
                listener.onVBATData(value)
            }
            Protocol.CURRENT -> {
                val value = data.data / 1000f
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
            Protocol.HEADING -> {
                val heading = data.data / 1000f
                listener.onHeadingData(heading)
            }
            Protocol.ALTITUDE -> {
                val altitude = data.data - 1000f
                listener.onAltitudeData(altitude)
            }
            Protocol.GSPEED -> {
                val speed = data.data / 100f
                listener.onGSpeedData(speed)
            }
            Protocol.FUEL -> {
                listener.onFuelData(data.data)
            }
            Protocol.FLYMODE -> {
                val rawMode = data.data
                val isStabilized =
                    (rawMode and MAV_MODE_FLAG_STABILIZE_ENABLED) == MAV_MODE_FLAG_STABILIZE_ENABLED
                val isGuided =
                    (rawMode and MAV_MODE_FLAG_GUIDED_ENABLED) == MAV_MODE_FLAG_GUIDED_ENABLED
                val armed = (rawMode and MAV_MODE_FLAG_SAFETY_ARMED) == MAV_MODE_FLAG_SAFETY_ARMED

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

                listener.onFlyModeData(armed, false, flyMode)
            }
            Protocol.ATTITUDE -> {
                val byteBuffer = ByteBuffer.wrap(data.rawData).order(ByteOrder.LITTLE_ENDIAN)
                val time = byteBuffer.int
                val roll = byteBuffer.float
                val pitch = byteBuffer.float
                val yaw = byteBuffer.float
                val rollSpeed = byteBuffer.float
                val pitchSpeed = byteBuffer.float
                val yawSpeed = byteBuffer.float
                listener.onRollData(roll)
                listener.onPitchData(pitch)
            }

            else -> {
                decoded = false
            }
        }

        if (newLatitude && newLongitude) {
            listener.onGPSData(latitude, longitude)
            newLatitude = false
            newLongitude = false
        }

        if (decoded) {
            listener.onSuccessDecode()
        }
    }
}