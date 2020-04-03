package crazydude.com.telemetry.protocol

import android.hardware.usb.UsbDeviceConnection
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors

class UsbDataPoller(
    private val listener: DataDecoder.Listener,
    private val serialPort: UsbSerialPort,
    private val connection: UsbDeviceConnection,
    private val logFile: FileOutputStream?
) : DataPoller {

    private var outputManager: SerialInputOutputManager? = null
    private var selectedProtocol: Protocol? = null

    init {
        try {
            serialPort.open(connection)
        } catch (e: IOException) {
            listener.onConnectionFailed()
        }
            serialPort.setParameters(
                57600,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

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
                        listener.onConnectionFailed()
                        outputManager?.stop()
                        return
                    }
                }

                listener.onConnected()
            }
        })

        outputManager =
            SerialInputOutputManager(serialPort, object : SerialInputOutputManager.Listener {
                override fun onRunError(e: Exception?) {
                    listener.onDisconnected()
                }

                override fun onNewData(data: ByteArray?) {
                    data?.let {
                        logFile?.write(data)
                        if (selectedProtocol != null) {
                            data.forEach {
                                selectedProtocol?.process(it.toUByte().toInt())
                            }
                        } else {
                            data.forEach {
                                protocolDetector.feedData(it.toUByte().toInt())
                            }
                        }
                    }
                }
            })
        Executors.newSingleThreadExecutor().submit(outputManager!!)
    }


    override fun disconnect() {
        outputManager?.stop()
        listener.onDisconnected()
    }
}