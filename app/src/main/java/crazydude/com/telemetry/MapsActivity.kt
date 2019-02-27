package crazydude.com.telemetry

import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, DataPoller.Listener {

    companion object {
        const val EXTRA_DEVICE_NAME = "extra_device_name"
    }

    private lateinit var map: GoogleMap
    private lateinit var dataPoller: DataPoller

    private lateinit var marker: Marker

    private lateinit var fuel: TextView
    private lateinit var topLayout: RelativeLayout
    private var lastGPS = LatLng(0.0, 0.0)
    private lateinit var polyLine : Polyline

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fuel = findViewById(R.id.fuel)
        topLayout = findViewById(R.id.top_layout)

        val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
        for (bondedDevice in BluetoothAdapter.getDefaultAdapter().bondedDevices) {
            if (bondedDevice.name == deviceName) {
                val socket =
                    bondedDevice.createRfcommSocketToServiceRecord(bondedDevice.uuids[0].uuid)
                dataPoller = DataPoller(socket, this)
                break
            }
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.isMyLocationEnabled = true
        map.setPadding(0, topLayout.height, 0, 0)
        marker = map.addMarker(MarkerOptions().position(LatLng(0.0, 0.0)))
        polyLine = map.addPolyline(PolylineOptions())
    }

    override fun onDestroy() {
        super.onDestroy()
        dataPoller.disconnect()
    }

    override fun onConnectionFailed() {
        Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onFuelData(fuel: Int) {
        this.fuel.text = "Fuel: $fuel"
    }

    override fun onGPSData(latitude: Double, longitude: Double) {
        if (LatLng(latitude, longitude) != lastGPS) {
            lastGPS = LatLng(latitude, longitude)
            marker.position = lastGPS
            val points = polyLine.points
            points.add(lastGPS)
            polyLine.points = points
        }
    }

    override fun onConnected() {
        Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
    }
}
