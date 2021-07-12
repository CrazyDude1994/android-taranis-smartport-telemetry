package crazydude.com.telemetry.protocol

import crazydude.com.telemetry.maps.Position
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.util.concurrent.CopyOnWriteArrayList

data class TelemetryModel(
    var fuel: Int = 0,
    var position: MutableList<Position> = CopyOnWriteArrayList(),
    var vbat: Float = 0f,
    var cellVoltage: Float = 0f,
    var current: Float = 0f,
    var heading: Float = 0f,
    var satelliteCount: Int = 0,
    var gpsFix: Boolean = false,
    var vspeed: Float = 0f,
    var altitude: Float = 0f,
    var gpsAltitude: Float = 0f,
    var distance: Float = 0f,
    var roll: Float = 0f,
    var pitch: Float = 0f,
    var gpsSpeed: Float = 0f,
    var armed: Boolean = false,
    var isHeadingMode: Boolean = false,
    var flightMode1: DataDecoder.Companion.FlyMode? = null,
    var flightMode2: DataDecoder.Companion.FlyMode? = null,
    var airSpeed: Float = 0f,
    var rssi: Int = -1) {

    fun decodeCurrentModes() : String {
        var mode = if (armed) "Armed" else "Disarmed"
        if (isHeadingMode) {
            mode += " | Heading"
        }
        flightMode1?.let { mode += " | ${it.modeName}"  }
        flightMode2?.let { mode += " | ${it.modeName}"  }
        return mode
    }

    fun formatVoltage() : String {
        return if (cellVoltage > 0)
            "${"%.2f".format(vbat)} (${"%.2f".format(cellVoltage)}) V"
        else
            "${"%.2f".format(vbat)} V"
    }
}