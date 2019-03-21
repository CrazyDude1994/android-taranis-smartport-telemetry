package crazydude.com.telemetry.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import crazydude.com.telemetry.R
import crazydude.com.telemetry.manager.PreferenceManager
import crazydude.com.telemetry.protocol.BluetoothDataPoller
import crazydude.com.telemetry.protocol.DataDecoder
import crazydude.com.telemetry.ui.MapsActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class DataService : Service(), DataDecoder.Listener {

    private var dataPoller: BluetoothDataPoller? = null
    private var dataListener: DataDecoder.Listener? = null
    private val dataBinder = DataBinder()
    private var hasGPSFix = false
    private var satellites = 0
    private lateinit var preferenceManager: PreferenceManager
    val points: ArrayList<LatLng> = ArrayList()

    override fun onCreate() {
        super.onCreate()

        preferenceManager = PreferenceManager(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel =
                NotificationChannel("bt_channel", "Bluetooth", importance)
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }


        val notification = NotificationCompat.Builder(this, "bt_channel")
            .setContentText("Telemetry service is running")
            .setContentTitle("Telemetry service is running. To stop - disconnect and close the app")
            .setContentIntent(PendingIntent.getActivity(this, -1, Intent(this, MapsActivity::class.java), 0))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        return Service.START_REDELIVER_INTENT
    }

    inner class DataBinder : Binder() {
        fun getService(): DataService = this@DataService
    }

    fun connect(device: BluetoothDevice) {
        var fileOutputStream: FileOutputStream? = null
        var csvFileOutputStream: FileOutputStream? = null
        if (preferenceManager.isLoggingEnabled()
            && ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val name = SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())
            val dir = Environment.getExternalStoragePublicDirectory("TelemetryLogs")
            dir.mkdirs()
            val file = File(dir, "$name.log")
            val csvFile = File(dir, "$name.csv")
            fileOutputStream = FileOutputStream(file)
            csvFileOutputStream = FileOutputStream(csvFile)
        }
        try {
            val socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
            dataPoller?.disconnect()
            dataPoller = BluetoothDataPoller(socket, this, fileOutputStream, csvFileOutputStream)
        } catch (e: IOException) {
            Toast.makeText(this, "Failed to connect to bluetooth", Toast.LENGTH_LONG).show()
        }
    }

    fun setDataListener(dataListener: DataDecoder.Listener?) {
        this.dataListener = dataListener
        if (dataListener != null) {
            runOnMainThread(Runnable {
                dataListener.onGPSState(satellites, hasGPSFix)
            })
        } else {
            if (!isConnected()) {
                stopSelf()
            }
        }
    }

    fun isConnected(): Boolean {
        return dataPoller != null
    }

    override fun onBind(intent: Intent): IBinder? {
        return dataBinder
    }

    override fun onDestroy() {
        super.onDestroy()
        dataPoller?.disconnect()
        dataPoller = null
    }

    override fun onConnectionFailed() {
        runOnMainThread(Runnable {
            dataListener?.onConnectionFailed()
        })
        dataPoller = null
    }

    override fun onFuelData(fuel: Int) {
        runOnMainThread(Runnable {
            dataListener?.onFuelData(fuel)
        })
    }

    override fun onConnected() {
        runOnMainThread(Runnable {
            dataListener?.onConnected()
        })
    }

    override fun onGPSData(latitude: Double, longitude: Double) {
        if (hasGPSFix) {
            points.add(LatLng(latitude, longitude))
        }
        runOnMainThread(Runnable {
            dataListener?.onGPSData(latitude, longitude)
        })
    }

    override fun onGPSData(list: List<LatLng>, addToEnd: Boolean) {

    }

    override fun onVBATData(voltage: Float) {
        runOnMainThread(Runnable {
            dataListener?.onVBATData(voltage)
        })
    }

    override fun onCellVoltageData(voltage: Float) {
        runOnMainThread(Runnable {
            dataListener?.onCellVoltageData(voltage)
        })
    }

    override fun onCurrentData(current: Float) {
        runOnMainThread(Runnable {
            dataListener?.onCurrentData(current)
        })
    }

    override fun onHeadingData(heading: Float) {
        runOnMainThread(Runnable {
            dataListener?.onHeadingData(heading)
        })
    }

    override fun onRSSIData(rssi: Int) {
        runOnMainThread(Runnable {
            dataListener?.onRSSIData(rssi)
        })
    }

    override fun onDisconnected() {
        points.clear()
        runOnMainThread(Runnable {
            dataListener?.onDisconnected()
        })
        dataPoller = null
        satellites = 0
        hasGPSFix = false
    }

    override fun onGPSState(satellites: Int, gpsFix: Boolean) {
        hasGPSFix = gpsFix
        runOnMainThread(Runnable {
            dataListener?.onGPSState(satellites, gpsFix)
        })
    }

    override fun onVSpeedData(vspeed: Float) {
        runOnMainThread(Runnable {
            dataListener?.onVSpeedData(vspeed)
        })
    }

    override fun onAltitudeData(altitude: Float) {
        runOnMainThread(Runnable {
            dataListener?.onAltitudeData(altitude)
        })
    }

    override fun onGPSAltitudeData(altitude: Float) {
        runOnMainThread(Runnable {
            dataListener?.onGPSAltitudeData(altitude)
        })
    }

    override fun onDistanceData(distance: Int) {
        runOnMainThread(Runnable {
            dataListener?.onDistanceData(distance)
        })
    }

    override fun onRollData(rollAngle: Float) {
        runOnMainThread(Runnable {
            dataListener?.onRollData(rollAngle)
        })
    }

    override fun onPitchData(pitchAngle: Float) {
        runOnMainThread(Runnable {
            dataListener?.onPitchData(pitchAngle)
        })
    }

    override fun onGSpeedData(speed: Float) {
        runOnMainThread(Runnable { dataListener?.onGSpeedData(speed) })
    }

    override fun onFlyModeData(
        armed: Boolean,
        heading: Boolean,
        firstFlightMode: DataDecoder.Companion.FlyMode,
        secondFlightMode: DataDecoder.Companion.FlyMode?
    ) {
        runOnMainThread(Runnable {
            dataListener?.onFlyModeData(armed, heading, firstFlightMode, secondFlightMode)
        })

    }

    private fun runOnMainThread(runnable: Runnable) {
        Handler(Looper.getMainLooper())
            .post {
                runnable.run()
            }
    }

    fun disconnect() {
        points.clear()
        dataPoller?.disconnect()
        dataPoller = null
        satellites = 0
        hasGPSFix = false
    }
}