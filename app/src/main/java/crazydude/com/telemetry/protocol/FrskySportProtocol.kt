package crazydude.com.telemetry.protocol

import android.util.Log
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import crazydude.com.telemetry.protocol.decoder.FrskyDataDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder


class FrSkySportProtocol : Protocol {

    constructor(dataListener: DataDecoder.Listener) : super(FrskyDataDecoder(dataListener))
    constructor(dataDecoder: DataDecoder) : super(dataDecoder)

    private var state: State = Companion.State.IDLE
    private var bufferIndex: Int = 0
    private var buffer: IntArray = IntArray(SPORT_PACKET_SIZE)

    companion object {
        enum class State {
            IDLE, DATA, XOR
        }

        const val SPORT_PACKET_SIZE = 0x09

        const val SPORT_START_BYTE = 0x7E
        const val SPORT_DATA_START = 0x10
        const val SPORT_DATA_STUFF = 0x7D
        const val SPORT_STUFF_MASK = 0x20

        const val SPORT_VFAS_SENSOR = 0x0210
        const val SPORT_CELL_SENSOR = 0x0910
        const val SPORT_VSPEED_SENSOR = 0x0110
        const val SPORT_GSPEED_SENSOR = 0x0830
        const val SPORT_ALT_SENSOR = 0x0100
        const val SPORT_GALT_SENSOR = 0x0820
        const val SPORT_DISTANCE_SENSOR = 0x0420
        const val SPORT_FUEL_SENSOR = 0x0600
        const val SPORT_GPS_SENSOR = 0x0800
        const val SPORT_CURRENT_SENSOR = 0x200
        const val SPORT_HEADING_SENSOR = 0x0840
        const val SPORT_RSSI_SENSOR = 0xF101
        const val SPORT_FLYMODE_SENSOR = 0x0400
        const val SPORT_GPS_STATE_SENSOR = 0x0410
        const val SPORT_PITCH_SENSOR = 0x0430
        const val SPORT_ROLL_SENSOR = 0x0440
        const val SPORT_PITCH_SENSOR_BETAFLIGHT = 0x05230
        const val SPORT_ROLL_SENSOR_BETAFLIGHT = 0x05240
        const val SPORT_AIRSPEED_SENSOR = 0x0A00
        //ardupilot passthrough sensors
        const val SPORT_ARDU_TEXT_SENSOR = 0x5000 // status text (dynamic)
        const val SPORT_ARDU_ATTITUDE_SENSOR = 0x5006 //Attitude and range (dynamic)
        const val SPORT_ARDU_VEL_YAW_SENSOR = 0x5005 //Vel and Yaw
        const val SPORT_ARDU_AP_STATUS_SENSOR = 0x5001 //AP status
        const val SPORT_ARDU_GPS_STATUS_SENSOR = 0x5002 //GPS status
        const val SPORT_ARDU_HOME_SENSOR =  0x5004   //Home
        const val SPORT_ARDU_BATT_2_SENSOR = 0x5008  // Battery 2 status
        const val SPORT_ARDU_BATT_1_SENSOR = 0x5003  // Battery 1 status
        const val SPORT_ARDU_PARAM_SENSOR = 0x5007   // parameters
        const val SPORT_RxBt_SENSOR = 0xF104 //https://github.com/Clooney82/MavLink_FrSkySPort/wiki/1.2.-FrSky-Taranis-Telemetry
        //ardupilot S.PORT sensors
        const val SPORT_DATA_ID_GPS_ALT_BP_SENSOR = 0x0001 //gps altitude integer part
        const val SPORT_DATA_ID_TEMP1_SENSOR = 0x0002 //flight mode
        const val SPORT_DATA_ID_FUEL_SENSOR = 0x0004 //battery remaining
        const val SPORT_DATA_ID_TEMP2_SENSOR = 0x0005 //GPS status and number of satellites as num_sats*10 + status (to fit into a uint8_t)
        const val SPORT_DATA_ID_GPS_ALT_AP_SENSOR = 0x0009 //gps altitude decimals
        const val SPORT_DATA_ID_BARO_ALT_BP_SENSOR = 0x0010 //altitude integer part
        const val SPORT_DATA_ID_GPS_SPEED_BP_SENSOR = 0x0011 //gps speed integer part
        const val SPORT_DATA_ID_GPS_LONG_BP_SENSOR = 0x0012 //gps longitude degree and minute integer part
        const val SPORT_DATA_ID_GPS_LAT_BP_SENSOR = 0x0013 //send gps lattitude degree and minute integer part
        const val SPORT_DATA_ID_GPS_COURS_BP_SENSOR = 0x0014 //heading in degree based on AHRS and not GPS
        const val SPORT_DATA_ID_GPS_SPEED_AP_SENSOR = 0x0019 //gps speed decimal part
        const val SPORT_DATA_ID_GPS_LONG_AP_SENSOR = 0x001A //gps longitude minutes decimal part
        const val SPORT_DATA_ID_GPS_LAT_AP_SENSOR = 0x001B //send gps lattitude minutes decimal part
        const val SPORT_DATA_ID_BARO_ALT_AP_SENSOR = 0x0021 //gps altitude decimal part
        const val SPORT_DATA_ID_GPS_LONG_EW_SENSOR = 0x0022 //gps East / West information
        const val SPORT_DATA_ID_GPS_LAT_NS_SENSOR = 0x0023 //gps North / South information
        const val SPORT_DATA_ID_ACC_X = 0x0024 //accelerometer value x
        const val SPORT_DATA_ID_ACC_Y = 0x0025 //accelerometer value y
        const val SPORT_DATA_ID_ACC_Z = 0x0026 //accelerometer value z
        const val SPORT_DATA_ID_ACC_X_BETAFLIGHT = 0x0700 //accelerometer value x betaflight custom
        const val SPORT_DATA_ID_ACC_Y_BETAFLIGHT = 0x0710 //accelerometer value y betaflight custom
        const val SPORT_DATA_ID_ACC_Z_BETAFLIGHT = 0x0720 //accelerometer value z betaflight custom
        const val SPORT_DATA_ID_CURRENT_SENSOR = 0x0028 //current consumption
        const val SPORT_DATA_ID_VARIO_SENSOR = 0x0030 //vspeed m/s
        const val SPORT_DATA_ID_VFAS_SENSOR = 0x0039 //battery voltage
        private val TAG: String = "FrSky Protocol"
    }

    override fun process(data: Int) {
        when (state) {
            Companion.State.IDLE -> {
                if (data == SPORT_START_BYTE) {
                    state = Companion.State.DATA
                }
            }
            Companion.State.DATA -> {
                if (data == SPORT_DATA_STUFF) {
                    state = Companion.State.XOR
                } else if (data == SPORT_START_BYTE) {
                    bufferIndex = 0
                } else {
                    buffer[bufferIndex++] = data
                }
            }
            Companion.State.XOR -> {
                buffer[bufferIndex++] = data xor SPORT_STUFF_MASK
                state = Companion.State.DATA
            }
        }

        if (bufferIndex == SPORT_PACKET_SIZE) {
            state = Companion.State.IDLE
            bufferIndex = 0
            val byteBuffer = ByteBuffer.wrap(buffer.foldIndexed(ByteArray(buffer.size)) { i, a, v ->
                a.apply {
                    set(
                        i,
                        v.toByte()
                    )
                }
            }).order(ByteOrder.LITTLE_ENDIAN)
            val sensorType = byteBuffer.get()
            val packetType = byteBuffer.get()
            if (packetType.toInt() == SPORT_DATA_START) {
                val dataType = byteBuffer.short.toInt() and 0xffff
                val rawData = byteBuffer.int
                when (dataType.toInt()) {
                    SPORT_FUEL_SENSOR -> {
                        //Log.d(TAG, "Fuel: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                FUEL,
                                rawData
                            )
                        )
                    }
                    SPORT_GPS_SENSOR -> {
                        //Log.d(TAG, "GPS: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                GPS,
                                rawData
                            )
                        )
                    }
                    SPORT_VFAS_SENSOR -> {
                        //Log.d(TAG, "VBAT: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                VBAT_OR_CELL,
                                rawData
                            )
                        )
                    }
                    SPORT_CELL_SENSOR -> {
                        //Log.d(TAG, "Cell voltage: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                CELL_VOLTAGE,
                                rawData
                            )
                        )
                    }
                    SPORT_CURRENT_SENSOR -> {
                        //Log.d(TAG, "Current: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                CURRENT,
                                rawData
                            )
                        )
                    }
                    SPORT_HEADING_SENSOR -> {
                        //Log.d(TAG, "Heading: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                HEADING,
                                rawData
                            )
                        )
                    }
                    SPORT_RSSI_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                RSSI,
                                rawData
                            )
                        )
                    }
                    SPORT_FLYMODE_SENSOR -> {
                        //Log.d(TAG, "Fly mode: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                FLYMODE,
                                rawData
                            )
                        )
                    }
                    SPORT_GPS_STATE_SENSOR -> {
                        //Log.d(TAG, "GPS State: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                GPS_STATE,
                                rawData
                            )
                        )
                    }
                    SPORT_VSPEED_SENSOR -> {
                        //Log.d(TAG, "VSpeed: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                VSPEED,
                                rawData
                            )
                        )
                    }
                    SPORT_GALT_SENSOR -> {
                        //Log.d(TAG, "GAlt: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                GPS_ALTITUDE,
                                rawData
                            )
                        )
                    }
                    SPORT_GSPEED_SENSOR -> {
                        //Log.d(TAG, "GSpeed: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                GSPEED,
                                rawData
                            )
                        )
                    }
                    SPORT_DISTANCE_SENSOR -> {
                        //Log.d(TAG, "Distance: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                DISTANCE,
                                rawData
                            )
                        )
                    }
                    SPORT_ALT_SENSOR -> {
                        //Log.d(TAG, "Altitutde: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                ALTITUDE,
                                rawData
                            )
                        )
                    }
                    SPORT_PITCH_SENSOR -> {
                        //Log.d(TAG, "Pitch: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                PITCH,
                                rawData
                            )
                        )
                    }
                    SPORT_ROLL_SENSOR -> {
                        //Log.d(TAG, "Roll: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                ROLL,
                                rawData
                            )
                        )
                    }
                    SPORT_PITCH_SENSOR_BETAFLIGHT -> {
                        //Log.d(TAG, "Pitch: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                PITCH,
                                rawData
                            )
                        )
                    }
                    SPORT_ROLL_SENSOR_BETAFLIGHT -> {
                        //Log.d(TAG, "Roll: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                ROLL,
                                rawData
                            )
                        )
                    }
                    SPORT_AIRSPEED_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ASPEED, rawData)
                        )
                    }
                    SPORT_DATA_ID_GPS_ALT_BP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(GPS_ALTITUDE, rawData) //gps altitude integer part
                        )
                    }
                    SPORT_DATA_ID_TEMP1_SENSOR -> {

                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(FLYMODE, rawData)
                        )
                    }
                    SPORT_DATA_ID_FUEL_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(FUEL, rawData)
                        )
                    }
                    SPORT_DATA_ID_TEMP2_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(GPS_STATE_ARDU, rawData)
                        )
                    }
                    SPORT_DATA_ID_GPS_ALT_AP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_ALT_AP, rawData)
                        )
                    }
                    SPORT_DATA_ID_BARO_ALT_BP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_ALT_BP, rawData)
                        )
                    }
                    SPORT_DATA_ID_GPS_SPEED_BP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_SPEED_BP, rawData)
                        )
                    }
                    SPORT_DATA_ID_GPS_LONG_BP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_LONG_BP, rawData)
                        )
                    }
                    SPORT_DATA_ID_GPS_LAT_BP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_LAT_BP, rawData)
                        )
                    }
                    SPORT_DATA_ID_GPS_COURS_BP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_COURS_BP, rawData)
                        )
                    }
                    SPORT_DATA_ID_GPS_SPEED_AP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_SPEED_AP, rawData)
                        )
                    }
                    SPORT_DATA_ID_GPS_LONG_AP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_LONG_AP, rawData)
                        )
                    }
                    SPORT_DATA_ID_GPS_LAT_AP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_LAT_AP, rawData)
                        )
                    }
                    SPORT_DATA_ID_BARO_ALT_AP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_BARO_ALT_AP, rawData)
                        )
                    }
                    SPORT_DATA_ID_GPS_LONG_EW_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_LONG_EW, rawData)
                        )
                    }
                    SPORT_DATA_ID_GPS_LAT_NS_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_LAT_NS, rawData)
                        )
                    }
                    SPORT_DATA_ID_ACC_X -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_ACC_X_1000, rawData)
                        )
                    }
                    SPORT_DATA_ID_ACC_Y -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_ACC_Y_1000, rawData)
                        )
                    }
                    SPORT_DATA_ID_ACC_Z-> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_ACC_Z_1000, rawData)
                        )
                    }
                    SPORT_DATA_ID_ACC_X_BETAFLIGHT -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_ACC_X_100, rawData)
                        )
                    }
                    SPORT_DATA_ID_ACC_Y_BETAFLIGHT -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_ACC_Y_100, rawData)
                        )
                    }
                    SPORT_DATA_ID_ACC_Z_BETAFLIGHT-> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_ACC_Z_100, rawData)
                        )
                    }
                    SPORT_DATA_ID_CURRENT_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(CURRENT, rawData)
                        )
                    }
                    SPORT_DATA_ID_VARIO_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(VSPEED, rawData)
                        )
                    }
                    SPORT_DATA_ID_VFAS_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(VBAT, rawData)
                        )
                    }
                    SPORT_RxBt_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(RxBt, rawData)
                        )
                    }
                    SPORT_ARDU_TEXT_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_TEXT, rawData)
                        )
                    }
                    SPORT_ARDU_ATTITUDE_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_ATTITUDE, rawData)
                        )
                    }
                    SPORT_ARDU_VEL_YAW_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_VEL_YAW, rawData)
                        )
                    }
                    SPORT_ARDU_AP_STATUS_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_AP_STATUS, rawData)
                        )
                    }
                    SPORT_ARDU_GPS_STATUS_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_GPS_STATUS, rawData)
                        )
                    }
                    SPORT_ARDU_HOME_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_HOME, rawData)
                        )
                    }
                    SPORT_ARDU_BATT_2_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_BATT_2, rawData)
                        )
                    }
                    SPORT_ARDU_BATT_1_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_BATT_1, rawData)
                        )
                    }
                    SPORT_ARDU_PARAM_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_PARAM, rawData)
                        )
                    }
                    else -> {
                        //Log.d(TAG, "Unknown packet Datatype 0x" + Integer.toHexString(dataType) +" " + buffer.contentToString())
                    }
                }
            }
        }
    }
}