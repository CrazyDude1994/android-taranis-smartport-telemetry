package crazydude.com.telemetry.maps

interface MapWrapper {
    var mapType: Int
    var isMyLocationEnabled: Boolean

    fun moveCamera(position: Position)
    fun moveCamera(position: Position, zoom: Float)
    fun addMarker(icon: Int, color: Int, position: Position): MapMarker
    fun addPolyline(width: Float, color: Int, vararg points: Position): MapLine
    fun setOnCameraMoveStartedListener(function: () -> Unit)
    fun addPolyline(color: Int): MapLine
}