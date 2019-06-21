package crazydude.com.telemetry.protocol

import android.util.Log
import androidx.annotation.IntDef
import java.nio.ByteBuffer
import java.nio.ByteOrder


class CrsfProtocol(dataListener: Protocol.Companion.DataListener) : Protocol(dataListener) {

    private var bufferIndex = 0
    private var buffer: ByteArray = ByteArray(MAX_PACKET_SIZE)
    private var state: State = Companion.State.IDLE

    companion object {

        enum class State {
            IDLE, LENGTH, DATA, CRC
        }

        // Device:Length:Type:Payload:CRC
        private const val RADIO_ADDRESS = 0xEA.toByte().toInt()
        private const val MAX_PACKET_SIZE = 62

        private const val BATTERY_TYPE = 0x08
    }

    override fun process(data: Int) {
        when(state) {
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
                    bufferIndex++
                }
                if (bufferIndex == buffer[0].toInt()) {
                    state = Companion.State.CRC
                }
            }
            Companion.State.CRC -> {
                state = Companion.State.IDLE
                val data = ByteBuffer.wrap(buffer, 1, buffer[0].toInt())
                val type = data.get()
                if (type == BATTERY_TYPE.toByte()) {

                }
            }
        }
    }
}