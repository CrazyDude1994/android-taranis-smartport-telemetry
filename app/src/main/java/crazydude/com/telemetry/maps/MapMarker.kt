package crazydude.com.telemetry.maps

interface MapMarker {
    var rotation: Float
    var position: Position
    fun setIcon(icon: Int, color: Int)
    fun remove()
}