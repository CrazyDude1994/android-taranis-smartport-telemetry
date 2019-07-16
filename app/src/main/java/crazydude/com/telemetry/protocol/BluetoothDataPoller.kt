package crazydude.com.telemetry.protocol

import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

class BluetoothDataPoller(
    private val bluetoothSocket: BluetoothSocket,
    private val listener: DataDecoder.Listener,
    outputStream: FileOutputStream?,
    csvOutputStream: FileOutputStream?
) : DataPoller {

    private lateinit var protocol: CrsfProtocol
    private lateinit var thread: Thread
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
                protocol = CrsfProtocol(listener)
                val buffer = ByteArray(1024)
                while (!thread.isInterrupted && bluetoothSocket.isConnected) {
                    val size = bluetoothSocket.inputStream.read(buffer)
                    outputStream?.write(buffer, 0, size)
                    for (i in 0 until size) {
                        protocol.process(buffer[i].toInt())
                    }
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

    override fun disconnect() {
        thread.interrupt()
    }
}