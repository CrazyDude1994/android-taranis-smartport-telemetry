package crazydude.com.telemetry.maps.osm

import android.content.Context
import android.os.Bundle
import android.preference.PreferenceManager
import crazydude.com.telemetry.maps.MapLine
import crazydude.com.telemetry.maps.MapMarker
import crazydude.com.telemetry.maps.MapWrapper
import crazydude.com.telemetry.maps.Position
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay


class OsmMapWrapper(private val context: Context, private val mapView: MapView, private val callback: () -> Unit) : MapWrapper {

    private val myLocationNewOverlay = MyLocationNewOverlay(mapView)

    init {
        Configuration.getInstance().load(
            context, PreferenceManager.getDefaultSharedPreferences(
                context
            )
        )
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mapView.setMultiTouchControls(true)
        mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        mapView.overlayManager.add(myLocationNewOverlay)
        val mapController: IMapController = mapView.controller
        mapController.setZoom(4.toDouble())
        callback()
    }

    override fun initialized() : Boolean {
        return true;
    }

    override var mapType: Int
        get() = 0
        set(value) {}
    override var isMyLocationEnabled: Boolean
        get() = myLocationNewOverlay.isMyLocationEnabled
        set(value) {
            if (value) {
                myLocationNewOverlay.enableMyLocation()
            } else {
                myLocationNewOverlay.disableMyLocation()
            }
        }

    override fun moveCamera(position: Position) {
        mapView.controller.setCenter(position.toGeoPoint())
    }

    override fun moveCamera(position: Position, zoom: Float) {
        mapView.controller.setZoom(zoom.toDouble())  //set zoom first, center second
        mapView.controller.setCenter(position.toGeoPoint())
    }

    override fun addMarker(icon: Int, color: Int, position: Position): MapMarker {
        return OsmMarker(icon, color, position, mapView, context)
    }

    override fun addPolyline(width: Float, color: Int, vararg points: Position): MapLine {
        val osmLine = OsmLine(mapView)
        osmLine.addPoints(points.toList())
        osmLine.color = color;
        return osmLine
    }

    override fun setOnCameraMoveStartedListener(function: () -> Unit) {
        mapView.setOnTouchListener { v, event ->
            function()
            return@setOnTouchListener false
        }
    }

    override fun addPolyline(color: Int): MapLine {
        val res = OsmLine(mapView)
        res.color = color;
        return res;
    }

    override fun onCreate(bundle: Bundle?) {
    }

    override fun onResume() {
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
    }

    override fun onLowMemory() {
    }

    override fun onStart() {
    }

    override fun onStop() {
    }

    override fun onDestroy() {
    }

    override fun onSaveInstanceState(outState: Bundle?) {
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
    }
}