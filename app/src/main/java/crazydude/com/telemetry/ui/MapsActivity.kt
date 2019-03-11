package crazydude.com.telemetry.ui

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.support.annotation.DrawableRes
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import crazydude.com.telemetry.R
import crazydude.com.telemetry.manager.PreferenceManager
import crazydude.com.telemetry.protocol.DataPoller
import crazydude.com.telemetry.protocol.LogPlayer
import crazydude.com.telemetry.service.DataService
import java.io.File
import kotlin.math.roundToInt


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, DataPoller.Listener {

    companion object {
        private const val REQUEST_ENABLE_BT: Int = 0
        private const val REQUEST_LOCATION_PERMISSION: Int = 1
        private const val REQUEST_WRITE_PERMISSION: Int = 2
        private const val REQUEST_READ_PERMISSION: Int = 3
        private val MAP_TYPE_ITEMS = arrayOf("Road Map", "Satellite", "Terrain", "Hybrid")
    }

    private lateinit var map: GoogleMap
    private var marker: Marker? = null

    private lateinit var connectButton: Button
    private lateinit var replayButton: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var fuel: TextView
    private lateinit var satellites: TextView
    private lateinit var current: TextView
    private lateinit var voltage: TextView
    private lateinit var speed: TextView
    private lateinit var distance: TextView
    private lateinit var altitude: TextView
    private lateinit var mode: TextView
    private lateinit var followButton: FloatingActionButton
    private lateinit var mapTypeButton: FloatingActionButton
    private lateinit var settingsButton: ImageView
    private lateinit var topLayout: RelativeLayout
    private lateinit var preferenceManager: PreferenceManager
    private var mapType = GoogleMap.MAP_TYPE_NORMAL

    private var lastGPS = LatLng(0.0, 0.0)
    private var followMode = true
    private lateinit var polyLine: Polyline
    private var hasGPSFix = false
    private var dataService: DataService? = null

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(p0: ComponentName?) {
            onDisconnected()
        }

        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            dataService = (p1 as DataService.DataBinder).getService()
            dataService?.setDataListener(this@MapsActivity)
            dataService?.let {
                if (it.isConnected()) {
                    switchToConnectedState()
                    val points = polyLine.points
                    points.addAll(it.points)
                    polyLine.points = points
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        fuel = findViewById(R.id.fuel)
        satellites = findViewById(R.id.satellites)
        topLayout = findViewById(R.id.top_layout)
        connectButton = findViewById(R.id.connect_button)
        current = findViewById(R.id.current)
        voltage = findViewById(R.id.voltage)
        speed = findViewById(R.id.speed)
        distance = findViewById(R.id.distance)
        altitude = findViewById(R.id.altitude)
        mode = findViewById(R.id.mode)
        followButton = findViewById(R.id.follow_button)
        mapTypeButton = findViewById(R.id.map_type_button)
        settingsButton = findViewById(R.id.settings_button)
        replayButton = findViewById(R.id.replay_button)
        seekBar = findViewById(R.id.seekbar)

        replayButton.setOnClickListener {
            replay()
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        preferenceManager = PreferenceManager(this)

        followButton.setOnClickListener {
            followMode = true
        }

        mapTypeButton.setOnClickListener {
            showMapTypeSelectorDialog()
        }

        switchToDisconnectedState()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        startDataService()
    }

    private fun replay() {
        if (dataService?.isConnected() != true) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_DENIED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_READ_PERMISSION
                )
            } else {
                val dir = Environment.getExternalStoragePublicDirectory("TelemetryLogs")
                if (dir.exists()) {
                    val files = dir.listFiles { file -> file.extension == "log" && file.length() > 0 }
                    AlertDialog.Builder(this)
                        .setAdapter(
                            ArrayAdapter<String>(
                                this,
                                android.R.layout.simple_list_item_1,
                                files.map { i -> i.nameWithoutExtension })
                        ) { _, i ->
                            startReplay(files[i])
                        }
                        .show()
                }
            }
        } else {
            Toast.makeText(this, "You need to disconnect first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startReplay(file: File?) {
        file?.also {
            val progressDialog = ProgressDialog(this)
            progressDialog.setCancelable(false)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            progressDialog.max = 100
            progressDialog.show()

            val logPlayer = LogPlayer(it, object: LogPlayer.DataReadyListener {
                override fun onUpdate(percent: Int) {
                    progressDialog.progress = percent
                }

                override fun onDataReady(size: Int) {
                    progressDialog.hide()
                    seekBar.max = size
                    seekBar.visibility = View.VISIBLE
                    seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(p0: SeekBar?, position: Int, p2: Boolean) {
                        }

                        override fun onStartTrackingTouch(p0: SeekBar?) {
                        }

                        override fun onStopTrackingTouch(p0: SeekBar?) {

                        }
                    })
                }
            })
        }
    }

    override fun onFlyModeData(
        armed: Boolean,
        heading: Boolean,
        firstFlightMode: DataPoller.Companion.FlyMode,
        secondFlightMode: DataPoller.Companion.FlyMode?
    ) {
        if (armed) {
            mode.text = "Armed"
        } else {
            mode.text = "Disarmed"
        }

        if (heading) {
            mode.text = mode.text.toString() + " | Heading"
        }

        if (secondFlightMode == null) {
            when (firstFlightMode) {
                DataPoller.Companion.FlyMode.ACRO -> {
                    mode.text = mode.text.toString() + " | Acro"
                }
                DataPoller.Companion.FlyMode.HORIZON -> {
                    mode.text = mode.text.toString() + " | Horizon"
                }
                DataPoller.Companion.FlyMode.ANGLE -> {
                    mode.text = mode.text.toString() + " | Angle"
                }
            }
        } else {
            when (secondFlightMode) {
                DataPoller.Companion.FlyMode.FAILSAFE -> {
                    mode.text = mode.text.toString() + " | Failsafe"
                }
                DataPoller.Companion.FlyMode.RTH -> {
                    mode.text = mode.text.toString() + " | RTH"
                }
                DataPoller.Companion.FlyMode.WAYPOINT -> {
                    mode.text = mode.text.toString() + " | Waypoint"
                }
                DataPoller.Companion.FlyMode.MANUAL -> {
                    mode.text = mode.text.toString() + " | Manual"
                }
                DataPoller.Companion.FlyMode.CRUISE -> {
                    mode.text = mode.text.toString() + " | Cruise"
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putInt("map_type", mapType)
        outState?.putBoolean("follow_mode", followMode)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        mapType = savedInstanceState?.getInt("map_type") ?: GoogleMap.MAP_TYPE_NORMAL
        followMode = savedInstanceState?.getBoolean("follow_mode", true) ?: true
    }

    private fun connect() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
        if (preferenceManager.isLoggingEnabled()) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_DENIED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_PERMISSION
                )
                return
            }
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
        startDataService()
        dataService?.let {
            connectButton.text = getString(R.string.connecting)
            connectButton.isEnabled = false
            it.connect(device)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            dataService?.setDataListener(null)
        }
        unbindService(serviceConnection)
    }

    private fun startDataService() {
        val intent = Intent(this, DataService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        startService(intent)
        bindService(intent, serviceConnection, 0)
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty()) {
            if (requestCode == REQUEST_LOCATION_PERMISSION) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    map.isMyLocationEnabled = true
                }
            } else if (requestCode == REQUEST_WRITE_PERMISSION) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    connect()
                } else {
                    AlertDialog.Builder(this)
                        .setMessage("Write permission is required in order to log telemetry data. Disable logging or grant permission to continue")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } else if (requestCode == REQUEST_READ_PERMISSION) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    replay()
                } else {
                    AlertDialog.Builder(this)
                        .setMessage("Read permission is required in order to read and replay telemetry data")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    override fun onVSpeedData(vspeed: Float) {

    }

    override fun onAltitudeData(altitude: Float) {
        this.altitude.text = "$altitude m"
    }

    override fun onGPSAltitudeData(altitude: Float) {

    }

    override fun onDistanceData(distance: Int) {
        this.distance.text = "$distance m"
    }

    override fun onRollData(rollAngle: Float) {

    }

    override fun onPitchData(pitchAngle: Float) {

    }

    override fun onGSpeedData(speed: Float) {
        this.speed.text = "${speed.roundToInt()} km/h"
    }

    override fun onGPSState(satellites: Int, gpsFix: Boolean) {
        this.hasGPSFix = gpsFix
        if (gpsFix && marker == null) {
            marker = map.addMarker(
                MarkerOptions().icon(
                    bitmapDescriptorFromVector(
                        this,
                        R.drawable.ic_plane
                    )
                ).position(lastGPS)
            )
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(lastGPS, 15f))
        }
        this.satellites.text = satellites.toString()
    }

    override fun onRSSIData(rssi: Int) {

    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.mapType = mapType
        if (checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_LOCATION_PERMISSION
            )
            map.isMyLocationEnabled = false
        }
        topLayout.measure(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        map.setPadding(0, topLayout.measuredHeight, 0, 0)
        val polylineOptions = PolylineOptions()
        polyLine = map.addPolyline(polylineOptions)
        map.setOnCameraMoveStartedListener {
            if (it == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                followMode = false
            }
        }
    }

    private fun showMapTypeSelectorDialog() {
        val fDialogTitle = "Select Map Type"
        val builder = AlertDialog.Builder(this)
        builder.setTitle(fDialogTitle)

        val checkItem = map.mapType - 1

        builder.setSingleChoiceItems(
            MAP_TYPE_ITEMS,
            checkItem
        ) { dialog, item ->
            when (item) {
                1 -> map.mapType = GoogleMap.MAP_TYPE_SATELLITE
                2 -> map.mapType = GoogleMap.MAP_TYPE_TERRAIN
                3 -> map.mapType = GoogleMap.MAP_TYPE_HYBRID
                else -> map.mapType = GoogleMap.MAP_TYPE_NORMAL
            }
            mapType = map.mapType
            dialog.dismiss()
        }

        val fMapTypeDialog = builder.create()
        fMapTypeDialog.setCanceledOnTouchOutside(true)
        fMapTypeDialog.show()
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
        switchToDisconnectedState()
    }

    private fun switchToDisconnectedState() {
        connectButton.text = getString(R.string.connect)
        connectButton.isEnabled = true
        connectButton.setOnClickListener {
            connect()
        }
    }

    private fun switchToConnectedState() {
        connectButton.text = getString(R.string.disconnect)
        connectButton.isEnabled = true
        connectButton.setOnClickListener {
            connectButton.isEnabled = false
            connectButton.text = getString(R.string.disconnecting)
            dataService?.disconnect()
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
        when (fuel) {
            in 91..100 -> R.drawable.ic_battery_full
            in 81..90 -> R.drawable.ic_battery_90
            in 61..80 -> R.drawable.ic_battery_80
            in 51..60 -> R.drawable.ic_battery_60
            in 31..50 -> R.drawable.ic_battery_50
            in 21..30 -> R.drawable.ic_battery_30
            in 0..20 -> R.drawable.ic_battery_alert
            else -> R.drawable.ic_battery_unknown
        }.let { this.fuel.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(this, it), null, null, null) }
        this.fuel.text = "$fuel%"
    }

    override fun onGPSData(latitude: Double, longitude: Double) {
        if (LatLng(latitude, longitude) != lastGPS) {
            lastGPS = LatLng(latitude, longitude)
            marker?.let { it.position = lastGPS }
            if (followMode) {
                map.moveCamera(CameraUpdateFactory.newLatLng(lastGPS))
            }
            if (hasGPSFix) {
                val points = polyLine.points
                points.add(lastGPS)
                polyLine.points = points
            }
        }
    }

    override fun onConnected() {
        Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
        switchToConnectedState()
    }
}
