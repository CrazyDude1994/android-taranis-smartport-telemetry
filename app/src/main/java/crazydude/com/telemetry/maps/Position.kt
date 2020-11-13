package crazydude.com.telemetry.maps

import com.google.android.gms.maps.model.LatLng

data class Position(var lat: Double, var lon: Double) {

    fun toLatLng() : LatLng {
        return LatLng(lat, lon)
    }
}