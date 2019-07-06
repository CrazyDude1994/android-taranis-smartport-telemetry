package crazydude.com.telemetry.protocol

import crazydude.com.telemetry.protocol.decoder.CrsfDataDecoder
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.nio.ByteBuffer


class CrsfProtocol(dataListener: DataDecoder.Listener) : Protocol(CrsfDataDecoder(dataListener)) {

    private var bufferIndex = 0
    private var buffer: ByteArray = ByteArray(MAX_PACKET_SIZE)
    private var state: State = Companion.State.IDLE

    companion object {

        enum class State {
            IDLE, LENGTH, DATA
        }

        // Device:Length:Type:Payload:CRC
        private const val RADIO_ADDRESS = 0xEA
        private const val MAX_PACKET_SIZE = 62

        private const val BATTERY_TYPE = 0x08
        private const val GPS_TYPE = 0x02
    }

    override fun process(data: Int) {
        when (state) {
            Companion.State.IDLE -> {
                if (data == RADIO_ADDRESS) {
                    state = Companion.State.LENGTH
                    bufferIndex = 0
                }
            }
            Companion.State.LENGTH -> {
                if (data > MAX_PACKET_SIZE) {
                    state = Companion.State.IDLE
                } else {
                    if (data > 2) {
                        state = Companion.State.DATA
                        buffer[0] = data.toByte()
                    } else {
                        state = Companion.State.IDLE
                    }
                }
            }
            Companion.State.DATA -> {
                if (bufferIndex < buffer[0]) {
                    buffer[++bufferIndex] = data.toByte()
                }
                if (bufferIndex == buffer[0].toInt()) {
                    state = Companion.State.IDLE
                    val data = ByteBuffer.wrap(buffer, 1, buffer[0].toInt())
                    val type = data.get()
                    when(type) {
                        BATTERY_TYPE.toByte() -> {
                            val voltage = data.short
                            val current = data.short
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(VBAT, voltage.toInt() * 10))
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(CURRENT, current.toInt()))
                        }
                        GPS_TYPE.toByte() -> {
                            val latitude = data.int
                            val longitude = data.int
                            val groundSpeed = data.short
                            val heading = data.short
                            val altitude = data.short
                            val satellites = data.get()
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(GPS_LATITUDE, latitude))
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(GPS_LONGITUDE, longitude))
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(GSPEED, groundSpeed.toInt()))
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(HEADING, heading.toInt()))
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(ALTITUDE, altitude.toInt()))
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(GPS_SATELLITES, satellites.toInt()))
                        }
                    }
                }
            }
        }
    }
}