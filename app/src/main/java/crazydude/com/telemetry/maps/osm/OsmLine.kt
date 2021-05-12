package crazydude.com.telemetry.maps.osm

import crazydude.com.telemetry.maps.MapLine
import crazydude.com.telemetry.maps.Position
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

class OsmLine(private val mapView: MapView) : MapLine {

    private val line = Polyline(mapView)

    init {
        mapView.overlayManager.add(line)
    }

    override fun remove() {
        mapView.overlayManager.remove(line)
    }

    override fun addPoints(points: List<Position>) {
        points.forEach { line.addPoint(it.toGeoPoint()) }
    }

    override fun setPoint(index: Int, position: Position) {
        val actualPoints = ArrayList(line.actualPoints)
        actualPoints[index] = position.toGeoPoint()
        line.setPoints(actualPoints)
    }

    override fun clear() {
        line.actualPoints.clear()
        mapView.invalidate()
    }

    override fun removeAt(index: Int) {
        line.actualPoints.removeAt(index)
        mapView.invalidate()
    }

    override fun setPoints(points: List<Position>) {
        line.setPoints(points.map { it.toGeoPoint() })
    }

    override val size: Int
        get() = line.actualPoints.size
    override var color: Int
        get() = line.color
        set(value) {line.color = value}
}