package crazydude.com.telemetry.protocol.decoder

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import crazydude.com.telemetry.protocol.Protocol

const val MAX_RSSI = -30
const val MIN_RSSI = -120

class CrsfDataDecoder(listener: Listener) : DataDecoder(listener) {

    private var newLatitude = false
    private var newLongitude = false
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var armedLatitude: Double = 0.0
    private var armedLongitude: Double = 0.0
    private var armed = false;
    private var armedOnce = false;
    private var rcChannels = IntArray(16) {1500};

    private fun sq(v : Int) : Int {
        return v*v;
    }

    init {
        this.restart()
    }
    override fun restart() {
        this.newLatitude = false
        this.newLongitude = false
        this.latitude = 0.0
        this.longitude = 0.0
        this.armedLatitude = 0.0
        this.armedLongitude = 0.0
        this.armed = false;
        this.armedOnce = false;
        this.rcChannels = IntArray(16) { 1500 };
    }

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
            Protocol.GPS_ALTITUDE -> {
                val gps_altitude = data.data - 1000.0f
                listener.onAltitudeData(gps_altitude)
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
                //data.data is RSSI in dbm, negative number like -76
                //convert RSSI to % like inav do with default MIN_RSSI, MAX_RSSI settings
                var v = (100 * sq(MAX_RSSI - MIN_RSSI) - (100 * sq(MAX_RSSI  - data.data))) / sq(MAX_RSSI - MIN_RSSI);
                if (data.data >= MAX_RSSI ) v = 99;
                if (data.data < MIN_RSSI) v = 0;

                listener.onRSSIData(v)
            }
            Protocol.CRSF_UP_LQ -> {
                listener.onUpLqData(data.data)
            }
            Protocol.CRSF_DN_LQ -> {
                listener.onDnLqData(data.data)
            }
            Protocol.ELRS_RF_MODE -> {
                listener.onElrsModeModeData(data.data)
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
                            this.armed = true;
                        }
                        "!FS!" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.FAILSAFE, null)
                        }
                        "MANU" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.MANUAL, null)
                            this.armed = true;
                        }
                        "RTH" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.RTH, null)
                            this.armed = true;
                        }
                        "HOLD" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.LOITER, null)
                            this.armed = true;
                        }
                        "HRST" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.HOME_RESET, null)
                            this.armed = true;
                        }
                        "3CRS","CRUZ" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.CRUISE3D, null)
                            this.armed = true;
                        }
                        "CRS", "CRSH" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.CRUISE, null)
                            this.armed = true;
                        }
                        "AH" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.ALTHOLD, null)
                        }
                        "WP" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.WAYPOINT, null)
                            this.armed = true;
                        }
                        "ANGL", "STAB" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.ANGLE, null)
                            this.armed = true;
                        }
                        "HOR" -> {
                            listener.onFlyModeData(true, false, Companion.FlyMode.HORIZON, null)
                            this.armed = true;
                        }
                        "WAIT" -> {
                            listener.onFlyModeData(false, false, Companion.FlyMode.WAIT, null)
                            this.armed = false;
                        }
                        "!ERR" -> {
                            listener.onFlyModeData(false, false, Companion.FlyMode.ERROR, null)
                            this.armed = false;
                        }
                        "OK" -> {
                            listener.onFlyModeData(false, false, Companion.FlyMode.ACRO, null)
                            this.armed = false;
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
            in Protocol.RC_CHANNEL_0..Protocol.RC_CHANNEL_15 -> {
                val index = data.telemetryType - Protocol.RC_CHANNEL_0;
                rcChannels[index] = data.data
                listener.onRCChannels(rcChannels)
            }
            Protocol.DN_SNR -> {
                listener.onDNSNRData(data.data)
            }
            Protocol.UP_SNR -> {
                listener.onUPSNRData(data.data)
            }
            Protocol.ANT -> {
                listener.onAntData(data.data)
            }
            Protocol.POWER -> {
                listener.onPowerData(data.data)
            }
            Protocol.RSSI_DBM_1 -> {
                listener.onRssiDbm1Data(data.data)
            }
            Protocol.RSSI_DBM_2 -> {
                listener.onRssiDbm2Data(data.data)
            }
            Protocol.RSSI_DBM_D -> {
                listener.onRssiDbmdData(data.data)
            }
            Protocol.VBAT_OR_CELL -> {
                val value = data.data / 10f
                listener.onVBATOrCellData(value)
            }
            else -> {
                decoded = false
            }
        }

        if (newLatitude && newLongitude) {
            listener.onGPSData(latitude, longitude)

            if ( armed && !armedOnce ) {
                armedLatitude = latitude
                armedLongitude = longitude
                armedOnce = true;
            }

            if (armedLatitude != 0.0 && armedLongitude != 0.0 ) {

                val distance = SphericalUtil.computeDistanceBetween(
                    LatLng(
                        armedLatitude,
                        armedLongitude
                    ), LatLng(latitude, longitude)
                )

                listener.onDistanceData(distance.toInt())
            }

            newLatitude = false
            newLongitude = false
        }

        if (decoded) {
            listener.onSuccessDecode()
        }
    }
}