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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.hoho.android.usbserial.driver.UsbSerialPort
import crazydude.com.telemetry.R
import crazydude.com.telemetry.api.*
import crazydude.com.telemetry.manager.PreferenceManager
import crazydude.com.telemetry.maps.Position
import crazydude.com.telemetry.protocol.TelemetryModel
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import crazydude.com.telemetry.protocol.pollers.*
import crazydude.com.telemetry.ui.MapsActivity
import crazydude.com.telemetry.utils.FileLogger
import crazydude.com.telemetry.utils.LogFile
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList

class DataService : Service(), DataDecoder.Listener {

    enum class ConnectionState {
        DISCONNECTED, CONNECTED, CONNECTING, REPLAY
    }

    private var dataPoller: DataPoller? = null
    private var logPlayer = LogPlayer(this)
    private val dataBinder = DataBinder()
    private val apiHandler = Handler()
    private var logFile: OutputStream? = null
    private var device: BluetoothDevice? = null
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var fileLogger: FileLogger
    private var telemetryModel = TelemetryModel()
    private val mutableTelemetryLiveData = MutableLiveData(telemetryModel)
    private var mutableConnectionStateLiveData = MutableLiveData(ConnectionState.DISCONNECTED)

    val telemetryLiveData = mutableTelemetryLiveData as LiveData<TelemetryModel>
    val connectionStateLiveData = mutableConnectionStateLiveData as LiveData<ConnectionState>

    override fun onCreate() {
        super.onCreate()

        preferenceManager = PreferenceManager(this)
        fileLogger = FileLogger(this)

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
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    -1,
                    Intent(this, MapsActivity::class.java),
                    0
                )
            )
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
        mutableConnectionStateLiveData.postValue(ConnectionState.CONNECTING)
        try {
            fileLogger.log(
                "Connect to bl. Type[${device.type}] isBle[$isBLE] bondState[${device.bondState}] uuids[${
                    device.uuids?.joinToString(
                        ","
                    )
                }]"
            )
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
            fileLogger.log("Connect exception: ${e.message}")
            onConnectionFailed()
        }
    }

    fun connect(serialPort: UsbSerialPort, connection: UsbDeviceConnection, logFile: OutputStream) {
        fileLogger.log("Usb connection")
        dataPoller = UsbDataPoller(
            this,
            serialPort,
            connection,
            logFile
        )
    }

/*
    fun setPollerListener(pollerListener: PollerListener?) {
//        this.pollerListener = pollerListener
        if (dataPoller == null && !isConnected()) {
            stopSelf()
        }
    }
*/

    fun isConnected(): Boolean {
        return dataPoller != null
    }

    override fun onBind(intent: Intent): IBinder {
        return dataBinder
    }

    override fun onDestroy() {
        super.onDestroy()
        dataPoller?.disconnect()
        dataPoller = null
    }

    override fun onConnectionFailed() {
        mutableConnectionStateLiveData.postValue(ConnectionState.DISCONNECTED)
        dataPoller = null
        Toast.makeText(this, "Failed to connect to bluetooth", Toast.LENGTH_LONG).show()
    }

    override fun onFuelData(fuel: Int) {
        telemetryModel.fuel = fuel
    }

    override fun onConnected() {

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
            if (telemetryModel.gpsFix && telemetryModel.armed) {
                ApiManager.apiService.sendData(
                    AddLogRequest(
                        sessionId,
                        telemetryModel.position.last().lat,
                        telemetryModel.position.last().lon,
                        telemetryModel.altitude,
                        telemetryModel.heading,
                        if (preferenceManager.usePitotTube()) telemetryModel.airSpeed else telemetryModel.gpsSpeed
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
        telemetryModel.position.add(Position(latitude, longitude))
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onGPSData(list: List<Position>, addToEnd: Boolean) {
        if (!addToEnd) {
            telemetryModel.position.clear()
        }
        telemetryModel.position.addAll(list)
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onVBATData(voltage: Float) {
        telemetryModel.vbat = voltage
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onCellVoltageData(voltage: Float) {
        telemetryModel.cellVoltage = voltage
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onCurrentData(current: Float) {
        telemetryModel.current = current
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onHeadingData(heading: Float) {
        telemetryModel.heading = heading
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onAirSpeed(speed: Float) {
        telemetryModel.airSpeed = speed
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onSuccessDecode() {
//        dataListener?.onSuccessDecode()
    }

    override fun onRSSIData(rssi: Int) {
    }

    override fun onDisconnected() {
//        dataListener?.onDisconnected()
        dataPoller = null
    }

    override fun onGPSState(satellites: Int, gpsFix: Boolean) {
        telemetryModel.satelliteCount = satellites
        telemetryModel.gpsFix = gpsFix
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onVSpeedData(vspeed: Float) {
        telemetryModel.vspeed = vspeed
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onAltitudeData(altitude: Float) {
        telemetryModel.altitude = altitude
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onGPSAltitudeData(altitude: Float) {
        telemetryModel.gpsAltitude = altitude
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onDistanceData(distance: Int) {
        telemetryModel.distance = distance.toFloat()
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onRollData(rollAngle: Float) {
        telemetryModel.roll = rollAngle
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onPitchData(pitchAngle: Float) {
        telemetryModel.pitch = pitchAngle
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onGSpeedData(speed: Float) {
        telemetryModel.gpsSpeed = speed
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    override fun onFlyModeData(
        armed: Boolean,
        heading: Boolean,
        firstFlightMode: DataDecoder.Companion.FlyMode?,
        secondFlightMode: DataDecoder.Companion.FlyMode?
    ) {
        telemetryModel.flightMode1 = firstFlightMode
        telemetryModel.flightMode2 = secondFlightMode
        telemetryModel.armed = armed
        telemetryModel.isHeadingMode = heading
        mutableTelemetryLiveData.postValue(telemetryModel)
    }

    fun disconnect() {
        dataPoller?.disconnect()
        dataPoller = null
    }

    fun seekReplay(position: Int) {
        logPlayer.seek(position)
    }

    fun startReplay(file: LogFile, dataReadyListener: LogPlayer.DataReadyListener) {
        logPlayer.load(file, dataReadyListener)
        mutableConnectionStateLiveData.postValue(ConnectionState.REPLAY)
    }

    fun stopReplay() {
        logPlayer.stopReplay()
        telemetryModel = TelemetryModel()
        mutableTelemetryLiveData.postValue(telemetryModel)
        mutableConnectionStateLiveData.postValue(ConnectionState.DISCONNECTED)
    }
}