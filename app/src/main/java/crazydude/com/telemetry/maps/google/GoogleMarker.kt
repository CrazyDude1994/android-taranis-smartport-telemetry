package crazydude.com.telemetry.maps.google

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import crazydude.com.telemetry.maps.MapMarker
import crazydude.com.telemetry.maps.Position

class GoogleMarker(private val marker: Marker, private val context: Context) : MapMarker {

    override var rotation: Float
        get() = marker.rotation
        set(value) {marker.rotation = value}
    override var position: Position
        get() = Position(marker.position.latitude, marker.position.longitude)
        set(value) {marker.position = value.toLatLng()}

    override fun setIcon(icon: Int, color: Int) {
        marker.setIcon(bitmapDescriptorFromVector(context, icon, color))
    }

    override fun remove() {
        marker.remove()
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