package crazydude.com.telemetry.maps.google

import com.google.android.gms.maps.model.Polyline
import crazydude.com.telemetry.maps.MapLine
import crazydude.com.telemetry.maps.Position

class GoogleLine(private val polyline: Polyline) : MapLine {


    override val size: Int
        get() = polyline.points.size

    override var color: Int
        get() = polyline.color
        set(value) {polyline.color = value}

    override fun remove() {
        polyline.remove()
    }

    override fun addPoints(points: List<Position>) {
        val googlePoints = polyline.points
        googlePoints.addAll(points.map { it.toLatLng() })
        polyline.points = googlePoints
    }

    override fun setPoint(index: Int, position: Position) {
        val points = polyline.points
        points[index] = position.toLatLng()
        polyline.points = points
    }

    override fun clear() {
        val points = polyline.points
        points.clear()
        polyline.points = points
    }

    override fun removeAt(index: Int) {
        val points = polyline.points
        points.removeAt(index)
        polyline.points = points
    }
}