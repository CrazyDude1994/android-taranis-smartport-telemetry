package crazydude.com.telemetry

import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.IOException

class DataPoller(private val bluetoothSocket: BluetoothSocket, private val listener: Listener) :
    FrSkySportProtocol.Companion.DataListener {

    private val protocol: FrSkySportProtocol = FrSkySportProtocol(this)
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
                bluetoothSocket.close()
            } catch (e: IOException) {
                runOnMainThread(Runnable {
                    listener.onConnectionFailed()
                })
            }
        })

        thread.start()
    }

    override fun onNewData(data: FrSkySportProtocol.Companion.TelemetryData) {
        if (data.telemetryType == FrSkySportProtocol.Companion.TelemetryType.FUEL) {
            runOnMainThread(Runnable {
                listener.onFuelData(data.data)
            })
        } else if (data.telemetryType == FrSkySportProtocol.Companion.TelemetryType.GPS) {
            runOnMainThread(Runnable {
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
            })
        }
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
    }
}