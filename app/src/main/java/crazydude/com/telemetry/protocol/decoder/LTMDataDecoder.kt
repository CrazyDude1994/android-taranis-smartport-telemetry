package crazydude.com.telemetry.protocol.decoder

import android.util.Log
import crazydude.com.telemetry.protocol.Protocol
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LTMDataDecoder(listener: Listener) : DataDecoder(listener) {


    override fun decodeData(data: Protocol.Companion.TelemetryData) {
        var decoded = true
        val byteBuffer = ByteBuffer.wrap(data.rawData).order(ByteOrder.LITTLE_ENDIAN)
        when (data.telemetryType) {
            Protocol.GPS -> {
                val latitude = byteBuffer.int / 10000000.toDouble()
                val longitude = byteBuffer.int / 10000000.toDouble()
                Log.d("LTMDecoder", "GPS: $latitude, $longitude")
                val speed = byteBuffer.get()
                val altitude = byteBuffer.int
                val gpsState = byteBuffer.get()
                listener.onGPSState(((gpsState.toUInt() shr 2) and 0xFF.toUInt()).toInt(), ((gpsState.toUInt() shr 0) and 1.toUInt()) == 1.toUInt())
                listener.onGPSData(latitude, longitude)
                listener.onGSpeedData(speed.toUByte().toByte() * (18 / 5f))
                listener.onAltitudeData(altitude / 100f)
            }

            Protocol.ATTITUDE -> {
                val pitch = byteBuffer.short
                val roll = byteBuffer.short
                val heading = byteBuffer.short
                listener.onRollData(roll.toFloat())
                listener.onPitchData(pitch.toFloat())
                listener.onHeadingData(heading.toFloat())
            }

            Protocol.FLYMODE -> {
                val status = byteBuffer.get()
                listener.onFlyModeData(((status.toUInt() shr 0) and 1.toUInt()) == 1.toUInt(), false, null, null)
            }

            Protocol.FUEL -> {
                val fuel = byteBuffer.short
                listener.onFuelData(fuel.toInt())
            }

            Protocol.VBAT -> {
                val battery = byteBuffer.short / 1000f
                listener.onVBATData(battery)
            }

            else -> {
                decoded = false
            }
        }

        if (decoded) {
            listener.onSuccessDecode()
        }
    }
}