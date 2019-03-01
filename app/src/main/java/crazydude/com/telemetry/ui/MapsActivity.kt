package crazydude.com.telemetry.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.widget.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import crazydude.com.telemetry.DataPoller
import crazydude.com.telemetry.R


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, DataPoller.Listener {

    companion object {
        private const val REQUEST_ENABLE_BT: Int = 0
        private const val REQUEST_LOCATION_PERMISSION: Int = 1
    }


    private lateinit var map: GoogleMap
    private lateinit var connectButton: Button
    private lateinit var dataPoller: DataPoller

    private var marker: Marker? = null

    private lateinit var fuel: TextView
    private lateinit var satellites: TextView
    private lateinit var current: TextView
    private lateinit var voltage: TextView
    private lateinit var topLayout: RelativeLayout

    private var lastGPS = LatLng(0.0, 0.0)
    private lateinit var polyLine: Polyline
    private var hasGPSFix = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        fuel = findViewById(R.id.fuel)
        satellites = findViewById(R.id.satellites)
        topLayout = findViewById(R.id.top_layout)
        connectButton = findViewById(R.id.connect_button)
        current = findViewById(R.id.current)
        voltage = findViewById(R.id.voltage)

        if (checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
            return
        }

        connectButton.setOnClickListener {
            connect()
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun connect() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
        val devices = BluetoothAdapter.getDefaultAdapter().bondedDevices.toList()
        AlertDialog.Builder(this).setAdapter(
            ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                devices.map { it.name })
        ) { _, i ->
            connectToDevice(devices[i])
        }.show()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        connectButton.text = getString(R.string.connecting)
        connectButton.isEnabled = false
        val socket = device.createRfcommSocketToServiceRecord(device.uuids[0].uuid)
        dataPoller = DataPoller(socket, this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            }
        }
    }

    override fun onGPSState(satellites: Int, gpsFix: Boolean) {
        this.hasGPSFix = gpsFix
        if (gpsFix && marker == null) {
            marker = map.addMarker(MarkerOptions().icon(bitmapDescriptorFromVector(this,
                R.drawable.ic_plane
            )).position(lastGPS))
        }
        this.satellites.text = satellites.toString()
    }

    override fun onRSSIData(rssi: Int) {

    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.isMyLocationEnabled = true
        topLayout.measure(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        map.setPadding(0, topLayout.measuredHeight, 0, 0)
        polyLine = map.addPolyline(PolylineOptions())
    }

    private fun bitmapDescriptorFromVector(context: Context, @DrawableRes vectorDrawableResourceId: Int): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorDrawableResourceId)
        vectorDrawable!!.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap =
            Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onDestroy() {
        super.onDestroy()
        dataPoller.disconnect()
    }

    override fun onVBATData(voltage: Float) {

    }

    override fun onCurrentData(current: Float) {
        this.current.text = "$current A"
    }

    override fun onHeadingData(heading: Float) {
        marker?.let { it.rotation = heading }
    }

    override fun onCellVoltageData(voltage: Float) {
        this.voltage.text = "$voltage V"
    }

    override fun onDisconnected() {
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
        connectButton.text = getString(R.string.connect)
        connectButton.isEnabled = true
        connectButton.setOnClickListener {
            connect()
        }
    }

    override fun onConnectionFailed() {
        Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
        connectButton.text = getString(R.string.connect)
        connectButton.isEnabled = true
        connectButton.setOnClickListener {
            connect()
        }
    }

    override fun onFuelData(fuel: Int) {
        when(fuel) {
            in 91..100 -> R.drawable.ic_battery_full
            in 81..90 -> R.drawable.ic_battery_90
            in 61..80 -> R.drawable.ic_battery_80
            in 51..60 -> R.drawable.ic_battery_60
            in 31..50 -> R.drawable.ic_battery_50
            in 21..30 -> R.drawable.ic_battery_30
            in 0..20 -> R.drawable.ic_battery_alert
            else -> R.drawable.ic_battery_unknown
        }.let { this.fuel.setCompoundDrawablesWithIntrinsicBounds(getDrawable(it), null, null, null) }
        this.fuel.text = "$fuel%"
    }

    override fun onGPSData(latitude: Double, longitude: Double) {
        if (LatLng(latitude, longitude) != lastGPS) {
            lastGPS = LatLng(latitude, longitude)
            marker?.let { it.position = lastGPS }
            if (hasGPSFix) {
                val points = polyLine.points
                points.add(lastGPS)
                polyLine.points = points
            }
        }
    }

    override fun onConnected() {
        Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
        connectButton.text = getString(R.string.disconnect)
        connectButton.isEnabled = true
        connectButton.setOnClickListener {
            dataPoller.disconnect()
            connectButton.isEnabled = false
            connectButton.text = getString(R.string.disconnecting)
        }
    }
}
