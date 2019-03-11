package crazydude.com.telemetry.service

import android.app.*
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import crazydude.com.telemetry.R
import crazydude.com.telemetry.manager.PreferenceManager
import crazydude.com.telemetry.protocol.DataPoller
import crazydude.com.telemetry.ui.MapsActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class DataService : Service(), DataPoller.Listener {

    private var dataPoller: DataPoller? = null
    private var dataListener: DataPoller.Listener? = null
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
            dataPoller = DataPoller(socket, this, fileOutputStream, csvFileOutputStream)
        } catch (e: IOException) {
            Toast.makeText(this, "Failed to connect to bluetooth", Toast.LENGTH_LONG).show()
        }
    }

    fun setDataListener(dataListener: DataPoller.Listener?) {
        this.dataListener = dataListener
        if (dataListener != null) {
            dataListener.onGPSState(satellites, hasGPSFix)
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
        dataListener?.onConnectionFailed()
        dataPoller = null
    }

    override fun onFuelData(fuel: Int) {
        dataListener?.onFuelData(fuel)
    }

    override fun onConnected() {
        dataListener?.onConnected()
    }

    override fun onGPSData(latitude: Double, longitude: Double) {
        if (hasGPSFix) {
            points.add(LatLng(latitude, longitude))
        }
        dataListener?.onGPSData(latitude, longitude)
    }

    override fun onVBATData(voltage: Float) {
        dataListener?.onVBATData(voltage)
    }

    override fun onCellVoltageData(voltage: Float) {
        dataListener?.onCellVoltageData(voltage)
    }

    override fun onCurrentData(current: Float) {
        dataListener?.onCurrentData(current)
    }

    override fun onHeadingData(heading: Float) {
        dataListener?.onHeadingData(heading)
    }

    override fun onRSSIData(rssi: Int) {
        dataListener?.onRSSIData(rssi)
    }

    override fun onDisconnected() {
        points.clear()
        dataListener?.onDisconnected()
        dataPoller = null
        satellites = 0
        hasGPSFix = false
    }

    override fun onGPSState(satellites: Int, gpsFix: Boolean) {
        hasGPSFix = gpsFix
        dataListener?.onGPSState(satellites, gpsFix)
    }

    override fun onVSpeedData(vspeed: Float) {
        dataListener?.onVSpeedData(vspeed)
    }

    override fun onAltitudeData(altitude: Float) {
        dataListener?.onAltitudeData(altitude)
    }

    override fun onGPSAltitudeData(altitude: Float) {
        dataListener?.onGPSAltitudeData(altitude)
    }

    override fun onDistanceData(distance: Int) {
        dataListener?.onDistanceData(distance)
    }

    override fun onRollData(rollAngle: Float) {
        dataListener?.onRollData(rollAngle)
    }

    override fun onPitchData(pitchAngle: Float) {
        dataListener?.onPitchData(pitchAngle)
    }

    override fun onGSpeedData(speed: Float) {
        dataListener?.onGSpeedData(speed)
    }

    override fun onFlyModeData(
        armed: Boolean,
        heading: Boolean,
        firstFlightMode: DataPoller.Companion.FlyMode,
        secondFlightMode: DataPoller.Companion.FlyMode?
    ) {
        dataListener?.onFlyModeData(armed, heading, firstFlightMode, secondFlightMode)
    }

    fun disconnect() {
        points.clear()
        dataPoller?.disconnect()
        dataPoller = null
        satellites = 0
        hasGPSFix = false
    }
}