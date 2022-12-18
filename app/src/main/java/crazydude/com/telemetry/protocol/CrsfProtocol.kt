package crazydude.com.telemetry.protocol

import crazydude.com.telemetry.protocol.crc.CRC8
import crazydude.com.telemetry.protocol.decoder.CrsfDataDecoder
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.nio.ByteBuffer


class CrsfProtocol : Protocol {

    constructor(dataListener: DataDecoder.Listener) : super(CrsfDataDecoder(dataListener))
    constructor(dataDecoder: DataDecoder) : super(dataDecoder)

    private var buffer = ArrayList<Int>()
    private val crC8 = CRC8()

    companion object {

        // Device or sync byte:Length:Type:Payload:CRC
        private const val RADIO_ADDRESS = 0xEA
        private const val SYNC_BYTE = 0xC8

        private const val GPS_TYPE = 0x02
        private const val VARIO_TYPE = 0x07
        private const val BATTERY_TYPE = 0x08
        private const val LINK_STATS = 0x14
        private const val ATTITUDE_TYPE = 0x1E
        private const val FLIGHT_MODE = 0x21
        private const val RC_CHANNELS_PACKED = 0x16

        private const val MAX_BUFFER_FILL_LIMIT = 128
        private const val MIN_BUFFER_FILL_LEVEL_BEFORE_LOOKING_FOR_VALID_PACKETS = 20 //arbitrary number which is probably bigger than most packets
        private const val MAX_PAYLOAD_SIZE = 62
        private const val MIN_PAYLOAD_SIZE = 5

        //packet length + 1 byte crc
        private const val MIN_FLIGHT_MODE_PACKET_LEN = 4
        private const val GPS_PACKET_LEN = 16
        private const val ATTITUDE_PACKET_LEN = 7
        private const val BATTERY_PACKET_LEN = 9
        private const val VARIO_PACKET_LEN = 3
        private const val LINK_STATS_PACKET_LEN = 11
        private const val RC_CHANNELS_PACKED_PACKET_LEN = 23;
    }

    override fun process(data: Int) {
        buffer.add(data)
        if (buffer.size > MAX_BUFFER_FILL_LIMIT) {
            buffer.removeAt(0)
        }
        if (buffer.size > MIN_BUFFER_FILL_LEVEL_BEFORE_LOOKING_FOR_VALID_PACKETS) {
            //Get and process any valid packets in the input buffer, removing data from the input buffer as we go
            getAndProcessValidPackets()
        }
    }

    private fun getAndProcessValidPackets() {
        var startCharPos = 0
        var pos = 0
        // Scan the whole input buffer
        while (pos < buffer.size) {
            // look for start characters
            if (buffer[pos] == RADIO_ADDRESS) {
                startCharPos = pos
                // is the input buffer big enough to include a length field for this start of packet
                if (pos + 1 < buffer.size) {
                    val packetLen = buffer[pos + 1]
                    if ((packetLen <= MAX_PAYLOAD_SIZE) && (packetLen >= MIN_PAYLOAD_SIZE)) {
                        // is the input buffer big enough to include the whole packet (as specified by the length field)
                        if (pos + 1 + packetLen < buffer.size) {
                            // Get the CRC from the packet and check it against what we think it should be
                            val frameCrc = buffer[pos + 1 + packetLen]
                            val payload = buffer.subList(pos + 2, pos + 1 + packetLen)
                            crC8.reset()
                            payload.map { it.toByte() }.forEach { crC8.update(it) }
                            val calculatedCrc = crC8.value.toUByte().toInt()
                            if (frameCrc == calculatedCrc) {
                                //Log.d("CrsfProtocol", "Good frame $payload")
                                proccessFrame(payload.map { it.toByte() }.toByteArray())
                                buffer.subList(0, pos + 2 + packetLen).clear()
                                return
                            }
                        } else {
                            // potential valid packet, but there is not enough data in the input buffer to process it yet
                            break
                        }
                    }
                } else {
                    break
                }
            }
            pos++
        }
        // remove any data in front of the last processed start character
        if (startCharPos > 0) {
            buffer.subList(0, startCharPos).clear()
        }
    }

    private fun remapChannel(value : Int) : Int {
        //The values are CRSF channel values (0-1984).
        // CRSF 172 represents 988us, CRSF 992 represents 1500us,
        // and CRSF 1811 represents 2012us.
        // Example: All channels set to 1500us (992)
        var v = value - 992;
        v = v * (2012 - 988) / (1811-172);
        return v + 1500;
    }

    private fun proccessFrame(inputData: ByteArray) {
        if (inputData.size > 0) {
            val data = ByteBuffer.wrap(inputData)
            val type = data.get()
            when (type) {
                BATTERY_TYPE.toByte() -> {
                    if (inputData.size == BATTERY_PACKET_LEN) {
                        val voltage = data.short
                        val current = data.short
                        val capacityArray = ByteArray(4)
                        data.get(capacityArray, 1, 3)
                        val capacity = ByteBuffer.wrap(capacityArray).int

                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( VBAT_OR_CELL, voltage.toInt()))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( CURRENT, current.toInt()))
                        dataDecoder.decodeData(Protocol.Companion.TelemetryData(FUEL, capacity))
                    }
                }
                GPS_TYPE.toByte() -> {
                    if (inputData.size == GPS_PACKET_LEN) {
                        val latitude = data.int
                        val longitude = data.int
                        val groundSpeed = data.short
                        val heading = data.short
                        val altitude = data.short
                        val satellites = data.get()

                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( GPS_SATELLITES, satellites.toInt()))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( GPS_ALTITUDE, altitude.toInt()))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( GPS_LATITUDE,latitude ))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( GPS_LONGITUDE, longitude ))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( GSPEED,groundSpeed.toInt()))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( HEADING, heading.toInt()))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( ALTITUDE, altitude.toInt() ))
                    }
                }
                VARIO_TYPE.toByte() -> {
                    if (inputData.size == VARIO_PACKET_LEN) {
                        val vspeed = data.short

                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( VSPEED, vspeed.toInt()))
                    }
                }
                LINK_STATS.toByte() -> {
                    if (inputData.size == LINK_STATS_PACKET_LEN) {
                        val uplinkRSSIAnt1 = -1 * data.get().toUByte().toInt() // dBm
                        val uplinkRSSIAnt2 = -1 * data.get().toUByte().toInt() // dBm
                        val uplinkLQ = data.get().toUByte().toInt()
                        val uplinkSNR = data.get().toInt()
                        val activeAntenna = data.get().toUByte().toInt()
                        val rfMode = data.get().toUByte().toInt()
                        val uplinkTxPwr = data.get().toUByte().toInt()
                        val downlinkRSSI = -1 * data.get().toUByte().toInt() // dBm
                        val downlinkLQ = data.get().toUByte().toInt()
                        val downlinkSNR = data.get().toInt()
                        val rssi = (if (activeAntenna == 1) uplinkRSSIAnt2 else uplinkRSSIAnt1)

                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( RSSI, rssi, inputData ))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( CRSF_UP_LQ, uplinkLQ, inputData))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( CRSF_DN_LQ, downlinkLQ, inputData))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( ELRS_RF_MODE, rfMode, inputData ))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( DN_SNR, downlinkSNR, inputData ))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( UP_SNR, uplinkSNR, inputData ) )
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( ANT, activeAntenna, inputData ) )
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( POWER, uplinkTxPwr, inputData ))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( RSSI_DBM_1, uplinkRSSIAnt1, inputData ))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( RSSI_DBM_2, uplinkRSSIAnt2, inputData ))
                        dataDecoder.decodeData( Protocol.Companion.TelemetryData( RSSI_DBM_D, downlinkRSSI, inputData ))
                    }
                }
                FLIGHT_MODE.toByte() -> {
                    if (inputData.size >= MIN_FLIGHT_MODE_PACKET_LEN) {
                        val byteArray = ByteArray(255)
                        var pos = 0
                        do {
                            val byte = data.get()
                            byteArray[pos] = byte
                            pos++
                        } while ((byte != 0x00.toByte()) && (pos < inputData.size - 1))
                        if (byteArray[pos - 1] == 0x00.toByte()) {
                            dataDecoder.decodeData( Protocol.Companion.TelemetryData( FLYMODE, 0, byteArray ) )
                        }
                    }
                }
                ATTITUDE_TYPE.toByte() -> {
                    if (inputData.size == ATTITUDE_PACKET_LEN) {
                        val pitch = data.short
                        val roll = data.short
                        val yaw = data.short
                        dataDecoder.decodeData(Protocol.Companion.TelemetryData(PITCH, pitch.toInt()))
                        dataDecoder.decodeData(Protocol.Companion.TelemetryData(ROLL, roll.toInt()))
                        dataDecoder.decodeData(Protocol.Companion.TelemetryData(YAW, yaw.toInt()))
                    }
                }
                RC_CHANNELS_PACKED.toByte() -> {
                    if (inputData.size == RC_CHANNELS_PACKED_PACKET_LEN) {
                        var bitsMerged = 0
                        var readValue: UInt = 0U;
                        var ch = 0;

                        for (n in 0 until 16) {
                            while (bitsMerged < 11) {
                                val readByte = data.get().toUByte();
                                readValue = readValue or (readByte.toUInt() shl bitsMerged)
                                bitsMerged += 8
                            }
                            dataDecoder.decodeData(Protocol.Companion.TelemetryData(RC_CHANNEL_0 + ch, this.remapChannel((readValue and 0x7ffU).toInt())))
                            readValue = readValue shr 11;
                            bitsMerged -= 11;
                            ch++;
                        }
                    }
                }
            }
        }
    }
}