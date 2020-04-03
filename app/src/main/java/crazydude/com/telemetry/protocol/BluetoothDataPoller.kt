package crazydude.com.telemetry.protocol

import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.util.Log
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

class BluetoothDataPoller(
    private val bluetoothSocket: BluetoothSocket,
    private val listener: DataDecoder.Listener,
    outputStream: FileOutputStream?
) : DataPoller {

    private var selectedProtocol: Protocol? = null
    private lateinit var thread: Thread
    private var outputStreamWriter: OutputStreamWriter? = null

    init {
        thread = Thread(Runnable {
            try {
                Log.d("BluetoothDataPoller", "MSzConnecting")
                bluetoothSocket.connect()
                if (bluetoothSocket.isConnected) {
                    runOnMainThread(Runnable {
                        listener.onConnected()
                    })
                }
                val protocolDetector = ProtocolDetector(object : ProtocolDetector.Callback {
                    override fun onProtocolDetected(protocol: Protocol?) {
                        when (protocol) {
                            is FrSkySportProtocol -> {
                                selectedProtocol =
                                    FrSkySportProtocol(listener)
                            }

                            is CrsfProtocol -> {
                                selectedProtocol =
                                    CrsfProtocol(listener)
                            }

                            is LTMProtocol -> {
                                selectedProtocol = LTMProtocol(listener)
                            }
                            else -> {
                                thread.interrupt()
                            }
                        }
                    }
                })
                val buffer = ByteArray(1024)
                while (!thread.isInterrupted && bluetoothSocket.isConnected) {
                    val size = bluetoothSocket.inputStream.read(buffer)
                    outputStream?.write(buffer, 0, size)
                    for (i in 0 until size) {
                        if (selectedProtocol == null) {
                            protocolDetector.feedData(buffer[i].toUByte().toInt())
                        } else {
                            selectedProtocol?.process(buffer[i].toUByte().toInt())
                        }
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