package crazydude.com.telemetry.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.*
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.maps.android.SphericalUtil
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import crazydude.com.telemetry.R
import crazydude.com.telemetry.manager.PreferenceManager
import crazydude.com.telemetry.protocol.LogPlayer
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import crazydude.com.telemetry.service.DataService
import java.io.File
import kotlin.math.roundToInt


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, DataDecoder.Listener {

    companion object {
        private const val REQUEST_ENABLE_BT: Int = 0
        private const val REQUEST_LOCATION_PERMISSION: Int = 1
        private const val REQUEST_WRITE_PERMISSION: Int = 2
        private const val REQUEST_READ_PERMISSION: Int = 3
        private const val ACTION_USB_DEVICE = "action_usb_device"
        private val MAP_TYPE_ITEMS = arrayOf("Road Map", "Satellite", "Terrain", "Hybrid")
    }

    private var map: GoogleMap? = null
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
    private lateinit var fullscreenButton: FloatingActionButton
    private lateinit var directionsButton: FloatingActionButton
    private lateinit var settingsButton: ImageView
    private lateinit var topLayout: RelativeLayout
    private lateinit var horizonView: HorizonView
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private var mapType = GoogleMap.MAP_TYPE_NORMAL

    private var lastGPS = LatLng(0.0, 0.0)
    private var lastHeading = 0f
    private var followMode = true
    private var polyLine: Polyline? = null
    private var headingPolyline: Polyline? = null
    private var hasGPSFix = false
    private var replayFileString: String? = null
    private var dataService: DataService? = null
    private var lastVBAT = 0f
    private var lastCellVoltage = 0f

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
                    val points = polyLine?.points
                    points?.addAll(it.points)
                    polyLine?.points = points
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        preferenceManager = PreferenceManager(this)

        mapType = preferenceManager.getMapType()
        followMode = savedInstanceState?.getBoolean("follow_mode", true) ?: true
        replayFileString = savedInstanceState?.getString("replay_file_name")

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
        horizonView = findViewById(R.id.horizon_view)
        fullscreenButton = findViewById(R.id.fullscreen_button)
        directionsButton = findViewById(R.id.directions_button)

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        fullscreenButton.setOnClickListener {
            if (window.decorView.systemUiVisibility == (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE)) {
                window.decorView.systemUiVisibility = 0
            } else {
                window.decorView.systemUiVisibility =
                    (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE)
            }
        }

        followButton.setOnClickListener {
            followMode = true
            marker?.let {
                map?.moveCamera(CameraUpdateFactory.newLatLng(it.position))
            }
        }

        mapTypeButton.setOnClickListener {
            showMapTypeSelectorDialog()
        }

        directionsButton.setOnClickListener {
            showDirectionsToCurrentLocation()
        }

        directionsButton.setOnLongClickListener {
            showAndCopyCurrentGPSLocation()
            true
        }

        if (isInReplayMode()) {
            startReplay(File(Environment.getExternalStoragePublicDirectory("TelemetryLogs"), replayFileString))
        } else {
            switchToIdleState()
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        startDataService()

        checkAppInstallDate()
    }

    private fun showAndCopyCurrentGPSLocation() {
        marker?.let {
            val posString = "${it.position.latitude},${it.position.longitude}"
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.primaryClip = ClipData.newPlainText("Location", posString)
            Toast.makeText(this, "Current plane location copied to clipboard ($posString)", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDirectionsToCurrentLocation() {
        marker?.let {
            val posString = "${it.position.latitude},${it.position.longitude}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://maps.google.com/maps?daddr=$posString"))
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "Cannot build directions", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkAppInstallDate() {
        val installTime = packageManager.getPackageInfo(packageName, 0).firstInstallTime
        val delta = System.currentTimeMillis() - installTime

        if (delta / 1000 / 60 / 60 / 24 > 3 && !preferenceManager.isYoutubeChannelShown()) {
            AlertDialog.Builder(this)
                .setTitle("Thanks for using my application")
                .setMessage(
                    "Thanks for using my application. As it's does not contain any ads and completely free, " +
                            "you can help me by subscribing to my youtube channel"
                )
                .setPositiveButton("Subscribe") { dialog: DialogInterface?, i: Int ->
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/channel/UCjAhODF0Achhc1fynxEXQLg?view_as=subscriber&sub_confirmation=1")
                        )
                    )
                }
                .setNegativeButton("Cancel", null)
                .setOnDismissListener { preferenceManager.setYoutubeShown() }
                .show()
        }
    }

    private fun isInReplayMode(): Boolean {
        return replayFileString != null
    }

    private fun isIdle(): Boolean {
        return !isInReplayMode() && !(dataService?.isConnected() ?: false)
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
                    val files = dir.listFiles { file -> file.extension == "log" && file.length() > 0 }.reversed()
                    AlertDialog.Builder(this)
                        .setAdapter(
                            ArrayAdapter(
                                this,
                                android.R.layout.simple_list_item_1,
                                files.map { i -> "${i.nameWithoutExtension} (${i.length() / 1024} Kb)" })
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

            switchToReplayMode()

            replayFileString = it.name

            val logPlayer = LogPlayer(this)
            logPlayer.load(file, object : LogPlayer.DataReadyListener {
                override fun onUpdate(percent: Int) {
                    progressDialog.progress = percent
                }

                override fun onDataReady(size: Int) {
                    progressDialog.hide()
                    seekBar.max = size
                    seekBar.visibility = View.VISIBLE
                    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekbar: SeekBar, position: Int, p2: Boolean) {
                            logPlayer.seek(position)
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
        firstFlightMode: DataDecoder.Companion.FlyMode?,
        secondFlightMode: DataDecoder.Companion.FlyMode?
    ) {
        runOnUiThread {
            if (armed) {
                mode.text = "Armed"
            } else {
                mode.text = "Disarmed"
            }

            if (heading) {
                mode.text = mode.text.toString() + " | Heading"
            }

            decodeMode(firstFlightMode)
            decodeMode(secondFlightMode)
        }
    }

    private fun decodeMode(flyMode: DataDecoder.Companion.FlyMode?) {
        when (flyMode) {
            DataDecoder.Companion.FlyMode.ACRO -> {
                mode.text = mode.text.toString() + " | Acro"
            }
            DataDecoder.Companion.FlyMode.HORIZON -> {
                mode.text = mode.text.toString() + " | Horizon"
            }
            DataDecoder.Companion.FlyMode.ANGLE -> {
                mode.text = mode.text.toString() + " | Angle"
            }
            DataDecoder.Companion.FlyMode.FAILSAFE -> {
                mode.text = mode.text.toString() + " | Failsafe"
            }
            DataDecoder.Companion.FlyMode.RTH -> {
                mode.text = mode.text.toString() + " | RTH"
            }
            DataDecoder.Companion.FlyMode.WAYPOINT -> {
                mode.text = mode.text.toString() + " | Waypoint"
            }
            DataDecoder.Companion.FlyMode.MANUAL -> {
                mode.text = mode.text.toString() + " | Manual"
            }
            DataDecoder.Companion.FlyMode.CRUISE -> {
                mode.text = mode.text.toString() + " | Cruise"
            }
            DataDecoder.Companion.FlyMode.HOLD -> {
                mode.text = mode.text.toString() + " | Hold"
            }
            DataDecoder.Companion.FlyMode.HOME_RESET -> {
                mode.text = mode.text.toString() + " | Home reset"
            }
            DataDecoder.Companion.FlyMode.CRUISE3D -> {
                mode.text = mode.text.toString() + " | 3D Cruise"
            }
            DataDecoder.Companion.FlyMode.ALTHOLD -> {
                mode.text = mode.text.toString() + " | Alt hold"
            }
            DataDecoder.Companion.FlyMode.ERROR -> {
                mode.text = mode.text.toString() + " | !ERROR!"
            }
            DataDecoder.Companion.FlyMode.WAIT -> {
                mode.text = mode.text.toString() + " | GPS wait"
            }
            null -> {}
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putBoolean("follow_mode", followMode)
        outState?.putString("replay_file_name", replayFileString)
    }

    override fun onStart() {
        super.onStart()
        polyLine?.let { it.color = preferenceManager.getRouteColor() }
        if (!isIdle()) {
            headingPolyline?.let { it.color = preferenceManager.getHeadLineColor() }
            if (preferenceManager.isHeadingLineEnabled() && headingPolyline == null) {
                headingPolyline = createHeadingPolyline()
                updateHeading()
            } else if (!preferenceManager.isHeadingLineEnabled() && headingPolyline != null) {
                headingPolyline?.remove()
                headingPolyline = null
            }
            marker?.setIcon(bitmapDescriptorFromVector(this, R.drawable.ic_plane, preferenceManager.getPlaneColor()))
        }
        if (preferenceManager.showArtificialHorizonView()) {
            horizonView.visibility = View.VISIBLE
        } else {
            horizonView.visibility = View.GONE
        }
    }

    private fun connect() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Bluetooth", "USB Serial")) { dialogInterface, i ->
                when(i) {
                    0 -> connectBluetooth()
                    1 -> connectUSB()
                }
            }
            .setTitle("Choose connection method")
            .show()
    }

    private fun connectUSB() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val driver = drivers.firstOrNull()
        if (driver == null) {
            Toast.makeText(this, "No valid usb driver has been found", Toast.LENGTH_SHORT).show()
        } else {
            val connection = usbManager.openDevice(driver.device)
            if (connection != null) {
                val port = driver.ports.firstOrNull()
                if (port == null) {
                    Toast.makeText(this, "No valid usb port has been found", Toast.LENGTH_SHORT).show()
                } else {
                    connectToUSBDevice(port, connection)
                }
            } else {
                val pendingIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_DEVICE), 0)
                registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (ACTION_USB_DEVICE == intent?.action) {
                            synchronized(this) {
                                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                    device?.apply {
                                        connectUSB()
                                    }
                                } else {
                                    Toast.makeText(this@MapsActivity, "You need to allow permission in order to connect with a usb", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        unregisterReceiver(this)
                    }
                }, IntentFilter(ACTION_USB_DEVICE))
                usbManager.requestPermission(driver.device, pendingIntent)
            }
        }
    }

    private fun connectBluetooth() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            AlertDialog.Builder(this)
                .setMessage("It seems like your phone does not have bluetooth, or it does not supported")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return
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

        val devices = ArrayList<BluetoothDevice>(adapter.bondedDevices)
        val deviceNames = ArrayList<String>(devices.map {
            var result = it.name
            if (result == null) {
                result = it.address
            }
            result
        }.filterNotNull())
        val deviceAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, deviceNames)

        val callback = BluetoothAdapter.LeScanCallback { bluetoothDevice, i, bytes ->
            if (!devices.contains(bluetoothDevice) && bluetoothDevice.name != null) {
                devices.add(bluetoothDevice)
                deviceNames.add(bluetoothDevice.name)
                deviceAdapter.notifyDataSetChanged()
            }
        }

        if (bleCheck()) {
            adapter.startLeScan(callback)
        }

        AlertDialog.Builder(this).setOnDismissListener {
            if (bleCheck()) {
                adapter.stopLeScan(callback)
            }
        }.setAdapter(deviceAdapter) { _, i ->
            if (bleCheck()) {
                adapter.stopLeScan(callback)
            }
            runOnUiThread {
                connectToBluetoothDevice(devices[i])
            }
        }.show()
    }

    private fun resetUI() {
        satellites.text = "0"
        voltage.text = "-"
        current.text = "-"
        fuel.text = "-"
        altitude.text = "-"
        speed.text = "-"
        distance.text = "-"
        mode.text = "Disconnected"
        horizonView.setPitch(0f)
        horizonView.setRoll(0f)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            this.fuel.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(this, R.drawable.ic_battery_unknown),
                null,
                null,
                null
            )
        } else {
            this.fuel.setCompoundDrawablesWithIntrinsicBounds(
                null,
                ContextCompat.getDrawable(this, R.drawable.ic_battery_unknown),
                null,
                null
            )
        }
    }

    private fun bleCheck() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED


    private fun connectToBluetoothDevice(device: BluetoothDevice) {
        startDataService()
        dataService?.let {
            connectButton.text = getString(R.string.connecting)
            connectButton.isEnabled = false
            it.connect(device)
        }
    }

    private fun connectToUSBDevice(
        port: UsbSerialPort,
        connection: UsbDeviceConnection
    ) {
        startDataService()
        dataService?.let {
            connectButton.text = getString(R.string.connecting)
            connectButton.isEnabled = false
            it.connect(port, connection)
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
                    map?.isMyLocationEnabled = true
                    checkSendDataDialogShown()
                } else {
                    AlertDialog.Builder(this)
                        .setMessage("Location permission is needed in order to discover BLE devices and show your location on map")
                        .setOnDismissListener { checkSendDataDialogShown() }
                        .setPositiveButton("OK", null)
                        .show()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            connectBluetooth()
        }
    }

    override fun onVSpeedData(vspeed: Float) {

    }

    override fun onAltitudeData(altitude: Float) {
        runOnUiThread {
            this.altitude.text = "$altitude m"
        }
    }

    override fun onGPSAltitudeData(altitude: Float) {

    }

    override fun onDistanceData(distance: Int) {
        runOnUiThread {
            this.distance.text = "$distance m"
        }
    }

    override fun onRollData(rollAngle: Float) {
        runOnUiThread {
            horizonView.setRoll(rollAngle)
        }
    }

    override fun onPitchData(pitchAngle: Float) {
        runOnUiThread {
            horizonView.setPitch(pitchAngle)
        }
    }

    override fun onGSpeedData(speed: Float) {
        runOnUiThread {
            if (!preferenceManager.usePitotTube()) {
                updateSpeed(speed)
            }
        }
    }

    override fun onAirSpeed(speed: Float) {
        runOnUiThread {
            if (preferenceManager.usePitotTube()) {
                updateSpeed(speed)
            }
        }
    }

    private fun updateSpeed(speed: Float) {
        this.speed.text = "${speed.roundToInt()} km/h"
    }

    override fun onGPSState(satellites: Int, gpsFix: Boolean) {
        runOnUiThread {
            this.hasGPSFix = gpsFix
            if (gpsFix && marker == null) {
                marker = map?.addMarker(
                    MarkerOptions().icon(
                        bitmapDescriptorFromVector(
                            this,
                            R.drawable.ic_plane, preferenceManager.getPlaneColor()
                        )
                    ).position(lastGPS)
                )
                if (headingPolyline == null && preferenceManager.isHeadingLineEnabled()) {
                    headingPolyline = createHeadingPolyline()
                }
                map?.moveCamera(CameraUpdateFactory.newLatLngZoom(lastGPS, 15f))
            }
            this.satellites.text = satellites.toString()
        }
    }

    private fun createHeadingPolyline(): Polyline? {
        return map?.addPolyline(
            PolylineOptions().add(lastGPS).add(lastGPS).color(preferenceManager.getHeadLineColor()).width(
                3f
            )
        )
    }

    override fun onRSSIData(rssi: Int) {

    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map?.mapType = mapType
        if (checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map?.isMyLocationEnabled = true
            checkSendDataDialogShown()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_LOCATION_PERMISSION
            )
            map?.isMyLocationEnabled = false
        }
        topLayout.measure(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        map?.setPadding(0, topLayout.measuredHeight, 0, 0)
        polyLine = map?.addPolyline(PolylineOptions().color(preferenceManager.getRouteColor()))
        map?.setOnCameraMoveStartedListener {
            if (it == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                followMode = false
            }
        }
    }

    private fun checkSendDataDialogShown() {
        if (!preferenceManager.isSendDataDialogShown()) {
            firebaseAnalytics.logEvent("send_data_dialog_shown", null)
            val dialog = AlertDialog.Builder(this)
                .setMessage(
                    Html.fromHtml(
                        "You can enable telemetry data sharing. Telemetry data sharing sends data to <a href='https://uavradar.org'>https://uavradar.org</a> at which" +
                                "you can watch for other aicraft flights (just like flightradar24, but for UAV). You can assign" +
                                " your callsign and your UAV model in the settings which will be used as your aircraft info. " +
                                "Data sent when you arm your UAV and have valid 3D GPS Fix"
                    )
                )
                .setPositiveButton("Enable") { _, i ->
                    preferenceManager.setTelemetrySendingEnabled(true)
                    firebaseAnalytics.setUserProperty("telemetry_sharing_enable", "true")
                    firebaseAnalytics.logEvent("telemetry_sharing_enabled", null)
                }
                .setNegativeButton("Disable") { _, i ->
                    preferenceManager.setTelemetrySendingEnabled(false)
                    firebaseAnalytics.setUserProperty("telemetry_sharing_enable", "false")
                    firebaseAnalytics.logEvent("telemetry_sharing_disabled", null)
                }
                .setCancelable(false)
                .show()
            dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun showMapTypeSelectorDialog() {
        val fDialogTitle = "Select Map Type"
        val builder = AlertDialog.Builder(this)
        builder.setTitle(fDialogTitle)

        val checkItem = (map?.mapType ?: GoogleMap.MAP_TYPE_NORMAL) - 1

        builder.setSingleChoiceItems(
            MAP_TYPE_ITEMS,
            checkItem
        ) { dialog, item ->
            when (item) {
                1 -> map?.mapType = GoogleMap.MAP_TYPE_SATELLITE
                2 -> map?.mapType = GoogleMap.MAP_TYPE_TERRAIN
                3 -> map?.mapType = GoogleMap.MAP_TYPE_HYBRID
                else -> map?.mapType = GoogleMap.MAP_TYPE_NORMAL
            }
            mapType = map?.mapType ?: GoogleMap.MAP_TYPE_NORMAL
            preferenceManager.setMapType(mapType)
            dialog.dismiss()
        }

        val fMapTypeDialog = builder.create()
        fMapTypeDialog.setCanceledOnTouchOutside(true)
        fMapTypeDialog.show()
    }

    private fun bitmapDescriptorFromVector(
        context: Context, @DrawableRes vectorDrawableResourceId: Int,
        color: Int? = null
    ): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorDrawableResourceId)
        color?.let {
            DrawableCompat.setTint(vectorDrawable!!, it)
        }
        vectorDrawable!!.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap =
            Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onVBATData(voltage: Float) {
        lastVBAT = voltage
        runOnUiThread {
            updateVoltage()
        }
    }

    override fun onCurrentData(current: Float) {
        runOnUiThread {
            this.current.text = "$current A"
        }
    }

    override fun onHeadingData(heading: Float) {
        lastHeading = heading
        runOnUiThread {
            marker?.let {
                it.rotation = heading
                updateHeading()
            }
        }
    }

    private fun updateHeading() {
        headingPolyline?.let { headingLine ->
            val points = headingLine.points
            points[0] = lastGPS
            points[1] = SphericalUtil.computeOffset(lastGPS, 1000.0, lastHeading.toDouble())
            headingLine.points = points
        }
    }

    override fun onCellVoltageData(voltage: Float) {
        lastCellVoltage = voltage
        runOnUiThread {
            updateVoltage()
        }
    }

    private fun updateVoltage() {
        this.voltage.text = "$lastVBAT ($lastCellVoltage) V"
    }

    override fun onDisconnected() {
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
        switchToIdleState()
    }

    private fun switchToReplayMode() {
        seekBar.setOnSeekBarChangeListener(null)
        seekBar.progress = 0
        directionsButton.show()
        connectButton.visibility = View.GONE
        replayButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_close))
        replayButton.setOnClickListener {
            switchToIdleState()
            replayFileString = null
        }
    }

    private fun switchToIdleState() {
        resetUI()
        directionsButton.hide()
        seekBar.visibility = View.GONE
        connectButton.visibility = View.VISIBLE
        connectButton.text = getString(R.string.connect)
        replayButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_replay))
        replayButton.visibility = View.VISIBLE
        replayButton.setOnClickListener {
            replay()
        }
        connectButton.isEnabled = true
        connectButton.setOnClickListener {
            connect()
        }
        marker?.remove()
        marker = null
        val points = polyLine?.points
        points?.clear()
        polyLine?.points = points
        headingPolyline?.remove()
    }

    private fun switchToConnectedState() {
        replayButton.visibility = View.GONE
        connectButton.text = getString(R.string.disconnect)
        connectButton.isEnabled = true
        connectButton.setOnClickListener {
            connectButton.isEnabled = false
            connectButton.text = getString(R.string.disconnecting)
            dataService?.disconnect()
        }
    }

    override fun onConnectionFailed() {
        runOnUiThread {
            Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
            connectButton.text = getString(R.string.connect)
            connectButton.isEnabled = true
            connectButton.setOnClickListener {
                connect()
            }
        }
    }

    override fun onFuelData(fuel: Int) {
        runOnUiThread {
            val batteryUnits = preferenceManager.getBatteryUnits()
            var realFuel = fuel

            when (batteryUnits) {
                "mAh", "mWh" -> {
                    this.fuel.text = "$fuel $batteryUnits"
                    realFuel = ((1 - (4.2f - lastCellVoltage)).coerceIn(0f, 1f) * 100).toInt()
                }
                "Percentage" -> {
                    this.fuel.text = "$fuel%"
                }
            }

            when (realFuel) {
                in 91..100 -> R.drawable.ic_battery_full
                in 81..90 -> R.drawable.ic_battery_90
                in 61..80 -> R.drawable.ic_battery_80
                in 51..60 -> R.drawable.ic_battery_60
                in 31..50 -> R.drawable.ic_battery_50
                in 21..30 -> R.drawable.ic_battery_30
                in 0..20 -> R.drawable.ic_battery_alert
                else -> R.drawable.ic_battery_unknown
            }.let {
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    this.fuel.setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(this, it),
                        null,
                        null,
                        null
                    )
                } else {
                    this.fuel.setCompoundDrawablesWithIntrinsicBounds(
                        null,
                        ContextCompat.getDrawable(this, it),
                        null,
                        null
                    )
                }
            }
        }
    }

    override fun onSuccessDecode() {

    }

    override fun onGPSData(list: List<LatLng>, addToEnd: Boolean) {
        runOnUiThread {
            if (hasGPSFix && list.isNotEmpty()) {
                val points = polyLine?.points
                if (!addToEnd) {
                    points?.clear()
                }
                points?.addAll(list)
                points?.removeAt(points.size - 1)
                polyLine?.points = points
                onGPSData(list[list.size - 1].latitude, list[list.size - 1].longitude)
            }
        }
    }

    override fun onGPSData(latitude: Double, longitude: Double) {
        runOnUiThread {
            if (LatLng(latitude, longitude) != lastGPS) {
                lastGPS = LatLng(latitude, longitude)
                marker?.let { it.position = lastGPS }
                updateHeading()
                if (followMode) {
                    map?.moveCamera(CameraUpdateFactory.newLatLng(lastGPS))
                }
                if (hasGPSFix) {
                    val points = polyLine?.points
                    points?.add(lastGPS)
                    polyLine?.points = points
                }
            }
        }
    }

    override fun onConnected() {
        runOnUiThread {
            Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
            switchToConnectedState()
        }
    }
}
