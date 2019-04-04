package crazydude.com.bt_debug

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var broadcastReceiver: BroadcastReceiver? = null

    companion object {
        private const val REQUEST_ENABLE_BT_BL: Int = 0
        private const val REQUEST_ENABLE_BT_BLE: Int = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        blConnectButton.setOnClickListener {
            connectBl()
        }

        bleConnectButton.setOnClickListener {
            connectBle()
        }
    }

    @SuppressLint("NewApi")
    private fun connectBle() {
        if (checkBluetooth(REQUEST_ENABLE_BT_BLE)) {
            val deviceNamesAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
            val deviceList = ArrayList<BluetoothDevice>()
            val callback = BluetoothAdapter.LeScanCallback { bluetoothDevice, p1, p2 ->
                bluetoothDevice?.let {
                    if (!deviceList.contains(it)) {
                        deviceList.add(it)
                        deviceNamesAdapter.add(it.name ?: it.address)
                    }
                }
            }
            AlertDialog.Builder(this)
                .setAdapter(deviceNamesAdapter) { dialogInterface, i ->
                    connectBle(deviceList[i])
                }
                .setOnDismissListener { bluetoothAdapter.stopLeScan(callback) }
                .show()
            bluetoothAdapter.startLeScan(callback)
        }
    }

    @SuppressLint("NewApi")
    private fun connectBle(bluetoothDevice: BluetoothDevice) {
        bluetoothDevice.connectGatt(this, false,
            object : BluetoothGattCallback() {
                private var fileOutputStream: FileOutputStream? = null

                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    super.onConnectionStateChange(gatt, status, newState)
                    fileOutputStream?.write("State changed $status, $newState\r\n".toByteArray())
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        gatt?.discoverServices()
                        fileOutputStream = getLogFileOutputStream()
                        fileOutputStream?.write("Connected as BLE\r\n".toByteArray())
                        disconnectButton.setOnClickListener {
                            gatt?.disconnect()
                        }
                        switchToConnectedState()
                    } else {
                        fileOutputStream?.write("Disconnected\r\n".toByteArray())
                        fileOutputStream?.close()
                        switchToDisconnected()
                        gatt?.close()
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt?,
                    characteristic: BluetoothGattCharacteristic?
                ) {
                    super.onCharacteristicChanged(gatt, characteristic)
                    fileOutputStream?.write(characteristic?.value)
                    GlobalScope.launch(Dispatchers.Main) {
                        if (logTextView.text.length > 2048) {
                            logTextView.text = ""
                        }
                        characteristic?.value?.forEach {
                            logTextView.text = "${logTextView.text}:${Integer.toHexString(it.toInt())}"
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    super.onServicesDiscovered(gatt, status)

                    fileOutputStream?.write("On service discovered $status\r\n".toByteArray())

                    val list = gatt?.services?.flatMap { it.characteristics }
                        ?.filter { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == BluetoothGattCharacteristic.PROPERTY_NOTIFY }

                    if (list?.isEmpty() != false) {
                        fileOutputStream?.write("No notify services found\r\n".toByteArray())
                    } else {
                        GlobalScope.launch(Dispatchers.Main) {
                            AlertDialog.Builder(this@MainActivity)
                                .setAdapter(
                                    ArrayAdapter<String>(
                                        this@MainActivity,
                                        android.R.layout.simple_list_item_1,
                                        list.map { it.uuid.toString() }
                                    )
                                ) { dialogInterface, i ->
                                    fileOutputStream?.write("Selected service: ${list[i].uuid}\r\n".toByteArray())
                                    gatt.setCharacteristicNotification(list[i], true)
                                }
                                .show()
                        }
                    }
                }
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        broadcastReceiver?.let {
            unregisterReceiver(it)
        }
    }

    private fun connectBl() {
        if (checkBluetooth(REQUEST_ENABLE_BT_BL)) {
            val deviceNamesAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
            val deviceList = ArrayList<BluetoothDevice>()
            AlertDialog.Builder(this)
                .setAdapter(deviceNamesAdapter) { dialogInterface, i ->
                    connectBl(deviceList[i])
                }
                .setOnDismissListener { bluetoothAdapter.cancelDiscovery() }
                .show()
            broadcastReceiver?.let {
                unregisterReceiver(it)
            }
            broadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(p0: Context?, intent: Intent?) {
                    intent?.let {
                        val bluetoothDevice = it.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice
                        if (!deviceList.contains(bluetoothDevice)) {
                            deviceList.add(bluetoothDevice)
                            deviceNamesAdapter.add(bluetoothDevice.name ?: bluetoothDevice.address)
                        }
                    }
                }
            }
            registerReceiver(broadcastReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            bluetoothAdapter.startDiscovery()
        }
    }

    private fun connectBl(bluetoothDevice: BluetoothDevice) {
        bluetoothAdapter.cancelDiscovery()
        try {
            val bluetoothSocket =
                bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
            try {
                bluetoothSocket.connect()
                if (bluetoothSocket.isConnected) {
                    val fileOutputStream = getLogFileOutputStream()
                    switchToConnectedState()
                    fileOutputStream.write("Connected as BL\r\n".toByteArray())
                    GlobalScope.launch {
                        while (bluetoothSocket.isConnected) {
                            val data = bluetoothSocket.inputStream.read()
                            fileOutputStream.write(data)
                            withContext(Dispatchers.Main) {
                                if (logTextView.text.length > 2048) {
                                    logTextView.text = ""
                                }
                                logTextView.text = "${logTextView.text}:${Integer.toHexString(data)}"
                            }
                        }
                        fileOutputStream.write("Disconnected\r\n".toByteArray())
                        switchToDisconnected()
                        fileOutputStream.close()
                    }
                } else {
                    Toast.makeText(this, "Failed to connect (no error)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this, "Failed to connect (${e.message})", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Failed to create socket (${e.message})", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchToConnectedState() {
        GlobalScope.launch(Dispatchers.Main) {
            buttonsLayout.visibility = View.GONE
            logTextView.visibility = View.VISIBLE
            disconnectButton.visibility = View.VISIBLE
            Toast.makeText(this@MainActivity, "Connected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchToDisconnected() {
        GlobalScope.launch(Dispatchers.Main) {
            buttonsLayout.visibility = View.VISIBLE
            logTextView.visibility = View.GONE
            disconnectButton.visibility = View.GONE
            logTextView.text = ""
            Toast.makeText(this@MainActivity, "Disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLogFileOutputStream(): FileOutputStream {
        val dir = Environment.getExternalStoragePublicDirectory("BluetoothLogs")
        dir.mkdirs()
        val file = File(dir, "btlog.log")
        return FileOutputStream(file, true)
    }

    private fun checkBluetooth(requestId: Int): Boolean {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, requestId)
            return false
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_DENIED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION),
                    requestId
                )
                return false
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_DENIED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    requestId
                )
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                REQUEST_ENABLE_BT_BLE -> {
                    connectBle()
                }
                REQUEST_ENABLE_BT_BL -> {
                    connectBl()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_ENABLE_BT_BL -> {
                    connectBl()
                }
                REQUEST_ENABLE_BT_BLE -> {
                    connectBle()
                }
            }
        }
    }
}
