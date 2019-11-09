package crazydude.com.telemetry.protocol

import crazydude.com.telemetry.protocol.Protocol.Companion.TelemetryData
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import org.junit.Assert.*
import org.junit.Test

class LTMProtocolTest {

    @Test
    fun testLTMProtocol() {

        val expectedTelemetry = arrayListOf(
            TelemetryData(Protocol.GPS, 0, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            TelemetryData(Protocol.GPS, 0, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)))
        val decodedTelemetry = ArrayList<TelemetryData>()
        val ltmProtocol = LTMProtocol(object : DataDecoder(Companion.DefaultDecodeListener()) {
            override fun decodeData(data: TelemetryData) {
                decodedTelemetry.add(data)
            }
        })

        val inputStream = this.javaClass.classLoader.getResourceAsStream("ltm.log")
        assertNotNull(inputStream)
        do {
            val data = inputStream.read()
            ltmProtocol.process(data.toUByte().toInt())
        } while (data != -1)

        assertEquals(expectedTelemetry.size, decodedTelemetry.size)
        assertArrayEquals(expectedTelemetry.toArray(), decodedTelemetry.toArray())
    }
}