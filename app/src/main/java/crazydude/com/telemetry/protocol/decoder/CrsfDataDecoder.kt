package crazydude.com.telemetry.protocol.decoder

import crazydude.com.telemetry.protocol.Protocol

class CrsfDataDecoder(listener: Listener) : DataDecoder(listener) {

    override fun decodeData(data: Protocol.Companion.TelemetryData) {
        data.telemetryType == Protocol.GPS_LONGITUDE
    }
}