package crazydude.com.telemetry

import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import crazydude.com.telemetry.protocol.FrSkySportProtocol
import crazydude.com.telemetry.protocol.FrSkySportProtocol.Companion.TelemetryType.*
import java.io.IOException

class DataPoller(private val bluetoothSocket: BluetoothSocket, private val listener: Listener) :
    FrSkySportProtocol.Companion.DataListener {

    private val protocol: FrSkySportProtocol =
        FrSkySportProtocol(this)
    private lateinit var thread: Thread
    private var newLatitude = false
    private var newLongitude = false
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

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
                    protocol.process(bluetoothSocket.inputStream.read())
                }
            } catch (e: IOException) {
                runOnMainThread(Runnable {
                    listener.onConnectionFailed()
                })
                return@Runnable
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
                }
            }
            VBAT -> Runnable {
                listener.onVBATData(data.data / 100f)
            }
            CELL_VOLTAGE -> Runnable {
                listener.onCellVoltageData(data.data / 100f)
            }
            CURRENT -> Runnable {
                listener.onCurrentData(data.data / 10f)
            }

            HEADING -> Runnable {
                listener.onHeadingData(data.data / 100f)
            }
            RSSI -> Runnable {
                listener.onRSSIData(data.data)
            }

            FLYMODE -> Runnable {
            }
            GPS_STATE -> Runnable {
                val satellites = data.data % 100
                val isFix = data.data > 1000
                listener.onGPSState(satellites, isFix)
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
    }
}