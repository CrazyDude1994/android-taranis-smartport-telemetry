package crazydude.com.telemetry.protocol.decoder

import android.util.Log
import crazydude.com.telemetry.protocol.Protocol
import java.io.IOException
import kotlin.math.pow

class FrskyDataDecoder(listener: Listener) : DataDecoder(listener) {

    private var newLatitude = false
    private var newLongitude = false
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private val TAG: String = "FrSky Protocol"
    private fun bitExtracted(number: Int, num: Int, pos: Int): Int {
        return (1 shl num) - 1 and (number shr pos - 1)
    }


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
//                  Log.d(TAG, "Decoded GPS lat=$latitude long=$longitude")
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
                val speed = Float.fromBits(Integer.parseInt(Integer.toBinaryString(data.data)))
                listener.onAirSpeed(speed * 1.852f)
            }
            Protocol.GPS_LONGITUDE -> {

            }
            Protocol.GPS_LATITUDE -> {

            }
            Protocol.GPS_SATELLITES -> {
                listener.onGPSState(data.data, true)
            }
            //decode ardupilot passthrough sensors
            Protocol.ARDU_GPS_STATUS ->{ //0x5002
                val satellites = bitExtracted(data.data,4,1)
                val gpsStatus=bitExtracted(data.data,2,5)
                val isFix = gpsStatus>= 3
                val gpsAlt: Double = bitExtracted(data.data,7,25)*10.0.pow(bitExtracted(data.data,2,23).toDouble())/10f
                if (bitExtracted(data.data,1,32)==1) listener.onGPSAltitudeData(gpsAlt.toFloat()*-1)
                else listener.onGPSAltitudeData(gpsAlt.toFloat())
                listener.onGPSState(satellites, isFix)
                //Log.d(TAG, "Decoded satellites $satellites isFix=$isFix GPSAlt $gpsAlt")
            }

            Protocol.ARDU_BATT_1 ->{ //0x5003
                val fr_bat1_amps = bitExtracted(data.data,7,11) * 10.0.pow(bitExtracted(data.data,1,10).toDouble())/10f
                val fr_bat1_mAh = bitExtracted(data.data,15,18)
                val fr_bat1_volts = bitExtracted(data.data,9,1).toFloat() / 10f
                val cellcount = fr_bat1_volts/4.3+1.toInt()
                val cellVoltage=fr_bat1_volts/cellcount
                listener.onCellVoltageData(cellVoltage.toFloat())
                listener.onVBATData(fr_bat1_volts)
                listener.onCurrentData(fr_bat1_amps.toFloat())
                listener.onFuelData(fr_bat1_mAh)
//              Log.d(TAG, "Decoded amps=$fr_bat1_amps")
//              Log.d(TAG, "Decoded mah=$fr_bat1_mAh")
            }

            Protocol.ARDU_AP_STATUS ->{ //0x5001
                val arduFlightMode=bitExtracted(data.data,5,1)
                val arduArmed:Boolean
                val firstFlightMode: Companion.FlyMode
                arduArmed = bitExtracted(data.data,1,9)==1
                if (arduFlightMode == 1) {
                    firstFlightMode = Companion.FlyMode.MANUAL
                } else if (arduFlightMode == 2) {
                    firstFlightMode = Companion.FlyMode.CIRCLE
                } else if (arduFlightMode == 3) {
                    firstFlightMode = Companion.FlyMode.STABILIZE
                } else if (arduFlightMode == 4) {
                    firstFlightMode = Companion.FlyMode.TRAINING
                } else if (arduFlightMode == 5) {
                    firstFlightMode = Companion.FlyMode.ACRO
                } else if (arduFlightMode == 6) {
                    firstFlightMode = Companion.FlyMode.FBWA
                } else if (arduFlightMode == 7) {
                    firstFlightMode = Companion.FlyMode.FBWB
                } else if (arduFlightMode == 8) {
                    firstFlightMode = Companion.FlyMode.CRUISE
                } else if (arduFlightMode == 9) {
                    firstFlightMode = Companion.FlyMode.AUTOTUNE
                } else if (arduFlightMode == 11) {
                    firstFlightMode = Companion.FlyMode.AUTONOMOUS
                } else if (arduFlightMode == 12) {
                    firstFlightMode = Companion.FlyMode.RTH
                } else if (arduFlightMode == 13) {
                    firstFlightMode = Companion.FlyMode.LOITER
                } else if (arduFlightMode == 14) {
                    firstFlightMode = Companion.FlyMode.TAKEOFF
                } else if (arduFlightMode == 15) {
                     firstFlightMode = Companion.FlyMode.AVOID_ADSB
                } else if (arduFlightMode == 16) {
                    firstFlightMode = Companion.FlyMode.GUIDED
                } else if (arduFlightMode == 17) {
                    firstFlightMode = Companion.FlyMode.INITIALISING
                } else if (arduFlightMode == 18) {
                    firstFlightMode = Companion.FlyMode.QSTABILIZE
                } else if (arduFlightMode == 19) {
                    firstFlightMode = Companion.FlyMode.QHOVER
                } else if (arduFlightMode == 20) {
                    firstFlightMode = Companion.FlyMode.QLOITER
                } else if (arduFlightMode == 21) {
                    firstFlightMode = Companion.FlyMode.QLAND
                } else if (arduFlightMode == 22) {
                    firstFlightMode = Companion.FlyMode.QRTL
                } else if (arduFlightMode == 23) {
                    firstFlightMode = Companion.FlyMode.QAUTOTUNE
                } else if (arduFlightMode == 24) {
                    firstFlightMode = Companion.FlyMode.QACRO
                } else {
                    firstFlightMode = Companion.FlyMode.FBWA
                }
                listener.onFlyModeData(arduArmed, false, firstFlightMode, null)
//                Log.d(TAG, "Decoded flightmode $arduFlightMode")
            }

            Protocol.ARDU_HOME ->{ //0x5004
                val fr_home_dist = bitExtracted(data.data,10,3) * 10.0.pow(bitExtracted(data.data,2,1)).toDouble()
                val alt_frome_home = bitExtracted(data.data,10,15)*10.0.pow(bitExtracted(data.data,2,13)).toDouble()*0.1
                if (bitExtracted(data.data,1,25)==1) listener.onAltitudeData(alt_frome_home.toFloat()*-1)
                else listener.onAltitudeData(alt_frome_home.toFloat())
                listener.onDistanceData(fr_home_dist.toInt())
                //Log.d(TAG, "Decoded distance $fr_home_dist ALT From Home $alt_frome_home")
            }

            Protocol.ARDU_VEL_YAW ->{ //0x5005
                val VSpeed = bitExtracted(data.data,7,2) * 10.0.pow(bitExtracted(data.data,1,1)).toDouble()/10f*3.6f*1.609344497892563f //km/h to mph
                val GSpeed = bitExtracted(data.data,7,11) * 10.0.pow(bitExtracted(data.data,1,10)).toDouble()/10f*3.6f*1.609344497892563f //km/h to mph
                val Heading = bitExtracted(data.data,11,18)*0.2f
                listener.onVSpeedData(VSpeed.toFloat())
                listener.onGSpeedData(GSpeed.toFloat())
                listener.onHeadingData(Heading)
                //Log.d(TAG, "Decoded VSpeed: $VSpeed, Decoded GSpeed:$GSpeed, Decoded Heading: $Heading")
            }

            Protocol.ARDU_ATTITUDE ->{ //0x5006
                val Roll = bitExtracted(data.data,11,1)
                val Pitch = bitExtracted(data.data,10,12)
                listener.onRollData((Roll-900)*0.2f)
                listener.onPitchData((Pitch-450)*0.2f)
//                Log.d(TAG, "Decoded roll $Roll, pitch $Pitch")
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