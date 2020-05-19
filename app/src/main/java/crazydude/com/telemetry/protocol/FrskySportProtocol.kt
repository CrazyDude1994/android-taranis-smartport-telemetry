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
    private var buffer: IntArray = IntArray(PACKET_SIZE)

    companion object {
        enum class State {
            IDLE, DATA, XOR
        }

        const val PACKET_SIZE = 0x09

        const val START_BYTE = 0x7E
        const val DATA_START = 0x10
        const val DATA_STUFF = 0x7D
        const val STUFF_MASK = 0x20

        const val VFAS_SENSOR = 0x0210
        const val CELL_SENSOR = 0x0910
        const val VSPEED_SENSOR = 0x0110
        const val GSPEED_SENSOR = 0x0830
        const val ALT_SENSOR = 0x0100
        const val GALT_SENSOR = 0x0820
        const val DISTANCE_SENSOR = 0x0420
        const val FUEL_SENSOR = 0x0600
        const val GPS_SENSOR = 0x0800
        const val CURRENT_SENSOR = 0x200
        const val HEADING_SENSOR = 0x0840
        const val RSSI_SENSOR = 0xF101
        const val FLYMODE_SENSOR = 0x0400
        const val GPS_STATE_SENSOR = 0x0410
        const val PITCH_SENSOR = 0x0430
        const val ROLL_SENSOR = 0x0440
        const val AIRSPEED_SENSOR = 0x0A00
        //ardupilot passthrough sensors
        const val ARDU_TEXT_SENSOR = 0x5000 // status text (dynamic)
        const val ARDU_ATTITUDE_SENSOR = 0x5006 //Attitude and range (dynamic)
        //set_scheduler_entry(GPS_LAT, 550, 280);     // 0x800 GPS lat
        //set_scheduler_entry(GPS_LON, 550, 280);     // 0x800 GPS lon
        const val ARDU_VEL_YAW_SENSOR = 0x5005 //Vel and Yaw
        const val ARDU_AP_STATUS_SENSOR = 0x5001 //AP status
        const val ARDU_GPS_STATUS_SENSOR = 0x5002 //GPS status
        const val ARDU_HOME_SENSOR =  0x5004   //Home
        const val ARDU_BATT_2_SENSOR = 0x5008  // Battery 2 status
        const val ARDU_BATT_1_SENSOR = 0x5003  // Battery 1 status
        const val ARDU_PARAM_SENSOR = 0x5007   // parameters
        const val RxBt_SENSOR = 0xF104 //https://github.com/Clooney82/MavLink_FrSkySPort/wiki/1.2.-FrSky-Taranis-Telemetry
        //ardupilot S.PORT sensors
        const val DATA_ID_GPS_ALT_BP_SENSOR = 0x0001 //gps altitude integer part
        const val DATA_ID_TEMP1_SENSOR = 0x0002 //flight mode
        const val DATA_ID_FUEL_SENSOR = 0x0004 //battery remaining
        const val DATA_ID_TEMP2_SENSOR = 0x0005 //GPS status and number of satellites as num_sats*10 + status (to fit into a uint8_t)
        const val DATA_ID_GPS_ALT_AP_SENSOR = 0x0009 //gps altitude decimals
        const val DATA_ID_BARO_ALT_BP_SENSOR = 0x0010 //altitude integer part
        const val DATA_ID_GPS_SPEED_BP_SENSOR = 0x0011 //gps speed integer part
        const val DATA_ID_GPS_LONG_BP_SENSOR = 0x0012 //gps longitude degree and minute integer part
        const val DATA_ID_GPS_LAT_BP_SENSOR = 0x0013 //send gps lattitude degree and minute integer part
        const val DATA_ID_GPS_COURS_BP_SENSOR = 0x0014 //heading in degree based on AHRS and not GPS
        const val DATA_ID_GPS_SPEED_AP_SENSOR = 0x0019 //gps speed decimal part
        const val DATA_ID_GPS_LONG_AP_SENSOR = 0x001A //gps longitude minutes decimal part
        const val DATA_ID_GPS_LAT_AP_SENSOR = 0x001B //send gps lattitude minutes decimal part
        const val DATA_ID_BARO_ALT_AP_SENSOR = 0x0021 //gps altitude decimal part
        const val DATA_ID_GPS_LONG_EW_SENSOR = 0x0022 //gps East / West information
        const val DATA_ID_GPS_LAT_NS_SENSOR = 0x0023 //gps North / South information
        const val DATA_ID_CURRENT_SENSOR = 0x0028 //current consumption
        const val DATA_ID_VARIO_SENSOR = 0x0030 //vspeed m/s
        const val DATA_ID_VFAS_SENSOR = 0x0039 //battery voltage
        private val TAG: String = "FrSky Protocol"
    }

    override fun process(data: Int) {
        when (state) {
            Companion.State.IDLE -> {
                if (data == START_BYTE) {
                    state = Companion.State.DATA
                    bufferIndex = 0
                }
            }
            Companion.State.DATA -> {
                if (data == DATA_STUFF) {
                    state = Companion.State.XOR
                } else if (data == START_BYTE) {
                    bufferIndex = 0
                } else {
                    buffer[bufferIndex++] = data
                }
            }
            Companion.State.XOR -> {
                buffer[bufferIndex++] = data xor STUFF_MASK
                state = Companion.State.DATA
            }
        }

        if (bufferIndex == PACKET_SIZE) {
            state = Companion.State.IDLE
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
            if (packetType.toInt() == DATA_START) {
                val dataType = byteBuffer.short
                val rawData = byteBuffer.int
                when (dataType.toInt()) {
                    FUEL_SENSOR -> {
                        //Log.d(TAG, "Fuel: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                FUEL,
                                rawData
                            )
                        )
                    }
                    GPS_SENSOR -> {
                        //Log.d(TAG, "GPS: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                GPS,
                                rawData
                            )
                        )
                    }
                    VFAS_SENSOR -> {
                        //Log.d(TAG, "VBAT: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                VBAT,
                                rawData
                            )
                        )
                    }
                    CELL_SENSOR -> {
                        //Log.d(TAG, "Cell voltage: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                CELL_VOLTAGE,
                                rawData
                            )
                        )
                    }
                    CURRENT_SENSOR -> {
                        //Log.d(TAG, "Current: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                CURRENT,
                                rawData
                            )
                        )
                    }
                    HEADING_SENSOR -> {
                        //Log.d(TAG, "Heading: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                HEADING,
                                rawData
                            )
                        )
                    }
                    RSSI_SENSOR -> {
                        //Log.d(TAG, "RSSI: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                RSSI,
                                rawData
                            )
                        )
                    }
                    FLYMODE_SENSOR -> {
                        //Log.d(TAG, "Fly mode: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                FLYMODE,
                                rawData
                            )
                        )
                    }
                    GPS_STATE_SENSOR -> {
                        //Log.d(TAG, "GPS State: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                GPS_STATE,
                                rawData
                            )
                        )
                    }
                    VSPEED_SENSOR -> {
                        //Log.d(TAG, "VSpeed: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                VSPEED,
                                rawData
                            )
                        )
                    }
                    GALT_SENSOR -> {
                        //Log.d(TAG, "GAlt: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                GALT,
                                rawData
                            )
                        )
                    }
                    GSPEED_SENSOR -> {
                        //Log.d(TAG, "GSpeed: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                GSPEED,
                                rawData
                            )
                        )
                    }
                    DISTANCE_SENSOR -> {
                        //Log.d(TAG, "Distance: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                DISTANCE,
                                rawData
                            )
                        )
                    }
                    ALT_SENSOR -> {
                        //Log.d(TAG, "Altitutde: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                ALTITUDE,
                                rawData
                            )
                        )
                    }
                    PITCH_SENSOR -> {
                        //Log.d(TAG, "Pitch: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                PITCH,
                                rawData
                            )
                        )
                    }
                    ROLL_SENSOR -> {
                        //Log.d(TAG, "Roll: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                ROLL,
                                rawData
                            )
                        )
                    }
                    AIRSPEED_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ASPEED, rawData)
                        )
                    }
                    DATA_ID_GPS_ALT_BP_SENSOR -> {
                        //Log.d(TAG, "ARDU_TEXT_SENSOR: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(GALT, rawData) //gps altitude integer part
                        )
                    }
                    DATA_ID_TEMP1_SENSOR -> {

                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(FLYMODE, rawData)
                        )
                    }
                    DATA_ID_FUEL_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(FUEL, rawData)
                        )
                    }
                    DATA_ID_TEMP2_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(GPS_STATE_ARDU, rawData)
                        )
                    }
                    DATA_ID_GPS_ALT_AP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_ALT_AP, rawData)
                        )
                    }
                    DATA_ID_BARO_ALT_BP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_ALT_BP, rawData)
                        )
                    }
                    DATA_ID_GPS_SPEED_BP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_SPEED_BP, rawData)
                        )
                    }
                    DATA_ID_GPS_LONG_BP_SENSOR -> {
                      //  Log.d(TAG, "DATA_ID_GPS_LONG_BP_SENSOR: $rawData")
                      //  Log.d(TAG, "DATA_ID_GPS_LONG_BP_SENSOR" + buffer.contentToString())
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_LONG_BP, rawData)
                        )
                    }
                    DATA_ID_GPS_LAT_BP_SENSOR -> {
                      //  Log.d(TAG, "DATA_ID_GPS_LAT_BP_SENSOR: $rawData")
                     //   Log.d(TAG, "DATA_ID_GPS_LAT_BP_SENSOR" + buffer.contentToString())
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_LAT_BP, rawData)
                        )
                    }
                    DATA_ID_GPS_COURS_BP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_COURS_BP, rawData)
                        )
                    }
                    DATA_ID_GPS_SPEED_AP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_SPEED_AP, rawData)
                        )
                    }
                    DATA_ID_GPS_LONG_AP_SENSOR -> {
                      //  Log.d(TAG, "DATA_ID_GPS_LONG_AP_SENSOR: $rawData")
                     //   Log.d(TAG, "DATA_ID_GPS_LONG_AP_SENSOR" + buffer.contentToString())
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_LONG_AP, rawData)
                        )
                    }
                    DATA_ID_GPS_LAT_AP_SENSOR -> {
                    //    Log.d(TAG, "DATA_ID_GPS_LAT_AP_SENSOR: $rawData")
                    //    Log.d(TAG, "DATA_ID_GPS_LAT_AP_SENSOR" + buffer.contentToString())
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_LAT_AP, rawData)
                        )
                    }
                    DATA_ID_BARO_ALT_AP_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_BARO_ALT_AP, rawData)
                        )
                    }
                    DATA_ID_GPS_LONG_EW_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_LONG_EW, rawData)
                        )
                    }
                    DATA_ID_GPS_LAT_NS_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(DATA_ID_GPS_LAT_NS, rawData)
                        )
                    }
                    DATA_ID_CURRENT_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(CURRENT, rawData)
                        )
                    }
                    DATA_ID_VARIO_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(VSPEED, rawData)
                        )
                    }
                    DATA_ID_VFAS_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(VBAT, rawData)
                        )
                    }
                    RxBt_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(RxBt, rawData)
                        )
                    }
                    ARDU_TEXT_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_TEXT, rawData)
                        )
                    }
                    ARDU_ATTITUDE_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_ATTITUDE, rawData)
                        )
                    }
                    ARDU_VEL_YAW_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_VEL_YAW, rawData)
                        )
                    }
                    ARDU_AP_STATUS_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_AP_STATUS, rawData)
                        )
                    }
                    ARDU_GPS_STATUS_SENSOR -> {
                        Log.d(TAG, "ARDU_GPS_STATUS: $rawData")
                        Log.d(TAG, "ARDU_GPS_STATUS" + buffer.contentToString())
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_GPS_STATUS, rawData)
                        )
                    }
                    ARDU_HOME_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_HOME, rawData)
                        )
                    }
                    ARDU_BATT_2_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_BATT_2, rawData)
                        )
                    }
                    ARDU_BATT_1_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_BATT_1, rawData)
                        )
                    }
                    ARDU_PARAM_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ARDU_PARAM, rawData)
                        )
                    }

                    else -> {
                        Log.d(TAG, "Unknown packet" + buffer.contentToString())
                    }
                }
            }
        }
    }
}