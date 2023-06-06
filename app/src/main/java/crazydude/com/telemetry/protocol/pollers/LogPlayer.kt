package crazydude.com.telemetry.protocol.pollers
import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.Environment
import android.widget.Toast
import crazydude.com.telemetry.maps.Position
import crazydude.com.telemetry.protocol.*
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.io.File
import java.io.FileInputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class LogPlayer(val originalListener: DataDecoder.Listener) : DataDecoder.Listener {

    private var cachedData = ArrayList<Protocol.Companion.TelemetryData>()
    private var decodedCoordinates = ArrayList<Position>()
    private var hasGPSFix = false
    private var satellites = 0;
    private var dataReadyListener: DataReadyListener? = null
    public var currentPosition: Int = 0
    private var uniqueData = HashMap<Int, Int>()
    private var uniqueDataIndex = HashMap<Int, Int>()
    private lateinit var protocol: Protocol

    private var decodedAltitude : Float = -1f;
    private var decodedSpeed : Float = 0f;
    private var decodedHeading : Float = 0f;

    private var statusTextExpire : Int = 0;

    private var fireGPSState = false;

    private var mTimer: Timer? = null

    private var totalPlaybackDurationMS : Int = 30000;

    public var launchPointMSLAltitude = 0;

    //async task used to load file, detect protocol and decode packets into arrayList
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
                                dataReadyListener?.onProtocolDetected("FrSky")
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
                                dataReadyListener?.onProtocolDetected("CRSF")
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
                                dataReadyListener?.onProtocolDetected("LTM")
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
                                dataReadyListener?.onProtocolDetected("Mavlink v1")
                            }

                            is MAVLink2Protocol -> {
                                tempProtocol = MAVLink2Protocol(tempDecoder)
                                protocol = MAVLink2Protocol(this@LogPlayer)
                                dataReadyListener?.onProtocolDetected("Mavlink v2")
                            }
                        }
                    }
                })

            val buffer = ByteArray(1024)

            //feed protocolDetector until protocol is detected and
            //tempProtocol and protocol are assigned correct protocol decoder
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
                //just assign dummy protocol
                protocol = CrsfProtocol(
                    this@LogPlayer
                )
                dataReadyListener?.onProtocolDetected("Unknown")
            } else {
                //now when protocol is detected and tempProtocol is assigned,
                //feed tempProtocol to decode all packets into arrayList
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

            if (dataReadyListener?.getPlaybackAutostart() == true ){
                startPlayback();
            }
        }
    }

    fun load(file: File, dataReadyListener: DataReadyListener) {
        this.dataReadyListener = dataReadyListener
        task.execute(file)
    }

    fun seek(position: Int) {
        //seek forward: fire all packets from last position to new position
        //seek backward: fire all packets from the start to the new position

        //in the range of processed packets during the seek,
        //packets which produce onGPSData: all fired (are required to build correct track without cut corners)
        //other packets: only last one is fired (there is no need to fire data which will be replaced by last packet)
        uniqueData.clear()
        uniqueDataIndex.clear()
        decodedCoordinates.clear()

        //when decodedCoordinates.size=key, cachedData[value]
        var outUniqueData: HashMap<Int, ArrayList<Int>> = HashMap<Int, ArrayList<Int>>();

        this.fireGPSState = false;

        var addToEnd: Boolean = false;

        if ( position == 0) {
            //clear router line and message
            protocol.dataDecoder.restart()
            this.expireStatusText(10000)
        }

        if (position > currentPosition) {
            for (i in currentPosition until position) {
                var prevFix = this.hasGPSFix
                if ( protocol.dataDecoder.isGPSData( cachedData[i].telemetryType )) {
                    protocol.dataDecoder.decodeData(cachedData[i])
                    if ( prevFix != this.hasGPSFix)
                    {
                        var index = decodedCoordinates.size;
                        if ( outUniqueData[index] == null) {
                            outUniqueData[index] = ArrayList<Int>();
                        }
                        outUniqueData[index]?.add(i);
                    }
                } else {
                    uniqueData[cachedData[i].telemetryType] = i
                    uniqueDataIndex[cachedData[i].telemetryType] = decodedCoordinates.size;
                }
            }
            addToEnd = true
            currentPosition = position
        } else if (position < currentPosition) {
            protocol.dataDecoder.restart()
            this.hasGPSFix = false;
            this.satellites = 0;
            for (i in 0 until position) {
                var prevFix = this.hasGPSFix
                if ( protocol.dataDecoder.isGPSData( cachedData[i].telemetryType )) {
                    protocol.dataDecoder.decodeData(cachedData[i])
                    if ( prevFix != this.hasGPSFix)
                    {
                        var index = decodedCoordinates.size;
                        if ( outUniqueData[index] == null) {
                            outUniqueData[index] = ArrayList<Int>();
                        }
                        outUniqueData[index]?.add(i);
                        uniqueData.remove(cachedData[i].telemetryType)
                        uniqueDataIndex.remove(cachedData[i].telemetryType)
                    }
                } else {
                    uniqueData[cachedData[i].telemetryType] = i
                    uniqueDataIndex[cachedData[i].telemetryType] = decodedCoordinates.size;
                }
            }
            currentPosition = position
            addToEnd = false
        }

        uniqueDataIndex.forEach {
            var type = it.key;
            var index = it.value;
            if (outUniqueData[index] == null) {
                outUniqueData[index] = ArrayList<Int>();
            }
            outUniqueData[index]?.add(uniqueData[type]!!);
        }

        //we can fire only last packet for unique data,
        //but it has to be correctly fired between gps coords
        var outDecodedCoordinates = ArrayList<Position>()
        this.fireGPSState = true;

        for ( index in 0..decodedCoordinates.size) {
            var uids: ArrayList<Int>? = outUniqueData[index];
            if (uids != null) {
                if (outDecodedCoordinates.size > 0) {
                    originalListener.onGPSData(outDecodedCoordinates, addToEnd)
                    this.expireStatusText(outDecodedCoordinates.size)
                    addToEnd = true;
                    outDecodedCoordinates.clear();
                }
                uids.forEach({
                    protocol.dataDecoder.decodeData(cachedData[it])
                })
            }
            if ( index < decodedCoordinates.size )
            {
                outDecodedCoordinates.add(decodedCoordinates[index]);
            }
        }

        if ( outDecodedCoordinates.size > 0 ) {
            originalListener.onGPSData(outDecodedCoordinates, addToEnd)
            this.expireStatusText(outDecodedCoordinates.size)
        }
    }

    fun stop() {
        if ( mTimer != null ) {
            this.mTimer?.cancel();
            this.mTimer = null;
            this.dataReadyListener?.onPlaybackStateChange(false)
        }
    }

    fun startPlayback() {
        if ( this.mTimer == null ) {
            this.mTimer = Timer();

            if ( currentPosition == cachedData.size) {
                seek(0)
            }

            totalPlaybackDurationMS = dataReadyListener!!.getTotalPlaybackDurationSec() * 1000

            val step = Math.max(1, Math.min( 1000, cachedData.size / (totalPlaybackDurationMS / 50)))

            this.mTimer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    val prevPosition = currentPosition;

                    if ( currentPosition == cachedData.size ) {
                        stop();
                    } else {
                        var nextPosition = Math.min(currentPosition + step, cachedData.size)
                        dataReadyListener?.onPlaybackPositionChange( prevPosition, nextPosition );
                    }
                }
            }, 100, 50)
            this.dataReadyListener?.onPlaybackStateChange(true)
        }

    }

    public fun isPlaying() : Boolean {
        return this.mTimer != null;
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

    override fun onVBATOrCellData(voltage: Float) {
        originalListener.onVBATOrCellData(voltage)
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

    override fun onUpLqData(lq: Int) {
        originalListener.onUpLqData(lq)
    }

    override fun onDnLqData(lq: Int) {
        originalListener.onDnLqData(lq)
    }

    override fun onElrsModeModeData(rf: Int) {
        originalListener.onElrsModeModeData(rf)
    }

    override fun onAntData(activeAntena: Int) {
        originalListener.onAntData(activeAntena)
    }

    override fun onGPSData(list: List<Position>, addToEnd: Boolean) {

    }

    override fun onDisconnected() {
    }

    override fun onGPSState(satellites: Int, gpsFix: Boolean) {
        this.hasGPSFix = gpsFix
        this.satellites = satellites
        if ( fireGPSState) {
            originalListener.onGPSState(satellites, gpsFix)
        }
    }

    override fun onVSpeedData(vspeed: Float) {
        originalListener.onVSpeedData(vspeed)
    }

    override fun onThrottleData(throttle :Int) {
        originalListener.onThrottleData(throttle)
    }

    override fun onAltitudeData(altitude: Float) {
        decodedAltitude = altitude;
        originalListener.onAltitudeData(altitude)
    }

    override fun onGPSAltitudeData(altitude: Float) {
        originalListener.onGPSAltitudeData(altitude)
        if ( launchPointMSLAltitude == 0 && altitude != 0.0f) {
            launchPointMSLAltitude = Math.ceil(altitude.toDouble()).toInt();
        }
    }

    override fun onDistanceData(distance: Int) {
        originalListener.onDistanceData(distance)
    }

    override fun onRollData(rollAngle: Float) {
        originalListener.onRollData(rollAngle)
    }

    override fun onAirSpeedData(speed: Float) {
        originalListener.onAirSpeedData(speed)
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
        this.statusTextExpire = 10;
        originalListener.onStatusText(message)
    }


    fun expireStatusText(cycles: Int) {
        if (this.statusTextExpire > 0) {
            this.statusTextExpire -= cycles;
            if (this.statusTextExpire <= 0) {
                this.statusTextExpire = 0;
                originalListener.onStatusText("")
            }
        }
    }

    override fun onDNSNRData(snr: Int) {
        originalListener.onDNSNRData(snr)
    }

    override fun onUPSNRData(snr: Int) {
        originalListener.onUPSNRData(snr)
    }

    override fun onPowerData(power: Int) {
        originalListener.onPowerData(power)
    }

    override fun onRssiDbm1Data(rssi: Int) {
        originalListener.onRssiDbm1Data(rssi)
    }

    override fun onRssiDbm2Data(rssi: Int) {
        originalListener.onRssiDbm2Data(rssi)
    }

    override fun onRssiDbmdData(rssi: Int) {
        originalListener.onRssiDbmdData(rssi)
    }

    override fun onTelemetryByte(){
        originalListener.onTelemetryByte()
    }

    override fun onSuccessDecode() {
        originalListener.onSuccessDecode()
    }

    override fun onDecoderRestart() {
        originalListener.onDecoderRestart()
    }

    override fun onFlyModeData(
        armed: Boolean,
        heading: Boolean,
        firstFlightMode: DataDecoder.Companion.FlyMode?,
        secondFlightMode: DataDecoder.Companion.FlyMode?
    ) {
        originalListener.onFlyModeData(armed, heading, firstFlightMode, secondFlightMode)
    }

    fun addGPXHeader(fileWriter : PrintWriter )
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

    fun addGPXFooter(fileWriter : PrintWriter )
    {
        fileWriter.write("</trkseg>\n" +
                "</trk>\n" +
                "</gpx>")
    }

    //https://github.com/Parrot-Developers/mavlink/blob/master/pymavlink/tools/mavtogpx.py
    fun exportGPX(fileName: String, homePointAltitudeMSL: Float)
    {
        val dir = Environment.getExternalStoragePublicDirectory("TelemetryLogs")
        dir.mkdirs()
        val file = File(dir, fileName)

        var fileWriter = file.printWriter()
        addGPXHeader( fileWriter );

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
                            "  <ele>" +  ((decodedAltitude + homePointAltitudeMSL) ).toString()  + "</ele>\n" +
                            //"  <time>%s</time>\n" +
                            "  <course>" + decodedHeading.toString()  + "</course>\n" +
                            "  <speed>" + decodedSpeed.toString()  + "</speed>\n" +
                            "  <fix>3d</fix>\n" +
                            "  <time>" + sdf.format( Date(t) ) + "</time>\n" +
                            "</trkpt>";
                    fileWriter.write(s);
                }
        }

        addGPXFooter(fileWriter);

        fileWriter.flush();
        fileWriter.close()
    }

    fun addKMLHeader(fileWriter : PrintWriter, altitudeMode: String )
    {
        val s = """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2"  xmlns:gx="http://www.google.com/kml/ext/2.2" xmlns:kml="http://www.opengis.net/kml/2.2"    
     xmlns:atom="http://www.w3.org/2005/Atom">
    <Document>
        <visibility>1</visibility>
        <open>1</open>
        <Style id="red">
            <LineStyle>
            <color>C81400FF</color>
            <width>4</width>
            </LineStyle>
        </Style>
        <Folder>
            <name>Tracks</name>
            <description>Track 1</description>
            <visibility>1</visibility>            
            <open>0</open>
                                                            
                <Placemark>
                    <visibility>1</visibility>            
                    <open>0</open> 
                    <styleUrl>#red</styleUrl>
                    <name>Track no. 1</name>
                    <description>No info available</description>
                    <LineString>
                        <extrude>true</extrude>
                        <tessellate>true</tessellate>
                        <altitudeMode>$altitudeMode</altitudeMode> 
                        <coordinates>
"""
        fileWriter.write(s)
    }

    fun addKMLFooter(fileWriter : PrintWriter, altitudeMode: String, lookAtLon : Double, lookAtLat: Double, lookAtAlt: Float, homePointAltitudeMSL: Float )
    {
        val lon = lookAtLon.toString()
        val lat = lookAtLat.toString()
        val alt = (lookAtAlt + homePointAltitudeMSL)
        val s = """                        </coordinates>
                    </LineString>
                </Placemark>
                                        
        </Folder>
                
        <LookAt>
            <longitude>$lon</longitude>            
            <latitude>$lat</latitude>             
            <altitude>$alt</altitude>               
            <heading>0</heading>               
            <tilt>45</tilt>
            <range>226</range>                    
            <altitudeMode>$altitudeMode</altitudeMode> 
        </LookAt>
    </Document>
</kml>
"""
        fileWriter.write(s)
    }

    fun exportKML(fileName: String, homePointAltitudeMSL: Float, altitudeMode: String)
    {
        val dir = Environment.getExternalStoragePublicDirectory("TelemetryLogs")
        dir.mkdirs()
        val file = File(dir, fileName)

        var fileWriter = file.printWriter()
        addKMLHeader( fileWriter, altitudeMode );

        seek(0);

        decodedAltitude = -10000f;
        decodedSpeed = 0f;
        decodedHeading = 0f;

        var lastLon : Double = 0.0;
        var lastLat : Double = 0.0;

        var firstLon : Double = 0.0;
        var firstLat : Double = 0.0;
        var firstAlt : Float = 0.0f;

        var s = "                            ";

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

                if ( firstLon == 0.0 ) {
                    firstLon = decodedCoordinates[0].lon
                }
                if ( firstLat == 0.0 ) {
                    firstLat = decodedCoordinates[0].lat
                }
                if ( (firstAlt == 0.0f) && (decodedAltitude != - 10000f) ) {
                    firstAlt = decodedAltitude.toFloat()
                }

                decodedCoordinates.clear();
            }

            if ( output && (decodedAltitude != - 10000f) )
            {
                s += lastLon.toString() + "," + lastLat.toString() + "," + ((decodedAltitude + homePointAltitudeMSL) ).toString() + " "
            }
        }

        fileWriter.write(s)
        fileWriter.write("\n")

        addKMLFooter(fileWriter, altitudeMode, firstLon, firstLat, firstAlt, homePointAltitudeMSL)

        fileWriter.flush();
        fileWriter.close()
    }

    interface DataReadyListener {
        fun onUpdate(percent: Int)
        fun onDataReady(size: Int)
        fun onPlaybackPositionChange(prevPosition: Int, nextPosition: Int)
        fun onPlaybackStateChange( isPlaying : Boolean)
        fun getTotalPlaybackDurationSec() : Int
        fun getPlaybackAutostart() : Boolean
        fun onProtocolDetected( protocolName: String)
    }
}