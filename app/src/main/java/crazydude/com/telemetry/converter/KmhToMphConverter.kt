package crazydude.com.telemetry.converter

class KmhToMphConverter: Converter {

    override fun convert(data: Double): Double = data * 0.621371
    override fun convert(data: Float): Float = data * 0.621371f
}