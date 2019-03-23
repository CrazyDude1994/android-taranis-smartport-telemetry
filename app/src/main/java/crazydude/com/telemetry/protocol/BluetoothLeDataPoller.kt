package crazydude.com.telemetry.protocol

import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.*

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class BluetoothLeDataPoller(
    context: Context,
    device: BluetoothDevice,
    private val listener: DataDecoder.Listener,
    private val outputStream: FileOutputStream?,
    csvOutputStream: FileOutputStream?
) : DataPoller {

    companion object {
        private val SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        private val CHARACTERISTIC_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    }

    private lateinit var protocol: FrSkySportProtocol
    private val dataDecoder: DataDecoder = DataDecoder(listener)
    private var outputStreamWriter: OutputStreamWriter? = null
    private var connected = false
    private var bluetoothGatt: BluetoothGatt?

    init {
        csvOutputStream?.let { outputStreamWriter = OutputStreamWriter(it) }
        bluetoothGatt = device.connectGatt(context, false,
            object : BluetoothGattCallback() {
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt?,
                    characteristic: BluetoothGattCharacteristic?
                ) {
                    super.onCharacteristicChanged(gatt, characteristic)
                    characteristic?.value?.let {
                        outputStream?.write(it)
                        it.forEach {
                            protocol.process(it.toInt())
                        }
                    }
                }

                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    super.onConnectionStateChange(gatt, status, newState)
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt?.discoverServices()
                        connected = true
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
                    val characteristic = gatt?.getService(SERVICE_UUID)
                        ?.getCharacteristic(CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        runOnMainThread(Runnable {
                            listener.onConnected()
                        })
                        protocol = FrSkySportProtocol(dataDecoder)
                        gatt.setCharacteristicNotification(characteristic, true)
                    } else {
                        runOnMainThread(Runnable {
                            listener.onConnectionFailed()
                        })
                    }
                }
            })
    }

    private fun closeConnection() {
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