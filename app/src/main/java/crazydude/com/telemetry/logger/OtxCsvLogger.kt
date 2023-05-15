package crazydude.com.telemetry.logger

import android.os.Environment
import crazydude.com.telemetry.maps.Position
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*


class OtxCsvLogger : DataDecoder.Listener {

    private val fileWriter: FileWriter
    private val file: File?
    private val timer = Timer()

    private val header = listOf(
        "Date",
        "Time",
        "RSSI(dB)",
        "1RSS(dB)",
        "2RSS(dB)",
        "RQly(%)",
        "RSNR(dB)",
        "ANT",
        "RFMD",
        "TPWR(mW)",
        "TRSS(dB)", // Downlink - signal strength
        "TQly(%)",
        "TSNR(dB)",
        "Ptch(rad)",
        "Roll(rad)",
        //"Yaw(rad)",
        "FM",
        "VSpd(m/s)",
        "GPS",
        "GSpd(kmh)",
        "Hdg(°)",
        "Alt(m)",
        "Sats",
//            "RxBt(V)",
        "Curr(A)",
        "VFAS(V)",
        "Dist(m)"
    )

    init {
        val name = SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())
        val dir = Environment.getExternalStoragePublicDirectory("TelemetryLogs")
        dir.mkdirs()
        file = File(dir, "$name.csv")
        fileWriter = FileWriter(file)
        outputLine(header)
    }

    private var fuel: Int = 0
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var batVoltage: Float = 0F
    private var cellVoltage: Float = 0F
    private var current: Float = 0F
    private var heading: Float = 0F
    private var rssi: Int = 0
    private var upLq: Int = 0
    private var dnLq: Int = 0
    private var elrsMode: Int = 0
    private var satellites: Int = 0
    private var gpsFix: Boolean = false
    private var vSpeed: Float = 0F
    private var throttle: Int = 0;
    private var altitude: Float = 0F
    private var gpsAltitude: Float = 0F
    private var distance: Int = 0
    private var rollAngle: Float = 0F
    private var pitchAngle: Float = 0F
    private var gSpeed: Float = 0F
    private var armed: Boolean = false
    private var airSpeed: Float = 0F
    private var rcChannels: IntArray? = null
    private var statusText: String = ""
    private var dnSnr: Int = 0
    private var upSnr: Int = 0
    private var activeAntenna: Int = 0
    private var power: Int = 0
    private var rssiDbm1: Int = 0
    private var rssiDbm2: Int = 0
    private var rssiDbmd: Int = 0
    private var flightMode: String = ""

    private fun outputLine(line: List<String>) {
        val csv = line.joinToString(",")
        fileWriter.append(csv)
        fileWriter.append("\n")
    }

    /*
    *
    * "Alt(m)" altitude 0.0
      -        armed false
      -        hdg false
      "FM" firstFlightMode ERROR
      "Curr(A)"  current: 0.4
      "TSNR(dB)"  dn snr 4
      "TQly(%)"   dnLq: 100
      "RFMD"      elrs mode: 5
      "Fuel(mAh)" fuel: 0 mah
      "GPS"       GPS: 0.0 0.0
      "GSpd(kmh)" ground speed 0.0
      "Hdg(°)"    heading: 103.89444
      "Ptch(rad)" pitchAngle -81.49752
      "TPWR(mW)"  power 1
      "Roll(rad)" rollAngle 83.99561
      "1RSS(dB)"  rssi dbm1 -40
      "2RSS(dB)"  rssi dbm2 0
      "TRSS(dB)"  rssi dbmd -16
      "RSSI(dB)"  rssi: 94
      "Sats"      sats 0
      -           fix: false
      "RSNR(dB)"  up snr 2
      "RQly(%)"   upLq: 100
      "VFAS(V)"   voltage vbat or cell 16.4

    * */
    private fun outputData() {
        val date = SimpleDateFormat("yyyy-MM-dd").format(Date())
        val time = SimpleDateFormat("HH:mm:ss.SSS").format(Date())

        val data = listOf<String>(
            date,
            time,
            rssi.toString(),
            rssiDbm1.toString(),
            rssiDbm2.toString(),
            upLq.toString(),
            upSnr.toString(),
            activeAntenna.toString(),
            elrsMode.toString(),
            power.toString(),
            rssiDbmd.toString(), // Downlink - signal strength
            dnLq.toString(),
            dnSnr.toString(),
            pitchAngle.toString(),
            rollAngle.toString(),
            flightMode,
            vSpeed.toString(),
            "$latitude $longitude",
            gSpeed.toString(),
            heading.toString(),
            altitude.toString(),
            satellites.toString(),
//            "RxBt(V)",
            current.toString(),
            batVoltage.toString(),
            distance.toString()
        )
        outputLine(data)
    }

    override fun onConnectionFailed() {
        fileWriter.close()
    }

    override fun onFuelData(fuel: Int) {
        this.fuel = fuel

    }

    override fun onConnected() {
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                outputData()
            }
        },1000,200)
    }

    override fun onGPSData(latitude: Double, longitude: Double) {
        this.latitude = latitude
        this.longitude = longitude
    }

    override fun onGPSData(list: List<Position>, addToEnd: Boolean) {

    }

    override fun onVBATData(voltage: Float) {
        this.batVoltage = voltage
    }

    override fun onCellVoltageData(voltage: Float) {
        this.cellVoltage = voltage
    }

    override fun onCurrentData(current: Float) {
        this.current = current
    }

    override fun onHeadingData(heading: Float) {
        this.heading = heading
    }

    override fun onRSSIData(rssi: Int) {
        this.rssi = rssi
    }

    override fun onUpLqData(lq: Int) {
        this.upLq = lq
    }

    override fun onDnLqData(lq: Int) {
        this.dnLq = lq
    }

    override fun onElrsModeModeData(mode: Int) {
        this.elrsMode = mode
    }

    override fun onDisconnected() {
//        val sendIntent = Intent()
//        sendIntent.action = Intent.ACTION_SEND
//        sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
//        sendIntent.type = "text/csv"
//        startActivity(Intent.createChooser(sendIntent, "SHARE"))
        timer.cancel()
        timer.purge()
        fileWriter.close()
    }

    override fun onGPSState(satellites: Int, gpsFix: Boolean) {
        this.satellites = satellites
        this.gpsFix = gpsFix
    }

    override fun onVSpeedData(vspeed: Float) {
        this.vSpeed = vspeed
    }

    override fun onThrottleData(throttle: Int) {
        this.throttle = throttle
    }

    override fun onAltitudeData(altitude: Float) {
        this.altitude = altitude
    }

    override fun onGPSAltitudeData(altitude: Float) {
        this.gpsAltitude = altitude
    }

    override fun onDistanceData(distance: Int) {
        this.distance = distance
    }

    override fun onRollData(rollAngle: Float) {
        this.rollAngle = rollAngle
    }

    override fun onPitchData(pitchAngle: Float) {
        this.pitchAngle = pitchAngle
    }

    override fun onGSpeedData(speed: Float) {
        this.gSpeed = speed
    }

    override fun onFlyModeData(
        armed: Boolean,
        heading: Boolean,
        firstFlightMode: DataDecoder.Companion.FlyMode?,
        secondFlightMode: DataDecoder.Companion.FlyMode?
    ) {
        this.flightMode = firstFlightMode.toString()
    }

    override fun onAirSpeedData(speed: Float) {
        this.airSpeed=speed
    }

    override fun onRCChannels(rcChannels: IntArray) {
        // not yet implemented
    }

    override fun onStatusText(message: String) {
        this.statusText=message
    }

    override fun onDNSNRData(snr: Int) {
        this.dnSnr=snr
    }

    override fun onUPSNRData(snr: Int) {
        this.upSnr=snr
    }

    override fun onAntData(activeAntena: Int) {
        this.activeAntenna=activeAntena
    }

    override fun onPowerData(power: Int) {
        this.power=power
    }

    /**
     * Uplink - received signal strength antenna 1 (RSSI)
     */
    override fun onRssiDbm1Data(rssi: Int) {
        this.rssiDbm1=rssi
    }

    /**
     * Uplink - received signal strength antenna 2 (RSSI)
     */
    override fun onRssiDbm2Data(rssi: Int) {
        this.rssiDbm2=rssi
    }

    /**
     * Downlink - received signal strength (RSSI)
     */
    override fun onRssiDbmdData(rssi: Int) {
        this.rssiDbmd=rssi
    }

    override fun onVBATOrCellData(voltage: Float) {
        this.batVoltage=voltage
    }

    override fun onTelemetryByte() {

    }

    override fun onSuccessDecode() {

    }

    override fun onDecoderRestart() {

    }

}