package crazydude.com.telemetry.protocol

import android.annotation.SuppressLint
import android.os.AsyncTask
import com.google.android.gms.maps.model.LatLng
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.io.File
import java.io.FileInputStream
import java.util.*
import kotlin.collections.ArrayList

class LogPlayer(val originalListener: DataDecoder.Listener) : DataDecoder.Listener {

    private var cachedData = ArrayList<Protocol.Companion.TelemetryData>()
    private var decodedCoordinates = ArrayList<LatLng>()
    private var dataReadyListener: DataReadyListener? = null
    private var currentPosition: Int = 0
    private var uniqueData = HashMap<Int, Int>()
    private lateinit var protocol: Protocol
    private var armed = false

    private val task = @SuppressLint("StaticFieldLeak") object :
        AsyncTask<File, Long, ArrayList<Protocol.Companion.TelemetryData>>() {

        override fun doInBackground(vararg file: File): ArrayList<Protocol.Companion.TelemetryData> {
            var logFile = FileInputStream(file[0])
            val arrayList = ArrayList<Protocol.Companion.TelemetryData>()
            var tempProtocol: Protocol? = null

            val tempDecoder = object : DataDecoder(this@LogPlayer) {
                override fun decodeData(data: Protocol.Companion.TelemetryData) {
                    arrayList.add(data)
                }
            }

            val protocolDetector = ProtocolDetector(object : ProtocolDetector.Callback {
                override fun onProtocolDetected(detectedProtocol: Protocol?) {
                    when (detectedProtocol) {
                        is FrSkySportProtocol -> {
                            tempProtocol =
                                FrSkySportProtocol(tempDecoder)
                            protocol = FrSkySportProtocol(this@LogPlayer)
                        }

                        is CrsfProtocol -> {
                            tempProtocol =
                                CrsfProtocol(tempDecoder)
                            protocol = CrsfProtocol(this@LogPlayer)
                        }

                        is LTMProtocol -> {
                            tempProtocol = LTMProtocol(tempDecoder)
                            protocol = LTMProtocol(this@LogPlayer)
                        }
                    }
                }
            })

            val buffer = ByteArray(1024)

            while (logFile.read(buffer) == buffer.size && tempProtocol == null) {
                for (byte in buffer) {
                    if (tempProtocol == null) {
                        protocolDetector.feedData(byte.toUByte().toInt())
                    } else {
                        break
                    }
                }
            }

            if (tempProtocol == null) {
                publishProgress(100)
            } else {
                logFile = FileInputStream(file[0])
                val size = (file[0].length() / 100).toInt()
                val bytes = ByteArray(size)
                var bytesRead = logFile.read(bytes)
                var allBytes = bytesRead
                while (bytesRead == size) {
                    for (i in 0 until bytesRead) {
                        tempProtocol?.process(bytes[i].toUByte().toInt())
                    }
                    publishProgress(((allBytes / file[0].length().toFloat()) * 100).toLong())
                    bytesRead = logFile.read(bytes)
                    allBytes += bytesRead
                }
            }

            return arrayList
        }

        override fun onProgressUpdate(vararg values: Long?) {
            values.let { dataReadyListener?.onUpdate(values[0]?.toInt() ?: 0) }
        }

        override fun onPostExecute(result: ArrayList<Protocol.Companion.TelemetryData>) {
            cachedData = result
            dataReadyListener?.onDataReady(result.size)
        }

    }

    fun load(file: File, dataReadyListener: DataReadyListener) {
        this.dataReadyListener = dataReadyListener
        task.execute(file)
    }

    fun seek(position: Int) {
        uniqueData.clear()
        decodedCoordinates.clear()
        val addToEnd: Boolean
        if (position > currentPosition) {
            for (i in currentPosition until position) {
                if (cachedData[i].telemetryType == Protocol.GPS || cachedData[i].telemetryType == Protocol.GPS_LATITUDE
                    || cachedData[i].telemetryType == Protocol.GPS_LONGITUDE
                ) {
                    if (armed) {
                        protocol.dataDecoder.decodeData(cachedData[i])
                    }
                } else if (cachedData[i].telemetryType == Protocol.FLYMODE) {
                    protocol.dataDecoder.decodeData(cachedData[i])
                } else {
                    uniqueData[cachedData[i].telemetryType] = i
                }
            }
            addToEnd = true
            currentPosition = position
        } else {
            for (i in 0 until position) {
                if (cachedData[i].telemetryType == Protocol.GPS || cachedData[i].telemetryType == Protocol.GPS_LATITUDE
                    || cachedData[i].telemetryType == Protocol.GPS_LONGITUDE
                ) {
                    if (armed) {
                        protocol.dataDecoder.decodeData(cachedData[i])
                    }
                } else if (cachedData[i].telemetryType == Protocol.FLYMODE) {
                    protocol.dataDecoder.decodeData(cachedData[i])
                } else {
                    uniqueData[cachedData[i].telemetryType] = i
                }
            }
            currentPosition = position
            addToEnd = false
        }
        uniqueData.entries.forEach {
            protocol.dataDecoder.decodeData(cachedData[it.value])
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
        if (latitude != 0.0 && longitude != 0.0) {
            decodedCoordinates.add(LatLng(latitude, longitude))
        }
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

    override fun onAirSpeed(speed: Float) {
        originalListener.onAirSpeed(speed)
    }

    override fun onPitchData(pitchAngle: Float) {
        originalListener.onPitchData(pitchAngle)
    }

    override fun onGSpeedData(speed: Float) {
        originalListener.onGSpeedData(speed)
    }

    override fun onSuccessDecode() {
        originalListener.onSuccessDecode()
    }

    override fun onFlyModeData(
        armed: Boolean,
        heading: Boolean,
        firstFlightMode: DataDecoder.Companion.FlyMode?,
        secondFlightMode: DataDecoder.Companion.FlyMode?
    ) {
        this.armed = armed
        originalListener.onFlyModeData(armed, heading, firstFlightMode, secondFlightMode)
    }

    interface DataReadyListener {
        fun onUpdate(percent: Int)
        fun onDataReady(size: Int)
    }
}