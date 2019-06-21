package crazydude.com.telemetry.protocol

import android.util.Log
import androidx.annotation.IntDef
import java.nio.ByteBuffer
import java.nio.ByteOrder


class FrSkySportProtocol(dataListener: Protocol.Companion.DataListener) : Protocol(dataListener) {

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
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                FUEL,
                                rawData
                            )
                        )
                    }
                    GPS_SENSOR -> {
                        //Log.d(TAG, "GPS: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                GPS,
                                rawData
                            )
                        )
                    }
                    VFAS_SENSOR -> {
                        //Log.d(TAG, "VBAT: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                VBAT,
                                rawData
                            )
                        )
                    }
                    CELL_SENSOR -> {
                        //Log.d(TAG, "Cell voltage: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                CELL_VOLTAGE,
                                rawData
                            )
                        )
                    }
                    CURRENT_SENSOR -> {
                        //Log.d(TAG, "Current: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                CURRENT,
                                rawData
                            )
                        )
                    }
                    HEADING_SENSOR -> {
                        //Log.d(TAG, "Heading: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                HEADING,
                                rawData
                            )
                        )
                    }
                    RSSI_SENSOR -> {
                        //Log.d(TAG, "RSSI: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                RSSI,
                                rawData
                            )
                        )
                    }
                    FLYMODE_SENSOR -> {
                        //Log.d(TAG, "Fly mode: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                FLYMODE,
                                rawData
                            )
                        )
                    }
                    GPS_STATE_SENSOR -> {
                        //Log.d(TAG, "GPS State: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                GPS_STATE,
                                rawData
                            )
                        )
                    }
                    VSPEED_SENSOR -> {
                        //Log.d(TAG, "VSpeed: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                VSPEED,
                                rawData
                            )
                        )
                    }
                    GALT_SENSOR -> {
                        //Log.d(TAG, "GAlt: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                GALT,
                                rawData
                            )
                        )
                    }
                    GSPEED_SENSOR -> {
                        //Log.d(TAG, "GSpeed: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                GSPEED,
                                rawData
                            )
                        )
                    }
                    DISTANCE_SENSOR -> {
                        //Log.d(TAG, "Distance: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                DISTANCE,
                                rawData
                            )
                        )
                    }
                    ALT_SENSOR -> {
                        //Log.d(TAG, "Altitutde: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                ALTITUDE,
                                rawData
                            )
                        )
                    }
                    PITCH_SENSOR -> {
                        //Log.d(TAG, "Pitch: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                PITCH,
                                rawData
                            )
                        )
                    }
                    ROLL_SENSOR -> {
                        //Log.d(TAG, "Roll: $rawData")
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(
                                ROLL,
                                rawData
                            )
                        )
                    }
                    AIRSPEED_SENSOR -> {
                        dataListener.onNewData(
                            Protocol.Companion.TelemetryData(ASPEED, rawData)
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