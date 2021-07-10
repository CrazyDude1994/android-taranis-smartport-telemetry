package crazydude.com.telemetry.protocol.pollers

import android.bluetooth.*
import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import crazydude.com.telemetry.protocol.*
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.*
import kotlin.collections.HashMap

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class BluetoothLeDataPoller(
    context: Context,
    device: BluetoothDevice,
    private val listener: DataDecoder.Listener,
    private val outputStream: FileOutputStream?
) : DataPoller {

    private lateinit var selectedProtocol: Protocol
    private var outputStreamWriter: OutputStreamWriter? = null
    private var connected = false
    private var bluetoothGatt: BluetoothGatt?

    init {
        bluetoothGatt = device.connectGatt(context, false,
            object : BluetoothGattCallback() {

                private var serviceSelected = false
                private val protocolDetectors: HashMap<UUID, ProtocolDetector> = HashMap()

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt?,
                    characteristic: BluetoothGattCharacteristic?
                ) {
                    super.onCharacteristicChanged(gatt, characteristic)

                    characteristic?.let {
                        if (serviceSelected) {
                            characteristic.value?.let { bytes ->
                                outputStream?.write(bytes)
                                bytes.forEach {
                                    selectedProtocol.process(it.toUByte().toInt())
                                }
                            }
                        } else {
                            characteristic.value?.let { bytes ->
                                bytes.forEach {
                                    protocolDetectors[characteristic.uuid]?.feedData(it.toUByte().toInt())
                                }
                            }
                        }
                    }
                }

                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    super.onConnectionStateChange(gatt, status, newState)
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        connected = true
                        serviceSelected = false
                        protocolDetectors.clear()
                        gatt?.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        if (connected) {
                            runOnMainThread(Runnable {
                                listener.onDisconnected()
                            })
                        } else {
                            runOnMainThread(Runnable {
                                listener.onConnectionFailed()
                            })
                        }
                        connected = false
                        closeConnection()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    super.onServicesDiscovered(gatt, status)

                    val notifyCharacteristicList = gatt?.services?.flatMap { it.characteristics }
                        ?.filter { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == BluetoothGattCharacteristic.PROPERTY_NOTIFY }

                    if (notifyCharacteristicList != null && notifyCharacteristicList.isNotEmpty()) {
                        notifyCharacteristicList.forEach { characteristic ->
                            val protocolDetector =
                                ProtocolDetector(
                                    object :
                                        ProtocolDetector.Callback {
                                        override fun onProtocolDetected(protocol: Protocol?) {
                                            if (protocol != null) {
                                                notifyCharacteristicList.filter { it.uuid != characteristic.uuid }
                                                    .forEach {
                                                        gatt.setCharacteristicNotification(
                                                            it,
                                                            false
                                                        )
                                                    }
                                                when (protocol) {
                                                    is FrSkySportProtocol -> {
                                                        selectedProtocol =
                                                            FrSkySportProtocol(
                                                                listener
                                                            )
                                                    }

                                                    is CrsfProtocol -> {
                                                        selectedProtocol =
                                                            CrsfProtocol(
                                                                listener
                                                            )
                                                    }

                                                    is LTMProtocol -> {
                                                        selectedProtocol =
                                                            LTMProtocol(
                                                                listener
                                                            )
                                                    }

                                                    is MAVLinkProtocol -> {
                                                        selectedProtocol =
                                                            MAVLinkProtocol(
                                                                listener
                                                            )
                                                    }

                                                    is MAVLink2Protocol -> {
                                                        selectedProtocol =
                                                            MAVLink2Protocol(
                                                                listener
                                                            )
                                                    }
                                                }
                                                serviceSelected = true
                                                runOnMainThread(Runnable {
                                                    listener.onConnected()
                                                })
                                                protocolDetectors.clear()
                                            }
                                        }
                                    })
                            protocolDetectors.put(characteristic.uuid, protocolDetector)
                            gatt.setCharacteristicNotification(characteristic, true)
                            AsyncTask.execute {
                                Thread.sleep(5000)
                                if (!serviceSelected) {
                                    notifyCharacteristicList.forEach {
                                        gatt.setCharacteristicNotification(it, false)
                                        protocolDetectors.clear()
                                        runOnMainThread(Runnable {
                                            listener.onConnectionFailed()
                                        })
                                    }
                                }
                            }
                        }
                    } else {
                        runOnMainThread(Runnable {
                            listener.onConnectionFailed()
                        })
                    }
                }
            }, BluetoothDevice.TRANSPORT_LE)
    }

    fun closeConnection() {
        bluetoothGatt?.close()

        try {
            outputStreamWriter?.close()
        } catch (e: IOException) {

        }
        try {
            outputStream?.close()
        } catch (e: IOException) {

        }
    }


    private fun runOnMainThread(runnable: Runnable) {
        Handler(Looper.getMainLooper())
            .post {
                runnable.run()
            }
    }

    override fun disconnect() {
        bluetoothGatt?.disconnect()
    }
}