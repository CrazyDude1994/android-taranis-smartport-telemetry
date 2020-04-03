package crazydude.com.telemetry.protocol.decoder

import crazydude.com.telemetry.protocol.Protocol
import java.io.IOException

class FrskyPassthroughDataDecoder(listener: Listener) : DataDecoder(listener) {

    private var newLatitude = false
    private var newLongitude = false
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    override fun decodeData(data: Protocol.Companion.TelemetryData) {
        var decoded = true
        when (data.telemetryType) {
            Protocol.FUEL -> {
                listener.onFuelData(data.data)
            }
            Protocol.GPS -> {
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
//                    Log.d(TAG, "Decoded GPS lat=$latitude long=$longitude")
                }
            }
            Protocol.VBAT -> {
                val value = data.data / 100f
                listener.onVBATData(value)
//                Log.d(TAG, "Decoded vbat $value")
            }
            Protocol.CELL_VOLTAGE -> {
                val value = data.data / 100f
                listener.onCellVoltageData(value)
//                Log.d(TAG, "Decoded cell voltage $value")
            }
            Protocol.CURRENT -> {
                val value = data.data / 10f
                listener.onCurrentData(value)
//                Log.d(TAG, "Decoded current $value")
            }

            Protocol.HEADING -> {
                val value = data.data / 100f
                listener.onHeadingData(value)
//                Log.d(TAG, "Decoded heading $value")
            }
            Protocol.RSSI -> {
                listener.onRSSIData(data.data)
            }

            Protocol.FLYMODE -> {
                val modeA = data.data / 10000
                val modeB = data.data / 1000 % 10
                val modeC = data.data / 100 % 10
                val modeD = data.data / 10 % 10
                val modeE = data.data % 10

                val firstFlightMode: Companion.FlyMode
                val secondFlightMode: Companion.FlyMode?

                if (modeD and 2 == 2) {
                    firstFlightMode = Companion.FlyMode.HORIZON
                } else if (modeD and 1 == 1) {
                    firstFlightMode = Companion.FlyMode.ANGLE
                } else {
                    firstFlightMode = Companion.FlyMode.ACRO
                }

                val armed = modeE and 4 == 4
                val heading = modeC and 1 == 1

                if (modeA and 4 == 4) {
                    secondFlightMode = Companion.FlyMode.FAILSAFE
                } else if (modeB and 1 == 1) {
                    secondFlightMode = Companion.FlyMode.RTH
                } else if (modeD and 4 == 4) {
                    secondFlightMode = Companion.FlyMode.MANUAL
                } else if (modeB and 2 == 2) {
                    secondFlightMode = Companion.FlyMode.WAYPOINT
                } else if (modeB and 8 == 8) {
                    secondFlightMode = Companion.FlyMode.CRUISE
                } else {
                    secondFlightMode = null
                }

                listener.onFlyModeData(armed, heading, firstFlightMode, secondFlightMode)
            }
            Protocol.GPS_STATE -> {
                val satellites = data.data % 100
                val isFix = data.data > 1000
                listener.onGPSState(satellites, isFix)
//                Log.d(TAG, "Decoded satellites $satellites isFix=$isFix")
            }
            Protocol.VSPEED -> {
                val value = data.data / 100f
                listener.onVSpeedData(value)
//                Log.d(TAG, "Decoded vspeed $value")
            }
            Protocol.ALTITUDE -> {
                val value = data.data / 100f
                listener.onAltitudeData(value)
//                Log.d(TAG, "Decoded altitutde $value")
            }
            Protocol.GSPEED -> {
                val value = (data.data / (1944f / 100f)) / 27.778f
                listener.onGSpeedData(value)
//                Log.d(TAG, "Decoded GSpeed $value")
            }
            Protocol.DISTANCE -> {
                listener.onDistanceData(data.data)
//                Log.d(TAG, "Decoded distance ${data.data}")
            }
            Protocol.ROLL -> {
                val value = data.data / 10f
                listener.onRollData(value)
//                Log.d(TAG, "Decoded roll $value")
            }
            Protocol.GALT -> {
                val value = data.data / 100f
                listener.onGPSAltitudeData(value)
//                Log.d(TAG, "Decoded gps altitude $value")
            }
            Protocol.PITCH -> {
                val value = data.data / 10f
                listener.onPitchData(value)
//                Log.d(TAG, "Decoded pitch $value")
            }
            Protocol.ASPEED -> {
                listener.onAirSpeed(data.data * 1.852f)
            }
            Protocol.GPS_LONGITUDE -> {

            }
            Protocol.GPS_LATITUDE -> {

            }
            Protocol.GPS_SATELLITES -> {
                listener.onGPSState(data.data, true)
            }
            else -> {
                decoded = false
            }
        }

        if (decoded) {
            listener.onSuccessDecode()
        }
    }
}