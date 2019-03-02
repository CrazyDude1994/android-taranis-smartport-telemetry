package crazydude.com.telemetry

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import com.google.android.gms.maps.model.LatLng
import crazydude.com.telemetry.protocol.DataPoller
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class DataService : Service(), DataPoller.Listener {

    private var dataPoller: DataPoller? = null
    private var dataListener: DataPoller.Listener? = null
    private val dataBinder = DataBinder()
    private var hasGPSFix = false
    private var satellites = 0
    val points: ArrayList<LatLng> = ArrayList()

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        return Service.START_REDELIVER_INTENT
    }

    inner class DataBinder : Binder() {
        fun getService(): DataService = this@DataService
    }

    fun connect(device: BluetoothDevice) {
        val socket = device.createRfcommSocketToServiceRecord(device.uuids[0].uuid)
        val name = SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())
        val dir = Environment.getExternalStoragePublicDirectory("TelemetryLogs")
        dir.mkdirs()
        val file = File(dir, "$name.log")
        dataPoller?.disconnect()
        dataPoller = DataPoller(socket, this, FileOutputStream(file))
    }

    fun setDataListener(dataListener: DataPoller.Listener?) {
        this.dataListener = dataListener
        if (dataListener != null) {
            dataListener.onGPSState(satellites, hasGPSFix)
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