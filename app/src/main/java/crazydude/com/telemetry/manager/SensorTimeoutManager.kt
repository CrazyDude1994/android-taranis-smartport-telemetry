package crazydude.com.telemetry.manager

import crazydude.com.telemetry.maps.Position
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.util.*

class SensorTimeoutManager(protected val listener: SensorTimeoutManager.Listener): DataDecoder.Listener {

    interface Listener {
        fun onSensorTimeout( sensorId : Int )
        fun onSensorData( sensorId : Int )
    }
    companion object {
        public const val SENSOR_GPS = 0;
        public const val SENSOR_DISTANCE = 1;
        public const val SENSOR_ALTITUDE = 2;
        public const val SENSOR_RSSI = 3;
        public const val SENSOR_VOLTAGE = 4;
        public const val SENSOR_CURRENT = 5;
        public const val SENSOR_SPEED = 6;
        public const val SENSOR_FUEL = 7;
        public const val SENSOR_RC_CHANNELS = 8;
        public const val SENSOR_STATUSTEXT = 9;
        public const val SENSOR_LQ = 10;
        public const val SENSOR_RF = 11;

        private const val SENSOR_COUNT = 12;

        private const val SENSOR_TIMEOUT_MS = 4000;
    }

    private var timeoutMS: IntArray = IntArray(SENSOR_COUNT)

    private var mTimer: Timer? = null

    private var disabled : Boolean = false;

    fun zeroTimeouts(){
        for( i in 0..SENSOR_COUNT-1){
            timeoutMS[i]=0;
        }
    }

    init {
        this.zeroTimeouts();
    }

    public fun resume()
    {
        if ( this.mTimer == null ) {
            this.mTimer = Timer();

            this.mTimer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    for( i in 0..SENSOR_COUNT-1){
                        if ( ( timeoutMS[i] < SENSOR_TIMEOUT_MS )){
                            timeoutMS[i]+=200;
                            if ( timeoutMS[i] >= SENSOR_TIMEOUT_MS )
                            {
                                if ( !disabled )
                                {
                                    listener.onSensorTimeout(i)
                                }
                            }
                        }
                    }
                }
            }, 200, 200)
        }
    }

    public fun getSensorTimeout( sensorId : Int ) : Boolean {
        if ( this.disabled ) return false;
        return this.timeoutMS[sensorId]>=SensorTimeoutManager.SENSOR_TIMEOUT_MS;
    }

    public fun pause() {
        if ( mTimer != null ) {
            this.mTimer?.cancel();
            this.mTimer = null;
        }
    }

    public fun disableTimeouts(){
        this.disabled = true;
        this.zeroTimeouts();
        for( i in 0..SENSOR_COUNT-1){
            this.listener.onSensorData(i);
        }
    }

    public fun enableTimeouts(){
        this.zeroTimeouts();
        this.disabled = false;
    }

    private fun onSensorData( sensorId: Int) {
        if ( this.disabled ) return;

        var v = this.timeoutMS[sensorId];
        this.timeoutMS[sensorId] = 0;
        if ( v >= SENSOR_TIMEOUT_MS )
        {
            this.listener.onSensorData(sensorId);
        }
    }

    override fun onConnectionFailed() {
    }

    override fun onFuelData(fuel: Int){
        this.onSensorData(SensorTimeoutManager.SENSOR_FUEL);
    }

    override fun onConnected(){
    }

    override fun onGPSData(latitude: Double, longitude: Double){
        this.onSensorData(SensorTimeoutManager.SENSOR_GPS);
    }

    override fun onGPSData(list: List<Position>, addToEnd: Boolean){
        this.onSensorData(SensorTimeoutManager.SENSOR_GPS);
    }

    override fun onVBATData(voltage: Float){
        this.onSensorData(SensorTimeoutManager.SENSOR_VOLTAGE);
    }

    override fun onCellVoltageData(voltage: Float){

    }

    override fun onCurrentData(current: Float){
        this.onSensorData(SensorTimeoutManager.SENSOR_CURRENT);
    }

    override fun onHeadingData(heading: Float){

    }
    override fun onRSSIData(rssi: Int) {
        this.onSensorData(SensorTimeoutManager.SENSOR_RSSI);
    }

    override fun onCrsfLqData(lq: Int){
        this.onSensorData(SensorTimeoutManager.SENSOR_LQ);
    }

    override fun onCrsfRfData(rf: Int){
        this.onSensorData(SensorTimeoutManager.SENSOR_RF);
    }

    override fun onDisconnected(){

    }
    override fun onGPSState(satellites: Int, gpsFix: Boolean){
        this.onSensorData(SensorTimeoutManager.SENSOR_GPS);
    }

    override fun onVSpeedData(vspeed: Float){
    }

    override fun onAltitudeData(altitude: Float){
        this.onSensorData(SensorTimeoutManager.SENSOR_ALTITUDE);
    }
    override fun onGPSAltitudeData(altitude: Float){

    }
    override fun onDistanceData(distance: Int){
        this.onSensorData(SensorTimeoutManager.SENSOR_DISTANCE);
    }
    override fun onRollData(rollAngle: Float){

    }
    override fun onPitchData(pitchAngle: Float){

    }
    override fun onGSpeedData(speed: Float){
        this.onSensorData(SensorTimeoutManager.SENSOR_SPEED);
    }

    override fun onFlyModeData(
        armed: Boolean,
        heading: Boolean,
        firstFlightMode: DataDecoder.Companion.FlyMode?,
        secondFlightMode: DataDecoder.Companion.FlyMode?
    ){

    }

    override fun onAirSpeed(speed: Float){
        this.onSensorData(SensorTimeoutManager.SENSOR_SPEED);
    }

    override fun onRCChannels(rcChannels:IntArray){
        this.onSensorData(SensorTimeoutManager.SENSOR_RC_CHANNELS);
    }

    override fun onStatusText(message: String){
        this.onSensorData(SensorTimeoutManager.SENSOR_STATUSTEXT);
    }

    override fun onSuccessDecode(){

    }

}