package crazydude.com.telemetry.maps

import android.os.Bundle

interface MapWrapper {
    var mapType: Int
    var isMyLocationEnabled: Boolean

    fun initialized() : Boolean

    fun moveCamera(position: Position)
    fun moveCamera(position: Position, zoom: Float)
    fun addMarker(icon: Int, color: Int, position: Position): MapMarker
    fun addPolyline(width: Float, color: Int, vararg points: Position): MapLine
    fun setOnCameraMoveStartedListener(function: () -> Unit)
    fun addPolyline(color: Int): MapLine

    fun invalidate()

    fun onCreate(bundle: Bundle?)
    fun onResume()
    fun onPause()
    fun onLowMemory()
    fun onStart()
    fun onStop()
    fun onDestroy()
    fun onSaveInstanceState(outState: Bundle?)
    fun setPadding(left: Int, top: Int, right: Int, bottom: Int)
}