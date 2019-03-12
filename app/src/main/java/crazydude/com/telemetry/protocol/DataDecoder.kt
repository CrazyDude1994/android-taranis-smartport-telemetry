package crazydude.com.telemetry.protocol

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import java.io.IOException

class DataDecoder(private val listener: Listener) : FrSkySportProtocol.Companion.DataListener {

    private var newLatitude = false
    private var newLongitude = false
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    companion object {
        private const val TAG = "DataDecoder"

        enum class FlyMode {
            ACRO, HORIZON, ANGLE, FAILSAFE, RTH, WAYPOINT, MANUAL, CRUISE
        }
    }

    override fun onNewData(data: FrSkySportProtocol.Companion.TelemetryData) {
        when (data.telemetryType) {
            FrSkySportProtocol.FUEL -> {
                listener.onFuelData(data.data)
            }
            FrSkySportProtocol.GPS -> {
                var gpsData = (data.data and 0x3FFFFFFF) / 10000.0 / 60.0
                if (data.data and 0x40000000 > 0) {
                    gpsData = -gpsData
                }
                if (data.data and 0x80000000.toInt() == 0) {
                    newLatitude = true
                    latitude = gpsData
                } else {
                    newLongitude = true
                    longitude = gpsData
                }
                if (newLatitude && newLongitude) {
                    newLongitude = false
                    newLatitude = false
                    listener.onGPSData(latitude, longitude)
                    try {
//                        outputStreamWriter?.append("$latitude, $longitude\r\n")
                    } catch (e: IOException) {
                        //ignore
                    }
                    Log.d(TAG, "Decoded GPS lat=$latitude long=$longitude")
                }
            }
            FrSkySportProtocol.VBAT -> {
                val value = data.data / 100f
                listener.onVBATData(value)
                Log.d(TAG, "Decoded vbat $value")
            }
            FrSkySportProtocol.CELL_VOLTAGE -> {
                val value = data.data / 100f
                listener.onCellVoltageData(value)
                Log.d(TAG, "Decoded cell voltage $value")
            }
            FrSkySportProtocol.CURRENT -> {
                val value = data.data / 10f
                listener.onCurrentData(value)
                Log.d(TAG, "Decoded current $value")
            }

            FrSkySportProtocol.HEADING -> {
                val value = data.data / 100f
                listener.onHeadingData(value)
                Log.d(TAG, "Decoded heading $value")
            }
            FrSkySportProtocol.RSSI -> {
                listener.onRSSIData(data.data)
            }

            FrSkySportProtocol.FLYMODE -> {
                val modeA = data.data / 10000
                val modeB = data.data / 1000 % 10
                val modeC = data.data / 100 % 10
                val modeD = data.data / 10 % 10
                val modeE = data.data % 10

                val firstFlightMode: FlyMode
                val secondFlightMode: FlyMode?

                if (modeD and 2 == 2) {
                    firstFlightMode = FlyMode.HORIZON
                } else if (modeD and 1 == 1) {
                    firstFlightMode = FlyMode.ANGLE
                } else {
                    firstFlightMode = FlyMode.ACRO
                }

                val armed = modeE and 4 == 4
                val heading = modeC and 1 == 1

                if (modeA and 4 == 4) {
                    secondFlightMode = FlyMode.FAILSAFE
                } else if (modeB and 1 == 1) {
                    secondFlightMode = FlyMode.RTH
                } else if (modeD and 4 == 4) {
                    secondFlightMode = FlyMode.MANUAL
                } else if (modeB and 2 == 2) {
                    secondFlightMode = FlyMode.WAYPOINT
                } else if (modeB and 8 == 8) {
                    secondFlightMode = FlyMode.CRUISE
                } else {
                    secondFlightMode = null
                }

                listener.onFlyModeData(armed, heading, firstFlightMode, secondFlightMode)
            }
            FrSkySportProtocol.GPS_STATE -> {
                val satellites = data.data % 100
                val isFix = data.data > 1000
                listener.onGPSState(satellites, isFix)
                Log.d(TAG, "Decoded satellites $satellites isFix=$isFix")
            }
            FrSkySportProtocol.VSPEED -> {
                val value = data.data / 100f
                listener.onVSpeedData(value)
                Log.d(TAG, "Decoded vspeed $value")
            }
            FrSkySportProtocol.ALTITUDE -> {
                val value = data.data / 100f
                listener.onAltitudeData(value)
                Log.d(TAG, "Decoded altitutde $value")
            }
            FrSkySportProtocol.GSPEED -> {
                val value = (data.data / (1944f / 100f)) / 27.778f
                listener.onGSpeedData(value)
                Log.d(TAG, "Decoded GSpeed $value")
            }
            FrSkySportProtocol.DISTANCE -> {
                listener.onDistanceData(data.data)
                Log.d(TAG, "Decoded distance ${data.data}")
            }
            FrSkySportProtocol.ROLL -> {
                val value = data.data / 10f
                listener.onRollData(value)
                Log.d(TAG, "Decoded roll $value")
            }
            FrSkySportProtocol.GALT -> {
                val value = data.data / 100f
                listener.onGPSAltitudeData(value)
                Log.d(TAG, "Decoded gps altitude $value")
            }
            FrSkySportProtocol.PITCH -> {
                val value = data.data / 10f
                listener.onPitchData(value)
                Log.d(TAG, "Decoded pitch $value")
            }
            else -> {}
        }
    }

    interface Listener {
        fun onConnectionFailed()
        fun onFuelData(fuel: Int)
        fun onConnected()
        fun onGPSData(latitude: Double, longitude: Double)
        fun onGPSData(list: List<LatLng>)
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
            firstFlightMode: FlyMode,
            secondFlightMode: FlyMode?
        )
    }
}