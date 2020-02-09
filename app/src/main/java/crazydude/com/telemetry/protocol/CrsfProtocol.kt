package crazydude.com.telemetry.protocol

import android.util.Log
import crazydude.com.telemetry.protocol.decoder.CrsfDataDecoder
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.nio.ByteBuffer


class CrsfProtocol : Protocol {

    constructor(dataListener: DataDecoder.Listener) : super(CrsfDataDecoder(dataListener))
    constructor(dataDecoder: DataDecoder) : super(dataDecoder)

    private var buffer = ArrayList<Int>()
    val crC8 = CRC8()

    companion object {

        // Device or sync byte:Length:Type:Payload:CRC
        private const val RADIO_ADDRESS = 0xEA
        private const val SYNC_BYTE = 0xC8

        private const val BATTERY_TYPE = 0x08
        private const val GPS_TYPE = 0x02
        private const val ATTITUDE_TYPE = 0x1E
        private const val FLIGHT_MODE = 0x21
    }

    override fun process(data: Int) {
        buffer.add(data)
        if (buffer[0] == RADIO_ADDRESS) {
            if (buffer.size > 5) {
                val frameLength = buffer[1]
                if (frameLength < buffer.size - 1) {
                    val payload = buffer.subList(2, frameLength + 1)
                    val frameCrc = buffer[frameLength + 1]
                    crC8.reset()
                    payload.map { it.toByte() }.forEach { crC8.update(it) }
                    val calculatedCrc = crC8.value.toUByte().toInt()
                    if (frameCrc == calculatedCrc) {
                        proccessFrame(payload.map { it.toByte() }.toByteArray())
                        Log.d("CrsfProtocol", "Good frame $payload")
                        buffer = ArrayList(buffer.drop(frameLength + 2))
                    } else {
                        Log.d("CrsfProtocol", "Bad CRC")
                        buffer.removeAt(0)
                    }
                }
            }
        } else {
            buffer.removeAt(0)
        }
    }

    private fun proccessFrame(inputData: ByteArray) {
        val data = ByteBuffer.wrap(inputData)
        val type = data.get()
        when (type) {
            BATTERY_TYPE.toByte() -> {
                val voltage = data.short
                val current = data.short
                val capacityArray = ByteArray(4)
                data.get(capacityArray, 1, 3)
                val capacity = ByteBuffer.wrap(capacityArray).int
                dataDecoder.decodeData(
                    Protocol.Companion.TelemetryData(
                        VBAT,
                        voltage.toInt()
                    )
                )
                dataDecoder.decodeData(
                    Protocol.Companion.TelemetryData(
                        CURRENT,
                        current.toInt()
                    )
                )
                dataDecoder.decodeData(Protocol.Companion.TelemetryData(FUEL, capacity))
            }
            GPS_TYPE.toByte() -> {
                val latitude = data.int
                val longitude = data.int
                val groundSpeed = data.short
                val heading = data.short
                val altitude = data.short
                val satellites = data.get()
                dataDecoder.decodeData(
                    Protocol.Companion.TelemetryData(
                        GPS_SATELLITES,
                        satellites.toInt()
                    )
                )
                dataDecoder.decodeData(
                    Protocol.Companion.TelemetryData(
                        GPS_LATITUDE,
                        latitude
                    )
                )
                dataDecoder.decodeData(
                    Protocol.Companion.TelemetryData(
                        GPS_LONGITUDE,
                        longitude
                    )
                )
                dataDecoder.decodeData(
                    Protocol.Companion.TelemetryData(
                        GSPEED,
                        groundSpeed.toInt()
                    )
                )
                dataDecoder.decodeData(
                    Protocol.Companion.TelemetryData(
                        HEADING,
                        heading.toInt()
                    )
                )
                dataDecoder.decodeData(
                    Protocol.Companion.TelemetryData(
                        ALTITUDE,
                        altitude.toInt()
                    )
                )
            }
            FLIGHT_MODE.toByte() -> {
                val byteArray = ByteArray(255)
                var pos = 0
                do {
                    val byte = data.get()
                    byteArray[pos] = byte
                    pos++
                } while (byte != 0x00.toByte())
                dataDecoder.decodeData(
                    Protocol.Companion.TelemetryData(
                        FLYMODE,
                        0,
                        byteArray
                    )
                )
            }
            ATTITUDE_TYPE.toByte() -> {
                val pitch = data.short
                val roll = data.short
                val yaw = data.short
                dataDecoder.decodeData(Protocol.Companion.TelemetryData(PITCH, pitch.toInt()))
                dataDecoder.decodeData(Protocol.Companion.TelemetryData(ROLL, roll.toInt()))
                dataDecoder.decodeData(Protocol.Companion.TelemetryData(YAW, yaw.toInt()))
            }
        }
    }
}