package crazydude.com.telemetry

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder


class FrSkySportProtocol(var dataListener: DataListener) {

    private var state: State = State.IDLE
    private var bufferIndex: Int = 0
    private var buffer: IntArray = IntArray(PACKET_SIZE)
    private var longitude: Float = 0f
    private var latitude: Float = 0f

    companion object {
        enum class State {
            IDLE, DATA, XOR
        }

        const val PACKET_SIZE = 0x09

        const val START_BYTE = 0x7E
        const val DATA_START = 0x10
        const val DATA_STUFF = 0x7D
        const val STUFF_MASK = 0x20

        const val FC_SENSORS = 0x1B

        const val FUEL_SENSOR = 0x0600
        const val GPS_SENSOR = 0x0800

        interface DataListener {
            fun onNewData(data: TelemetryData)
        }

        enum class TelemetryType {
            FUEL,
            GPS
        }

        data class TelemetryData(val telemetryType: TelemetryType, val data: Int)
    }

    fun process(data: Int) {
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
                Log.d("FrSky Protocol", "New packet:" + buffer.contentToString())
                val dataType = byteBuffer.short
                val rawData = byteBuffer.int
                if (dataType == FUEL_SENSOR.toShort()) {
                    Log.d("FrSky Protocol", "Fuel: $rawData")
                    dataListener.onNewData(TelemetryData(Companion.TelemetryType.FUEL, rawData))
                } else if (dataType == GPS_SENSOR.toShort()) {
                    Log.d("FrSky Protocol", "GPS: $rawData")
                    dataListener.onNewData(TelemetryData(Companion.TelemetryType.GPS, rawData))
                }
            }
        }
    }
}