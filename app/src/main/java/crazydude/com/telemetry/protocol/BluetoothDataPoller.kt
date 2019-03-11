package crazydude.com.telemetry.protocol

import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

class BluetoothDataPoller(
    private val bluetoothSocket: BluetoothSocket,
    private val listener: DataDecoder.Listener,
    outputStream: FileOutputStream?,
    csvOutputStream: FileOutputStream?
) {

    private lateinit var protocol: FrSkySportProtocol
    private lateinit var thread: Thread
    private lateinit var dataDecoder: DataDecoder
    private var outputStreamWriter: OutputStreamWriter? = null

    init {
        thread = Thread(Runnable {
            try {
                csvOutputStream?.let { outputStreamWriter = OutputStreamWriter(it) }
                bluetoothSocket.connect()
                if (bluetoothSocket.isConnected) {
                    runOnMainThread(Runnable {
                        listener.onConnected()
                    })
                }
                protocol = FrSkySportProtocol(dataDecoder)
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
                try {
                    outputStreamWriter?.close()
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
                outputStreamWriter?.close()
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


    private fun runOnMainThread(runnable: Runnable) {
        Handler(Looper.getMainLooper())
            .post {
                runnable.run()
            }
    }

    fun disconnect() {
        thread.interrupt()
    }
}