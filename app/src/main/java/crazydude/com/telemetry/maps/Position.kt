package crazydude.com.telemetry.maps

import com.google.android.gms.maps.model.LatLng
import org.osmdroid.api.IGeoPoint
import org.osmdroid.util.GeoPoint

data class Position(var lat: Double, var lon: Double) {

    fun toLatLng() : LatLng {
        return LatLng(lat, lon)
    }

    fun toGeoPoint(): GeoPoint {
        return GeoPoint(lat, lon)
    }
}