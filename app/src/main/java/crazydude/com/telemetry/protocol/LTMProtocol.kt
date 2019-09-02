package crazydude.com.telemetry.protocol

import android.util.Log
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import crazydude.com.telemetry.protocol.decoder.FrskyDataDecoder
import crazydude.com.telemetry.protocol.decoder.LTMDataDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder


class LTMProtocol(
    dataListener: DataDecoder.Listener,
    dataDecoder: DataDecoder = LTMDataDecoder(dataListener)
) : Protocol(dataDecoder) {

    private var state: State = Companion.State.HEADER
    private var bufferIndex: Int = 0
    private var buffer: ByteArray = ByteArray(MAX_PACKET_SIZE)
    private lateinit var packetType : PacketType
    private var packetSize = 0

    companion object {
        enum class State {
            HEADER, HEADER2, TYPE, DATA
        }

        enum class PacketType {
            GPS, ATTITUDE, STATUS, ORIGIN, NAVIGATION, EXTRA
        }

        const val PACKET_MARKER = "\$T"
        const val MAX_PACKET_SIZE = 14
    }

    override fun process(data: Int) {
        when (state) {
            Companion.State.HEADER -> {
                if (data.toChar() == PACKET_MARKER[0]) {
                    packetSize = 0
                    bufferIndex = 0
                    state = Companion.State.HEADER2
                }
            }
            Companion.State.HEADER2 -> {
                if (data.toChar() == PACKET_MARKER[1]) {
                    state = Companion.State.TYPE
                }
            }
            Companion.State.TYPE -> {
                when(data.toChar()) {
                    'G' -> {
                        packetSize = 14
                        state = Companion.State.DATA
                        packetType = Companion.PacketType.GPS
                    }
                    'A' -> {
                        packetSize = 6
                        state = Companion.State.DATA
                        packetType = Companion.PacketType.ATTITUDE
                    }
                    'S' -> {
                        packetSize = 7
                        state = Companion.State.DATA
                        packetType = Companion.PacketType.STATUS
                    }
                    'O' -> {
                        packetSize = 14
                        state = Companion.State.DATA
                        packetType = Companion.PacketType.ORIGIN
                    }
                    'N' -> {
                        packetSize = 6
                        state = Companion.State.DATA
                        packetType = Companion.PacketType.NAVIGATION
                    }
                    'X' -> {
                        packetSize = 6
                        state = Companion.State.DATA
                        packetType = Companion.PacketType.EXTRA
                    }
                    else -> {
                        state = Companion.State.HEADER
                    }
                }
            }
            Companion.State.DATA -> {
                if (bufferIndex < packetSize) {
                    buffer[bufferIndex] = data.toByte()
                    bufferIndex++
                } else {
                    when (packetType) {
                        Companion.PacketType.GPS -> {
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(GPS, 0, buffer))
                        }
                        Companion.PacketType.ATTITUDE -> {
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(ATTITUDE, 0, buffer))
                        }
                        Companion.PacketType.STATUS -> {
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(VBAT, 0, buffer.copyOfRange(0, 2)))
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(FUEL, 0, buffer.copyOfRange(2, 4)))
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(FLYMODE, 0, buffer.copyOfRange(6, 7)))
                        }
                        Companion.PacketType.ORIGIN -> {}
                        Companion.PacketType.NAVIGATION -> {}
                        Companion.PacketType.EXTRA -> {}
                    }
                    state = Companion.State.HEADER
                }
            }
        }
    }
}