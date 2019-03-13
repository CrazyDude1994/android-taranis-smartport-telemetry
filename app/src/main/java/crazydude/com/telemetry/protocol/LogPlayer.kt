package crazydude.com.telemetry.protocol

import android.annotation.SuppressLint
import android.os.AsyncTask
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.io.FileInputStream
import java.util.*
import kotlin.collections.ArrayList

class LogPlayer(val originalListener: DataDecoder.Listener) : DataDecoder.Listener {

    private var cachedData = ArrayList<FrSkySportProtocol.Companion.TelemetryData>()
    private var decodedCoordinates = ArrayList<LatLng>()
    private var dataReadyListener: DataReadyListener? = null
    private var currentPosition: Int = 0
    private var dataDecoder: DataDecoder = DataDecoder(this)
    private var uniqueData = HashMap<Int, Int>()

    private val task = @SuppressLint("StaticFieldLeak") object :
        AsyncTask<File, Long, ArrayList<FrSkySportProtocol.Companion.TelemetryData>>() {

        override fun doInBackground(vararg file: File): ArrayList<FrSkySportProtocol.Companion.TelemetryData> {
            val logFile = FileInputStream(file[0])
            val arrayList = ArrayList<FrSkySportProtocol.Companion.TelemetryData>()
            val protocol = FrSkySportProtocol(object : FrSkySportProtocol.Companion.DataListener {
                override fun onNewData(data: FrSkySportProtocol.Companion.TelemetryData) {
                    arrayList.add(data)
                }
            })

            val size = (file[0].length() / 100).toInt()
            val bytes = ByteArray(size)
            var bytesRead = logFile.read(bytes)
            var allBytes = bytesRead
            while (bytesRead == size) {
                for (i in 0 until bytesRead) {
                    protocol.process(bytes[i].toInt())
                }
                publishProgress(((allBytes / file[0].length().toFloat()) * 100).toLong())
                bytesRead = logFile.read(bytes)
                allBytes += bytesRead
            }

            return arrayList
        }

        override fun onProgressUpdate(vararg values: Long?) {
            values.let { dataReadyListener?.onUpdate(values[0]?.toInt() ?: 0) }
        }

        override fun onPostExecute(result: ArrayList<FrSkySportProtocol.Companion.TelemetryData>) {
            cachedData = result
            dataReadyListener?.onDataReady(result.size)
        }

    }

    companion object {
        private const val TAG = "LogPlayer"
    }

    fun load(file: File, dataReadyListener: DataReadyListener) {
        this.dataReadyListener = dataReadyListener
        task.execute(file)
    }

    fun seek(position: Int) {
        uniqueData.clear()
        val addToEnd: Boolean
        decodedCoordinates.clear()
        if (position > currentPosition) {
            for (i in currentPosition until position) {
                if (cachedData[i].telemetryType == FrSkySportProtocol.GPS) {
                    dataDecoder.onNewData(cachedData[i])
                } else {
                    uniqueData[cachedData[i].telemetryType] = i
                }
            }
            addToEnd = true
            currentPosition = position
        } else {
            for (i in 0 until position) {
                if (cachedData[i].telemetryType == FrSkySportProtocol.GPS) {
                    dataDecoder.onNewData(cachedData[i])
                } else {
                    uniqueData[cachedData[i].telemetryType] = i
                }
            }
            currentPosition = position
            addToEnd = false
        }
        uniqueData.entries.forEach {
            dataDecoder.onNewData(cachedData[it.value])
        }
        originalListener.onGPSData(decodedCoordinates, addToEnd)
    }

    override fun onConnectionFailed() {
    }

    override fun onFuelData(fuel: Int) {
        originalListener.onFuelData(fuel)
    }

    override fun onConnected() {
    }

    override fun onGPSData(latitude: Double, longitude: Double) {
        decodedCoordinates.add(LatLng(latitude, longitude))
    }

    override fun onVBATData(voltage: Float) {
        originalListener.onVBATData(voltage)
    }

    override fun onCellVoltageData(voltage: Float) {
        originalListener.onCellVoltageData(voltage)
    }

    override fun onCurrentData(current: Float) {
        originalListener.onCurrentData(current)
    }

    override fun onHeadingData(heading: Float) {
        originalListener.onHeadingData(heading)
    }

    override fun onRSSIData(rssi: Int) {
        originalListener.onRSSIData(rssi)
    }

    override fun onGPSData(list: List<LatLng>, addToEnd: Boolean) {

    }

    override fun onDisconnected() {
    }

    override fun onGPSState(satellites: Int, gpsFix: Boolean) {
        originalListener.onGPSState(satellites, gpsFix)
    }

    override fun onVSpeedData(vspeed: Float) {
        originalListener.onVSpeedData(vspeed)
    }

    override fun onAltitudeData(altitude: Float) {
        originalListener.onAltitudeData(altitude)
    }

    override fun onGPSAltitudeData(altitude: Float) {
        originalListener.onGPSAltitudeData(altitude)
    }

    override fun onDistanceData(distance: Int) {
        originalListener.onDistanceData(distance)
    }

    override fun onRollData(rollAngle: Float) {
        originalListener.onRollData(rollAngle)
    }

    override fun onPitchData(pitchAngle: Float) {
        originalListener.onPitchData(pitchAngle)
    }

    override fun onGSpeedData(speed: Float) {
        originalListener.onGSpeedData(speed)
    }

    override fun onFlyModeData(
        armed: Boolean,
        heading: Boolean,
        firstFlightMode: DataDecoder.Companion.FlyMode,
        secondFlightMode: DataDecoder.Companion.FlyMode?
    ) {
        originalListener.onFlyModeData(armed, heading, firstFlightMode, secondFlightMode)
    }

    interface DataReadyListener {
        fun onUpdate(percent: Int)
        fun onDataReady(size: Int)
    }
}