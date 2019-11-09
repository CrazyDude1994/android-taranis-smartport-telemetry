package crazydude.com.telemetry.protocol

import com.google.android.gms.maps.model.LatLng
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import org.junit.Test

class SportProtocolTest {

    @Test
    fun decodeTest() {
        val protocol =
            FrSkySportProtocol(object : DataDecoder(Companion.DefaultDecodeListener()) {
                override fun decodeData(data: Protocol.Companion.TelemetryData) {

                }
            })
    }
}