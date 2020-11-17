package crazydude.com.telemetry.maps.osm

import android.content.Context
import androidx.core.graphics.drawable.DrawableCompat
import crazydude.com.telemetry.maps.MapMarker
import crazydude.com.telemetry.maps.Position
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class OsmMarker(icon: Int, color: Int, position: Position, private val mapView: MapView, private val context: Context) : MapMarker {

    private val marker = Marker(mapView)

    init {
        marker.icon = context.resources.getDrawable(icon).also {
            DrawableCompat.setTint(it, color)
        }
        marker.position = position.toGeoPoint()
        mapView.overlayManager.add(marker)
    }

    override var rotation: Float
        get() = marker.rotation
        set(value) {marker.rotation = -value}
    override var position: Position
        get() = Position(marker.position.latitude, marker.position.longitude)
        set(value) {marker.position = value.toGeoPoint()}

    override fun setIcon(icon: Int, color: Int) {
        marker.icon = context.resources.getDrawable(icon).also {
            DrawableCompat.setTint(it, color)
        }
    }

    override fun remove() {
        marker.remove(mapView)
    }
}