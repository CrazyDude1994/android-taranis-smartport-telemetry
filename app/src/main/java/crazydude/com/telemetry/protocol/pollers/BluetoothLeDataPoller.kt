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
                                    listener?.onTelemetryByte();
                                    selectedProtocol.process(it.toUByte().toInt())
                                }
                            }
                        } else {
                            characteristic.value?.let { bytes ->
                                bytes.forEach {
                                    listener?.onTelemetryByte();
                                    protocolDetectors[characteristic.uuid]?.feedData(
                                        it.toUByte().toInt()
                                    )
                                }
                            }
                        }
                    }
                }

                override fun onConnectionStateChange(
                    gatt: BluetoothGatt?,
                    status: Int,
                    newState: Int
                ) {
                    super.onConnectionStateChange(gatt, status, newState)
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        connected = true
                        serviceSelected = false
                        protocolDetectors.clear()

                        //change MTU to max crsf packet length + 3 to be abe to receive biggest CRSF packet from ELRS without need of fragmentation.
                        // Should be set on both ends (smaller from two is used).
                        //BTW HM-10 module always use MTU=23
                        try{
                            gatt?.requestMtu(64+3);
                        }
                        catch(e: NoSuchMethodError ) {
                            gatt?.discoverServices();
                        }

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

                override fun onMtuChanged(gatt: BluetoothGatt, mtu:Int, status:Int) {
                    gatt?.discoverServices()
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
                                                        val reg =
                                                            gatt.setCharacteristicNotification(
                                                                it,
                                                                false
                                                            )
                                                        if (reg) {
                                                            for (descriptor in it.getDescriptors()) {
                                                                descriptor.value =
                                                                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                                                                gatt.writeDescriptor(descriptor)
                                                            }
                                                        }
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
                            val registered =
                                gatt.setCharacteristicNotification(characteristic, true)
                            if (registered) {
                                for (descriptor in characteristic.getDescriptors()) {
                                    descriptor.value =
                                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    gatt.writeDescriptor(descriptor)
                                }
                            }
                            AsyncTask.execute {
                                Thread.sleep(10000)
                                if (!serviceSelected) {
                                    notifyCharacteristicList.forEach {
                                        val reg = gatt.setCharacteristicNotification(it, false)
                                        if (reg) {
                                            for (descriptor in it.getDescriptors()) {
                                                descriptor.value =
                                                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                                                gatt.writeDescriptor(descriptor)
                                            }
                                        }
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
