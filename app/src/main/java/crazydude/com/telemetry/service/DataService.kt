package crazydude.com.telemetry.service

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDeviceConnection
import android.os.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import crazydude.com.telemetry.R
import crazydude.com.telemetry.api.*
import crazydude.com.telemetry.manager.PreferenceManager
import crazydude.com.telemetry.maps.Position
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import crazydude.com.telemetry.protocol.pollers.BluetoothDataPoller
import crazydude.com.telemetry.protocol.pollers.BluetoothLeDataPoller
import crazydude.com.telemetry.protocol.pollers.DataPoller
import crazydude.com.telemetry.protocol.pollers.UsbDataPoller
import crazydude.com.telemetry.ui.MapsActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList

class DataService : Service(), DataDecoder.Listener {

    private var dataPoller: DataPoller? = null
    private var dataListener: DataDecoder.Listener? = null
    private val dataBinder = DataBinder()
    private var hasGPSFix = false
    private var isArmed = false
    private var satellites = 0
    private var lastLatitude: Double = 0.0
    private var lastLongitude: Double = 0.0
    private var lastAltitude: Float = 0.0f
    private var lastSpeed: Float = 0.0f
    private var lastHeading: Float = 0.0f
    private val apiHandler = Handler()
    private var logFile : OutputStream? = null
    private var device: BluetoothDevice? = null
    private lateinit var preferenceManager: PreferenceManager
    val points: ArrayList<Position> = ArrayList()

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
            .setContentText("Telemetry service is running. To stop - disconnect and close the app")
            .setContentTitle("Telemetry service is running")
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

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun connect(device: BluetoothDevice, logFile: OutputStream, isBLE: Boolean) {
        try {
            dataPoller?.disconnect()

            this.device = device
            this.logFile = logFile

            if (isBLE) {

                dataPoller = BluetoothLeDataPoller(
                    this,
                    device,
                    this,
                    logFile
                )
            } else {
                val socket =
                    device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                dataPoller =
                    BluetoothDataPoller(
                        socket,
                        this,
                        logFile
                    )
            }
        } catch (e: IOException) {
            onConnectionFailed()
        }
    }

    fun connect(serialPort: UsbSerialPort, connection: UsbDeviceConnection, logFile : OutputStream) {
        dataPoller = UsbDataPoller(
            this,
            serialPort,
            connection,
            logFile
        )
    }

    fun setDataListener(dataListener: DataDecoder.Listener?) {
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
        Toast.makeText(this, "Failed to connect to bluetooth", Toast.LENGTH_LONG).show()
    }

    override fun onFuelData(fuel: Int) {
        dataListener?.onFuelData(fuel)
    }

    override fun onConnected() {
        dataListener?.onConnected()

        if (preferenceManager.isSendDataEnabled()) {
            createSession()
        }
    }


    fun createSession() {
        if (!isConnected()) {
            return
        }
        ApiManager.apiService.createSession(
            SessionCreateRequest(preferenceManager.getCallsign(), preferenceManager.getModel())
        ).enqueue(object : Callback<SessionCreateResponse?> {
            override fun onFailure(call: Call<SessionCreateResponse?>, t: Throwable) {
                Handler()
                    .postDelayed({ createSession() }, 5000)
            }

            override fun onResponse(
                call: Call<SessionCreateResponse?>,
                response: Response<SessionCreateResponse?>
            ) {
                try {
                    response?.body()?.let {
                        it?.sessionId.let { sendTelemetryData(it) }
                    }
                } catch (e: NullPointerException) {
                    // Unknown
                }
            }
        })
    }

    fun sendTelemetryData(sessionId: String) {
        if (!isConnected()) {
            return
        }
        apiHandler.postDelayed({
            if (hasGPSFix && isArmed) {
                ApiManager.apiService.sendData(
                    AddLogRequest(
                        sessionId,
                        lastLatitude,
                        lastLongitude,
                        lastAltitude,
                        lastHeading,
                        lastSpeed
                    )
                ).enqueue(object : Callback<AddLogResponse?> {
                    override fun onFailure(call: Call<AddLogResponse?>, t: Throwable) {
                        sendTelemetryData(sessionId)
                    }

                    override fun onResponse(
                        call: Call<AddLogResponse?>,
                        response: Response<AddLogResponse?>
                    ) {
                        try {
                            sendTelemetryData(sessionId)
                        } catch (e: NullPointerException) {

                        }
                    }
                })
            } else {
                sendTelemetryData(sessionId)
            }
        }, 5000)
    }

    override fun onGPSData(latitude: Double, longitude: Double) {
        if (hasGPSFix) {
            points.add(Position(latitude, longitude))
        }
        lastLatitude = latitude
        lastLongitude = longitude
        dataListener?.onGPSData(latitude, longitude)
    }

    override fun onGPSData(list: List<Position>, addToEnd: Boolean) {

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
        lastHeading = heading
        dataListener?.onHeadingData(heading)
    }

    override fun onAirSpeed(speed: Float) {
        if (preferenceManager.usePitotTube()) {
            lastSpeed = speed
        }
        dataListener?.onAirSpeed(speed)
    }

    override fun onSuccessDecode() {
        dataListener?.onSuccessDecode()
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
        lastAltitude = altitude
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
        if (!preferenceManager.usePitotTube()) {
            lastSpeed = speed
        }
        dataListener?.onGSpeedData(speed)
    }

    override fun onFlyModeData(
        armed: Boolean,
        heading: Boolean,
        firstFlightMode: DataDecoder.Companion.FlyMode?,
        secondFlightMode: DataDecoder.Companion.FlyMode?
    ) {
        isArmed = armed
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