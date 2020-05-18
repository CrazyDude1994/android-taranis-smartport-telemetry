package crazydude.com.telemetry.protocol

import crazydude.com.telemetry.protocol.decoder.DataDecoder

abstract class Protocol(val dataDecoder: DataDecoder) {

    companion object {
        const val FUEL = 0
        const val GPS = 1
        const val VBAT = 2
        const val CELL_VOLTAGE = 3
        const val CURRENT = 4
        const val HEADING = 5
        const val RSSI = 6
        const val FLYMODE = 7
        const val GPS_STATE = 8
        const val VSPEED = 9
        const val ALTITUDE = 10
        const val GSPEED = 11
        const val DISTANCE = 12
        const val ROLL = 13
        const val PITCH = 14
        const val YAW = 15
        const val GALT = 16
        const val ASPEED = 17
        const val GPS_LATITUDE = 18
        const val GPS_LONGITUDE = 19
        const val GPS_SATELLITES = 20
        const val ATTITUDE = 21
        const val GPS_ORIGIN_LATITUDE = 22
        const val GPS_ORIGIN_LONGITUDE = 23
        //ardupilot
        const val DATA_ID_GPS_ALT_BP = 24
        const val DATA_ID_GPS_SPEED_BP = 25
        const val DATA_ID_GPS_ALT_AP = 26
        const val DATA_ID_GPS_LONG_BP = 27
        const val DATA_ID_GPS_LAT_BP = 28
        const val DATA_ID_GPS_COURS_BP = 29
        const val DATA_ID_GPS_SPEED_AP = 30
        const val DATA_ID_GPS_LONG_AP = 31
        const val DATA_ID_GPS_LAT_AP = 32
        const val DATA_ID_BARO_ALT_AP = 33
        const val DATA_ID_GPS_LONG_EW = 34
        const val DATA_ID_GPS_LAT_NS = 35
        const val GPS_STATE_ARDU = 36
        const val RxBt = 35
        const val ARDU_TEXT = 36
        const val ARDU_ATTITUDE = 37
        const val ARDU_VEL_YAW = 38
        const val ARDU_AP_STATUS = 39
        const val ARDU_GPS_STATUS = 40
        const val ARDU_HOME =41
        const val ARDU_BATT_2 =42
        const val ARDU_BATT_1=43
        const val ARDU_PARAM=44
        //const val DATA_ID_TEMP1_SENSOR = 0x0002
        //const val DATA_ID_FUEL_SENSOR = 0x0004
        //const val DATA_ID_TEMP2_SENSOR = 0x0005
        //const val DATA_ID_BARO_ALT_BP_SENSOR = 0x0010
        //const val DATA_ID_CURRENT_SENSOR = 0x0028
        //const val DATA_ID_VARIO_SENSOR = 0x0030
        //const val DATA_ID_VFAS_SENSOR = 0x0039

        class TelemetryData(val telemetryType: Int, val data: Int, val rawData: ByteArray? = null) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as TelemetryData

                if (telemetryType != other.telemetryType) return false
                if (data != other.data) return false
                if (rawData != null) {
                    if (other.rawData == null) return false
                    if (!rawData.contentEquals(other.rawData)) return false
                } else if (other.rawData != null) return false

                return true
            }

            override fun hashCode(): Int {
                var result = telemetryType
                result = 31 * result + data
                result = 31 * result + (rawData?.contentHashCode() ?: 0)
                return result
            }

        }
    }

    abstract fun process(data: Int)
}