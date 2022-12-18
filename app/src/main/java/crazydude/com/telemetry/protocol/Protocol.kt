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
        const val GPS_ALTITUDE = 16
        const val ASPEED = 17
        const val GPS_LATITUDE = 18
        const val GPS_LONGITUDE = 19
        const val GPS_SATELLITES = 20
        const val ATTITUDE = 21
        const val GPS_ORIGIN_LATITUDE = 22
        const val GPS_ORIGIN_LONGITUDE = 23
        const val GPS_HOME_LATITUDE = 24
        const val GPS_HOME_LONGITUDE = 25
        //ardupilot S.PORT Data ID's
        const val DATA_ID_GPS_ALT_BP = 26
        const val DATA_ID_GPS_SPEED_BP = 27
        const val DATA_ID_GPS_ALT_AP = 28
        const val DATA_ID_GPS_LONG_BP = 29
        const val DATA_ID_GPS_LAT_BP = 30
        const val DATA_ID_GPS_COURS_BP = 31
        const val DATA_ID_GPS_SPEED_AP = 32
        const val DATA_ID_GPS_LONG_AP = 33
        const val DATA_ID_GPS_LAT_AP = 34
        const val DATA_ID_BARO_ALT_AP = 35
        const val DATA_ID_GPS_LONG_EW = 36
        const val DATA_ID_GPS_LAT_NS = 37
        const val DATA_ID_ACC_X_1000 = 38  //precision 3
        const val DATA_ID_ACC_Y_1000 = 39
        const val DATA_ID_ACC_Z_1000 = 40
        const val DATA_ID_ACC_X_100 = 41 //precision 2
        const val DATA_ID_ACC_Y_100 = 42
        const val DATA_ID_ACC_Z_100 = 43
        const val GPS_STATE_ARDU = 44
        //ardupilot S.PORT passthrough  Data ID's
        const val RxBt = 45
        const val ARDU_TEXT = 46
        const val ARDU_ATTITUDE = 47
        const val ARDU_VEL_YAW = 48
        const val ARDU_AP_STATUS = 49
        const val ARDU_GPS_STATUS = 50
        const val ARDU_HOME = 51
        const val ARDU_BATT_2 = 52
        const val ARDU_BATT_1 = 53
        const val ARDU_PARAM = 54
        const val CRSF_UP_LQ = 55
        const val CRSF_DN_LQ = 56
        const val ELRS_RF_MODE = 57
        const val DN_SNR = 58
        const val UP_SNR = 59
        const val ANT = 60
        const val POWER = 61
        const val RSSI_DBM_1 = 62
        const val RSSI_DBM_2 = 63
        const val RSSI_DBM_D = 64
        const val VBAT_OR_CELL = 65
        const val THROTTLE = 66
        const val ORIGIN = 67

        const val RC_CHANNEL_0 = 100
        const val RC_CHANNEL_1 = 101
        const val RC_CHANNEL_2 = 102
        const val RC_CHANNEL_3 = 103
        const val RC_CHANNEL_4 = 104
        const val RC_CHANNEL_5 = 105
        const val RC_CHANNEL_6 = 106
        const val RC_CHANNEL_7 = 107
        const val RC_CHANNEL_8 = 108
        const val RC_CHANNEL_9 = 109
        const val RC_CHANNEL_10 = 110
        const val RC_CHANNEL_11 = 111
        const val RC_CHANNEL_12 = 112
        const val RC_CHANNEL_13 = 113
        const val RC_CHANNEL_14 = 114
        const val RC_CHANNEL_15 = 115
        const val RC_CHANNEL_16 = 116
        const val RC_CHANNEL_17 = 117

        const val STATUSTEXT = 120

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