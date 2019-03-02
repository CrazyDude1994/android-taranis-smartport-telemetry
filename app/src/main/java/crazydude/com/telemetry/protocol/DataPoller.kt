package crazydude.com.telemetry.protocol

import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.util.Log
import crazydude.com.telemetry.protocol.FrSkySportProtocol.Companion.TelemetryType.*
import java.io.FileOutputStream
import java.io.IOException

class DataPoller(
    private val bluetoothSocket: BluetoothSocket,
    private val listener: Listener,
    outputStream: FileOutputStream?
) :
    FrSkySportProtocol.Companion.DataListener {

    private val protocol: FrSkySportProtocol =
        FrSkySportProtocol(this)
    private lateinit var thread: Thread
    private var newLatitude = false
    private var newLongitude = false
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    companion object {
        private const val TAG = "DataPoller"

        enum class FlyMode {
            ACRO, HORIZON, ANGLE, FAILSAFE, RTH, WAYPOINT, MANUAL, CRUISE
        }
    }

    init {
        thread = Thread(Runnable {
            try {
                bluetoothSocket.connect()
                if (bluetoothSocket.isConnected) {
                    runOnMainThread(Runnable {
                        listener.onConnected()
                    })
                }
                while (!thread.isInterrupted && bluetoothSocket.isConnected) {
                    val data = bluetoothSocket.inputStream.read()
                    outputStream?.write(data)
                    protocol.process(data)
                }
            } catch (e: IOException) {
                try {
                    outputStream?.close()
                } catch (e: IOException) {
                    // ignore
                }
                runOnMainThread(Runnable {
                    listener.onConnectionFailed()
                })
                return@Runnable
            }
            try {
                outputStream?.close()
            } catch (e: IOException) {
                // ignore
            }
            try {
                bluetoothSocket.close()
                runOnMainThread(Runnable {
                    listener.onDisconnected()
                })
            } catch (e: IOException) {
                runOnMainThread(Runnable {
                    listener.onDisconnected()
                })
            }
        })

        thread.start()
    }

    override fun onNewData(data: FrSkySportProtocol.Companion.TelemetryData) {
        runOnMainThread(when (data.telemetryType) {
            FUEL -> Runnable {
                listener.onFuelData(data.data)
            }
            GPS -> Runnable {
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
                    Log.d(TAG, "Decoded GPS lat=$latitude long=$longitude")
                }
            }
            VBAT -> Runnable {
                val value = data.data / 100f
                listener.onVBATData(value)
                Log.d(TAG, "Decoded vbat $value")
            }
            CELL_VOLTAGE -> Runnable {
                val value = data.data / 100f
                listener.onCellVoltageData(value)
                Log.d(TAG, "Decoded cell voltage $value")
            }
            CURRENT -> Runnable {
                val value = data.data / 10f
                listener.onCurrentData(value)
                Log.d(TAG, "Decoded current $value")
            }

            HEADING -> Runnable {
                val value = data.data / 100f
                listener.onHeadingData(value)
                Log.d(TAG, "Decoded heading $value")
            }
            RSSI -> Runnable {
                listener.onRSSIData(data.data)
            }

            FLYMODE -> Runnable {
                val modeA = data.data / 10000
                val modeB = data.data / 1000 % 10
                val modeC = data.data / 100 % 10
                val modeD = data.data / 10 % 10
                val modeE = data.data % 10

                val firstFlightMode: FlyMode
                val secondFlightMode: FlyMode?

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
            GPS_STATE -> Runnable {
                val satellites = data.data % 100
                val isFix = data.data > 1000
                listener.onGPSState(satellites, isFix)
                Log.d(TAG, "Decoded satellites $satellites isFix=$isFix")
            }
            VSPEED -> Runnable {
                val value = data.data / 100f
                listener.onVSpeedData(value)
                Log.d(TAG, "Decoded vspeed $value")
            }
            ALTITUDE -> Runnable {
                val value = data.data / 100f
                listener.onAltitudeData(value)
                Log.d(TAG, "Decoded altitutde $value")
            }
            GSPEED -> Runnable {
                val value = (data.data / 1000f) * 1.852f
                listener.onGSpeedData(value)
                Log.d(TAG, "Decoded GSpeed $value")
            }
            DISTANCE -> Runnable {
                listener.onDistanceData(data.data)
                Log.d(TAG, "Decoded distance ${data.data}")
            }
            ROLL -> Runnable {
                val value = data.data / 10f
                listener.onRollData(value)
                Log.d(TAG, "Decoded roll $value")
            }
            GALT -> Runnable {
                val value = data.data / 100f
                listener.onGPSAltitudeData(value)
                Log.d(TAG, "Decoded gps altitude $value")
            }
            PITCH -> Runnable {
                val value = data.data / 10f
                listener.onPitchData(value)
                Log.d(TAG, "Decoded pitch $value")
            }
        })
    }

    private fun runOnMainThread(runnable: Runnable) {
        Handler(Looper.getMainLooper())
            .post {
                runnable.run()
            }
    }

    fun disconnect() {
        thread.interrupt()
    }

    interface Listener {
        fun onConnectionFailed()
        fun onFuelData(fuel: Int)
        fun onConnected()
        fun onGPSData(latitude: Double, longitude: Double)
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