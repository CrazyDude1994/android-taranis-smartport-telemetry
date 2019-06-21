package crazydude.com.telemetry.protocol

import android.bluetooth.*
import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
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
    private val outputStream: FileOutputStream?,
    csvOutputStream: FileOutputStream?
) : DataPoller {

    private lateinit var protocol: FrSkySportProtocol
    private val dataDecoder: DataDecoder = DataDecoder(listener)
    private var outputStreamWriter: OutputStreamWriter? = null
    private var connected = false
    private var bluetoothGatt: BluetoothGatt?

    init {
        csvOutputStream?.let { outputStreamWriter = OutputStreamWriter(it) }
        bluetoothGatt = device.connectGatt(context, false,
            object : BluetoothGattCallback() {

                private var serviceSelected = false
                private val tempProtocols: HashMap<UUID, FrSkySportProtocol> = HashMap()
                private val validPacketCount: HashMap<UUID, Int> = HashMap()

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
                                    protocol.process(it.toInt())
                                }
                            }
                        } else {
                            characteristic.value?.let { bytes ->
                                bytes.forEach {
                                    tempProtocols[characteristic.uuid]?.process(it.toInt())
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
                        tempProtocols.clear()
                        validPacketCount.clear()
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
                            val sportProtocol =
                                FrSkySportProtocol(object : Protocol.Companion.DataListener {
                                    override fun onNewData(data: Protocol.Companion.TelemetryData) {
                                        validPacketCount[characteristic.uuid] =
                                            validPacketCount[characteristic.uuid]!! + 1

                                        val entry = validPacketCount.filterValues { it >= 10 }.entries.firstOrNull()

                                        if (entry != null) {
                                            notifyCharacteristicList.filter { it.uuid != entry.key }.forEach {
                                                gatt.setCharacteristicNotification(it, false)
                                            }
                                            protocol = FrSkySportProtocol(dataDecoder)
                                            serviceSelected = true
                                            runOnMainThread(Runnable {
                                                listener.onConnected()
                                            })
                                            tempProtocols.clear()
                                            validPacketCount.clear()
                                        }
                                    }
                                })

                            validPacketCount.put(characteristic.uuid, 0)
                            tempProtocols.put(characteristic.uuid, sportProtocol)
                            gatt.setCharacteristicNotification(characteristic, true)
                            AsyncTask.execute {
                                Thread.sleep(5000)
                                if (!serviceSelected) {
                                    notifyCharacteristicList.forEach {
                                        gatt.setCharacteristicNotification(it, false)
                                        validPacketCount.clear()
                                        tempProtocols.clear()
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
            })
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