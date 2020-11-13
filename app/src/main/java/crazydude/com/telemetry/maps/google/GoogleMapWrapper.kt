package crazydude.com.telemetry.maps.google

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import crazydude.com.telemetry.R
import crazydude.com.telemetry.maps.MapLine
import crazydude.com.telemetry.maps.MapMarker
import crazydude.com.telemetry.maps.MapWrapper
import crazydude.com.telemetry.maps.Position

class GoogleMapWrapper(val context: Context, val googleMap: GoogleMap) : MapWrapper {

    private val markers = HashSet<MapMarker>()
    private val lines = HashSet<MapLine>()

    override var mapType: Int
        get() = googleMap.mapType
        set(value) {googleMap.mapType = value}
    override var isMyLocationEnabled: Boolean
        get() = googleMap.isMyLocationEnabled
        @SuppressLint("MissingPermission")
        set(value) {googleMap.isMyLocationEnabled = value}

    override fun moveCamera(position: Position) {
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(position.toLatLng()))
    }

    override fun moveCamera(position: Position, zoom: Float) {
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position.toLatLng(), zoom))
    }

    override fun addMarker(icon: Int, color: Int, position: Position): MapMarker {
        val googleMarker = googleMap.addMarker(
            MarkerOptions().icon(
                bitmapDescriptorFromVector(
                    context,
                    R.drawable.ic_plane, color
                )
            ).position(position.toLatLng())
        )
        val marker = GoogleMarker(googleMarker, context)
        markers.add(marker)
        return marker
    }

    override fun addPolyline(width: Float, color: Int, vararg points: Position): MapLine {
        val options = PolylineOptions().color(color).width(width)
        points.forEach { options.add(it.toLatLng()) }

        val mapLine = GoogleLine(googleMap.addPolyline(options))

        lines.add(mapLine)

        return mapLine
    }

    override fun addPolyline(color: Int): MapLine {
        val options = PolylineOptions().color(color)

        val mapLine = GoogleLine(googleMap.addPolyline(options))

        lines.add(mapLine)

        return mapLine
    }

    override fun setOnCameraMoveStartedListener(callback: () -> Unit) {
        googleMap.setOnCameraMoveStartedListener {
            if (it == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                callback()
            }
        }
    }

    private fun bitmapDescriptorFromVector(
        context: Context, @DrawableRes vectorDrawableResourceId: Int,
        color: Int? = null
    ): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorDrawableResourceId)
        color?.let {
            DrawableCompat.setTint(vectorDrawable!!, it)
        }
        vectorDrawable!!.setBounds(
            0,
            0,
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight
        )
        val bitmap =
            Bitmap.createBitmap(
                vectorDrawable.intrinsicWidth,
                vectorDrawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}