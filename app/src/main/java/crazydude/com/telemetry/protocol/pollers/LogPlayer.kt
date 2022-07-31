package crazydude.com.telemetry.protocol.pollers
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Environment
import androidx.core.content.ContextCompat
import crazydude.com.telemetry.maps.Position
import crazydude.com.telemetry.protocol.*
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.io.File
import java.io.FileInputStream
import java.io.PrintWriter
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class LogPlayer(val originalListener: DataDecoder.Listener) : DataDecoder.Listener {

    private var cachedData = ArrayList<Protocol.Companion.TelemetryData>()
    private var decodedCoordinates = ArrayList<Position>()
    private var dataReadyListener: DataReadyListener? = null
    private var currentPosition: Int = 0
    private var uniqueData = HashMap<Int, Int>()
    private lateinit var protocol: Protocol

    private var decodedAltitude : Float = -1f;
    private var decodedSpeed : Float = 0f;
    private var decodedHeading : Float = 0f;

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

            val protocolDetector =
                ProtocolDetector(object :
                    ProtocolDetector.Callback {
                    override fun onProtocolDetected(detectedProtocol: Protocol?) {
                        when (detectedProtocol) {
                            is FrSkySportProtocol -> {
                                tempProtocol =
                                    FrSkySportProtocol(
                                        tempDecoder
                                    )
                                protocol =
                                    FrSkySportProtocol(
                                        this@LogPlayer
                                    )
                            }

                            is CrsfProtocol -> {
                                tempProtocol =
                                    CrsfProtocol(
                                        tempDecoder
                                    )
                                protocol =
                                    CrsfProtocol(
                                        this@LogPlayer
                                    )
                            }

                            is LTMProtocol -> {
                                tempProtocol =
                                    LTMProtocol(
                                        tempDecoder
                                    )
                                protocol =
                                    LTMProtocol(
                                        this@LogPlayer
                                    )
                            }

                            is MAVLinkProtocol -> {
                                tempProtocol =
                                    MAVLinkProtocol(
                                        tempDecoder
                                    )
                                protocol =
                                    MAVLinkProtocol(
                                        this@LogPlayer
                                    )
                            }

                            is MAVLink2Protocol -> {
                                tempProtocol = MAVLink2Protocol(tempDecoder)
                                protocol = MAVLink2Protocol(this@LogPlayer)
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
            //exportGPX();
        }

    }

    fun load(file: File, dataReadyListener: DataReadyListener) {
        this.dataReadyListener = dataReadyListener
        task.execute(file)
    }

    fun seek(position: Int) {
        uniqueData.clear()
        decodedCoordinates.clear()
        var addToEnd: Boolean = false;
        if (position > currentPosition) {
            for (i in currentPosition until position) {
                if ( protocol.dataDecoder.isGPSData( cachedData[i].telemetryType )) {
                    protocol.dataDecoder.decodeData(cachedData[i])
                } else if (cachedData[i].telemetryType == Protocol.FLYMODE) {
                    protocol.dataDecoder.decodeData(cachedData[i])
                } else {
                    uniqueData[cachedData[i].telemetryType] = i
                }
            }
            addToEnd = true
            currentPosition = position
        } else if (position < currentPosition) {
            protocol.dataDecoder.restart()
            for (i in 0 until position) {
                if ( protocol.dataDecoder.isGPSData( cachedData[i].telemetryType )) {
                    protocol.dataDecoder.decodeData(cachedData[i])
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
            decodedCoordinates.add(Position(latitude, longitude))
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
        decodedHeading = heading;
        originalListener.onHeadingData(heading)
    }

    override fun onRSSIData(rssi: Int) {
        originalListener.onRSSIData(rssi)
    }

    override fun onGPSData(list: List<Position>, addToEnd: Boolean) {

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
        decodedAltitude = altitude;
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
        decodedSpeed = speed;
        originalListener.onGSpeedData(speed)
    }

    override fun onRCChannels(rcChannels:IntArray) {
        originalListener.onRCChannels(rcChannels)
    }

    override fun onStatusText(message : String) {
        originalListener.onStatusText(message)
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
        originalListener.onFlyModeData(armed, heading, firstFlightMode, secondFlightMode)
    }

    fun addHeader(fileWriter : PrintWriter )
    {
        fileWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<gpx\n" +
                "  version=\"1.0\"\n" +
                "  creator=\"telemetryViewer\"\n" +
                "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "  xmlns=\"http://www.topografix.com/GPX/1/0\"\n" +
                "  xsi:schemaLocation=\"http://www.topografix.com/GPX/1/0 http://www.topografix.com/GPX/1/0/gpx.xsd\">\n" +
                "<trk>\n" +
                "<trkseg>")
    }

    fun addFooter(fileWriter : PrintWriter )
    {
        fileWriter.write("</trkseg>\n" +
                "</trk>\n" +
                "</gpx>")
    }

    //https://github.com/Parrot-Developers/mavlink/blob/master/pymavlink/tools/mavtogpx.py
    fun exportGPX()
    {
        val dir = Environment.getExternalStoragePublicDirectory("TelemetryLogs")
        dir.mkdirs()
        val file = File(dir, "replay.gpx")

        var fileWriter = file.printWriter()
        addHeader( fileWriter );

        seek(0);

        decodedAltitude = -10000f;
        decodedSpeed = 0f;
        decodedHeading = 0f;

        var lastLon : Double = 0.0;
        var lastLat : Double = 0.0;

        var startTime = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

        for (i in 0 until cachedData.size) {
                protocol.dataDecoder.decodeData(cachedData[i])

                var output = false;
                if (decodedCoordinates.size > 0)
                {
                    if ( decodedCoordinates[0].lon != lastLon)
                    {
                        lastLon = decodedCoordinates[0].lon;
                        output= true;
                    }
                    if ( decodedCoordinates[0].lat != lastLat)
                    {
                        lastLat = decodedCoordinates[0].lat;
                        output= true;
                    }
                    decodedCoordinates.clear();
                }

                if ( output && (decodedAltitude != - 10000f) )
                {
                    var t = startTime + i * 1800000L / cachedData.size;
                    var s = "<trkpt lat=\"" + lastLat.toString() + "\" lon=\"" + lastLon.toString()  + "\">\n" +
                            "  <ele>" +  ((decodedAltitude + 127) ).toString()  + "</ele>\n" +
                            //"  <time>%s</time>\n" +
                            "  <course>" + decodedHeading.toString()  + "</course>\n" +
                            "  <speed>" + decodedSpeed.toString()  + "</speed>\n" +
                            "  <fix>3d</fix>\n" +
                            "  <time>" + sdf.format( Date(t) ) + "</time>\n" +
                            "</trkpt>";
                    fileWriter.write(s);
                }
        }

        addFooter(fileWriter);

        fileWriter.flush();
        fileWriter.close()
    }

    interface DataReadyListener {
        fun onUpdate(percent: Int)
        fun onDataReady(size: Int)
    }
}