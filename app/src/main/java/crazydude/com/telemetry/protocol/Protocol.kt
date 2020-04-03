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
        const val GALT = 15
        const val ASPEED = 16
        const val GPS_LATITUDE = 17
        const val GPS_LONGITUDE = 18
        const val GPS_SATELLITES = 19
        const val ATTITUDE = 20

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