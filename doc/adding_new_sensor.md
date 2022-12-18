# Adding new sensor checklist

1) Create icon res\drawable\ic_xxx.xml  
Import from 240 x 240 px image, then edit xml manually: set 24 x 24
Can import from PSD:
https://developer.android.com/studio/write/vector-asset-studio#running

2) Define TextViewStableSize for the sensor in:
res\layout\top_layout.xml
res\layout-w600dp-port\top_layout.xml
res\layout-port\top_layout.xml

3) define 
private lateinit var dnSnr: TextView
in MapsActivity.kt

4) Initialize in mapsActivity:
dnSnr = findViewById(R.id.dn_snr)

5) Add to SensorViewMap in MapsActivity.kt with next index:

        sensorViewMap = hashMapOf(
            Pair(PreferenceManager.sensors.elementAt(11).name, dnSnr)
        )

6) Add in resetUI() in MapsActivity.kt:

    private fun resetUI() {
        dnSnr.text = "-"

7) Add in MapsActivity.kt:

    override fun onDNSNRData(snr: Int) {
        this.sensorTimeoutManager.onDNSNRData(snr);
        runOnUiThread {
            this.dnSnr.text = snr.toString();
        }
    }

8) Add in MapsActivity.kt:

    private fun updateSetSensorGrayed( sensorId : Int )
    {
          .....
            SensorTimeoutManager.SENSOR_DN_SNR ->{
                this.dnSnr.setAlpha(alpha);
            }

9) Add in SensorTimeoutManager.kt with next id, and increase SENSOR_COUNT:

        public const val SENSOR_DN_SNR = 12;

10) Add in SensorTimeoutManager.kt:

    override fun onDNSNRData(snr: Int){
        this.onSensorData(SensorTimeoutManager.SENSOR_DN_SNR);
    }


11) Add in Protoco.kt with next id:

        const val DN_SNR = 121

12) Add in PreferenceManager.kt:

SensorSetting("Downlink SNR", 6, "top", false )

6 is index of TextViewStableSize in layout_top.xml

13) Add in XXProtocol.kt:

                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                DN_SNR,
                                downlinkSNR,
                                inputData
                            )
                        )

14) Add in LogPlayer.kt:

    override fun onDNSNRData(snr: Int) {
        originalListener.onDNSNRData(snr)
    }

15) Add in DataDecoder.kt:

    override fun onDNSNRData(snr: Int) {
    }

16) Add in DataDecoder.kt:

   fun onDNSNRData(snr: Int)

17) Add in DataService.kt:

    override fun onDNSNRData(snr: Int) {
        dataListener?.onDNSNRData(snr)
    }


18)






