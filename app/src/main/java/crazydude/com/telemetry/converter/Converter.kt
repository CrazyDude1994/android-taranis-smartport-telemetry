package crazydude.com.telemetry.converter

interface Converter {

    fun convert(data: Double) : Double
    fun convert(data: Float) : Float
}