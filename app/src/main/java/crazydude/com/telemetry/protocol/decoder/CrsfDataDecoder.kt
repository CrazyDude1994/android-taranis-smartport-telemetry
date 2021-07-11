package crazydude.com.telemetry.protocol.decoder

import android.util.Log
import crazydude.com.telemetry.protocol.Protocol

const val MAX_RSSI = 0
const val MIN_RSSI = -130

class CrsfDataDecoder(listener: Listener) : DataDecoder(listener) {

    private var newLatitude = false
    private var newLongitude = false
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    override fun decodeData(data: Protocol.Companion.TelemetryData) {
        var decoded = true
        when (data.telemetryType) {
            Protocol.VBAT -> {
                val value = data.data / 10f
                listener.onVBATData(value)
            }
            Protocol.CURRENT -> {
                val value = data.data / 10f
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
                val satellites = data.data
                listener.onGPSState(satellites, satellites > 6)
            }
            Protocol.HEADING -> {
                val heading = data.data / 100f
//                listener.onHeadingData(heading)
            }
            Protocol.ALTITUDE -> {
                val altitude = data.data - 1000f
                listener.onAltitudeData(altitude)
            }
            Protocol.GSPEED -> {
                val speed = data.data / 10f
                listener.onGSpeedData(speed)
            }
            Protocol.VSPEED -> {
                val speed = data.data / 10f
                listener.onVSpeedData(speed)
            }
            Protocol.RSSI -> {
                listener.onRSSIData((data.data - MIN_RSSI) * 100 / (MAX_RSSI - MIN_RSSI))
            }
            Protocol.FUEL -> {
                listener.onFuelData(data.data)
            }
            Protocol.FLYMODE -> {
                data.rawData?.let {
                    val stringLength = it.indexOfFirst { it == 0x00.toByte() }
                    val byteArray = ByteArray(stringLength)
                    it.copyInto(byteArray, endIndex = stringLength)
                    val flightMode = String(byteArray)

                    when (flightMode) {
                        "AIR", "ACRO" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.ACRO, null)
                        }
                        "!FS!" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.FAILSAFE, null)
                        }
                        "MANU" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.MANUAL, null)
                        }
                        "RTH" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.RTH, null)
                        }
                        "HOLD" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.HOLD, null)
                        }
                        "HRST" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.HOME_RESET, null)
                        }
                        "3CRS" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.CRUISE3D, null)
                        }
                        "CRS" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.CRUISE, null)
                        }
                        "AH" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.ALTHOLD, null)
                        }
                        "WP" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.WAYPOINT, null)
                        }
                        "ANGL", "STAB" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.ANGLE, null)
                        }
                        "HOR" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.HORIZON, null)
                        }
                        "WAIT" -> {
                            listener.onFlyModeData(false, false, Companion.FlyMode.WAIT, null)
                        }
                        "!ERR" -> {
                            listener.onFlyModeData(false, false, Companion.FlyMode.ERROR, null)
                        }
                        else -> {
                            Log.d("CrsfData", "Bad mode $flightMode")
                        }
                    }
                }
            }
            Protocol.PITCH -> {
                val pitch = Math.toDegrees(data.data.toDouble() / 10000)
                listener.onPitchData(pitch.toFloat())
            }
            Protocol.ROLL -> {
                val roll = Math.toDegrees(data.data.toDouble() / 10000)
                listener.onRollData(roll.toFloat())
            }
            Protocol.YAW -> {
                val yaw = Math.toDegrees(data.data.toDouble() / 10000)
                listener.onHeadingData(yaw.toFloat())
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