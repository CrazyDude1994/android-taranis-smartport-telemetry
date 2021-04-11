package crazydude.com.telemetry.ui

import android.app.PendingIntent
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.google.android.gms.maps.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.maps.android.SphericalUtil
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.nex3z.flowlayout.FlowLayout
import crazydude.com.telemetry.R
import crazydude.com.telemetry.converter.Converter
import crazydude.com.telemetry.manager.PreferenceManager
import crazydude.com.telemetry.maps.MapLine
import crazydude.com.telemetry.maps.MapMarker
import crazydude.com.telemetry.maps.MapWrapper
import crazydude.com.telemetry.maps.Position
import crazydude.com.telemetry.maps.google.GoogleMapWrapper
import crazydude.com.telemetry.maps.osm.OsmMapWrapper
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import crazydude.com.telemetry.protocol.pollers.LogPlayer
import crazydude.com.telemetry.service.DataService
import crazydude.com.telemetry.utils.DocumentLogFile
import crazydude.com.telemetry.utils.LogFile
import crazydude.com.telemetry.utils.StandardLogFile
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.roundToInt


class MapsActivity : AppCompatActivity(), DataDecoder.Listener {

    companion object {
        private const val REQUEST_ENABLE_BT: Int = 0
        private const val REQUEST_LOCATION_PERMISSION: Int = 1
        private const val REQUEST_WRITE_PERMISSION: Int = 2
        private const val REQUEST_READ_PERMISSION: Int = 3
        private const val REQUEST_FILE_TREE_REPLAY: Int = 4
        private const val REQUEST_FILE_TREE_CREATE_LOG: Int = 5
        private const val ACTION_USB_DEVICE = "action_usb_device"
        private val MAP_TYPE_ITEMS = arrayOf(
            "Road Map (Google)",
            "Satellite (Google)",
            "Terrain (Google)",
            "Hybrid (Google)",
            "OpenStreetMap (can be cached)"
        )
    }

    private var map: MapWrapper? = null

    private var marker: MapMarker? = null
    private var polyLine: MapLine? = null
    private var headingPolyline: MapLine? = null

    private lateinit var connectButton: Button
    private lateinit var replayButton: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var fuel: TextView
    private lateinit var satellites: TextView
    private lateinit var current: TextView
    private lateinit var voltage: TextView
    private lateinit var phoneBattery: TextView
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
    private lateinit var topList: FlowLayout
    private lateinit var bottomList: FlowLayout
    private lateinit var rootLayout: CoordinatorLayout
    private lateinit var mapHolder: FrameLayout

    private lateinit var sensorViewMap: HashMap<String, TextView>
    private lateinit var sensorsConverters: HashMap<String, Converter>

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private var mapType = GoogleMap.MAP_TYPE_NORMAL

    private var lastGPS = Position(0.0, 0.0)
    private var lastHeading = 0f
    private var followMode = true
    private var hasGPSFix = false
    private var replayFileString: String? = null
    private var dataService: DataService? = null
    private var lastVBAT = 0f
    private var lastCellVoltage = 0f
    private var lastPhoneBattery = 0

    private var fullscreenWindow = false

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
                    polyLine?.addPoints(it.points)
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
        fullscreenWindow = preferenceManager.isFullscreenWindow()

        rootLayout = findViewById(R.id.rootLayout)
        fuel = findViewById(R.id.fuel)
        satellites = findViewById(R.id.satellites)
        topLayout = findViewById(R.id.top_layout)
        connectButton = findViewById(R.id.connect_button)
        current = findViewById(R.id.current)
        voltage = findViewById(R.id.voltage)
        phoneBattery = findViewById(R.id.phone_battery)
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
        topList = findViewById(R.id.top_list)
        bottomList = findViewById(R.id.bottom_list)
        mapHolder = findViewById(R.id.map_holder)

        sensorViewMap = hashMapOf(
            Pair(PreferenceManager.sensors.elementAt(0).name, satellites),
            Pair(PreferenceManager.sensors.elementAt(1).name, fuel),
            Pair(PreferenceManager.sensors.elementAt(2).name, voltage),
            Pair(PreferenceManager.sensors.elementAt(3).name, current),
            Pair(PreferenceManager.sensors.elementAt(4).name, speed),
            Pair(PreferenceManager.sensors.elementAt(5).name, distance),
            Pair(PreferenceManager.sensors.elementAt(6).name, altitude),
            Pair(PreferenceManager.sensors.elementAt(7).name, phoneBattery)
        )

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        fullscreenButton.setOnClickListener {
            updateFullscreenState()
            this.fullscreenWindow = !this.fullscreenWindow
            preferenceManager.setFullscreenWindow(fullscreenWindow)
            updateWindowFullscreenDecoration()
        }

        followButton.setOnClickListener {
            followMode = true
            marker?.let {
                map?.moveCamera(it.position)
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
            val logFile: LogFile
            if (shouldUseStorageAPI()) {
                logFile = DocumentLogFile(
                    DocumentFile.fromSingleUri(this, Uri.parse(replayFileString))!!,
                    contentResolver
                )
            } else {
                logFile = StandardLogFile(
                    File(
                        Environment.getExternalStoragePublicDirectory("TelemetryLogs"),
                        replayFileString
                    )
                )
            }
            startReplay(logFile)
        } else {
            switchToIdleState()
        }

        startDataService()

        checkAppInstallDate()
        initMap(false)
        map?.onCreate(savedInstanceState)

        this.registerReceiver(this.batInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun updateWindowFullscreenDecoration() {
        if (!this.fullscreenWindow) {
            window.decorView.systemUiVisibility = 0
        } else {
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE)
        }
    }

    private fun updateFullscreenState() {
        //user may have brought system ui with a swipe. Update state
        this.fullscreenWindow = window.decorView.systemUiVisibility ==
                (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE)
    }


    private fun initMap(simulateLifecycle: Boolean) {
        if (mapType in GoogleMap.MAP_TYPE_NORMAL..GoogleMap.MAP_TYPE_HYBRID) {
            initGoogleMap(simulateLifecycle)
        } else {
            initOSMMap()
        }
    }

    private fun initOSMMap() {
        val mapView = org.osmdroid.views.MapView(this)
        mapHolder.addView(mapView)
        map = OsmMapWrapper(applicationContext, mapView) {
            initHeadingLine()
        }
        map?.setOnCameraMoveStartedListener {
            followMode = false
        }
        polyLine = map?.addPolyline(preferenceManager.getRouteColor())
        showMyLocation()
    }

    private fun showMyLocation() {
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
    }

    private fun initGoogleMap(simulateLifecycle: Boolean) {
        val mapView = MapView(this)
        mapHolder.addView(mapView)
        map = GoogleMapWrapper(this, mapView) {
            showMyLocation()
            map?.mapType = mapType
            topLayout.measure(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            polyLine = map?.addPolyline(preferenceManager.getRouteColor())
            map?.setOnCameraMoveStartedListener {
                followMode = false
            }
            map?.setPadding(0, topLayout.measuredHeight, 0, 0)
            initHeadingLine()
        }
        if (simulateLifecycle) {
            map?.onCreate(null)
            map?.onStart()
            map?.onResume()
        }
    }

    private fun initHeadingLine() {
        polyLine?.let { it.color = preferenceManager.getRouteColor() }
        if (!isIdle()) {
            if (preferenceManager.isHeadingLineEnabled() && headingPolyline == null) {
                headingPolyline = createHeadingPolyline()
                updateHeading()
            } else if (!preferenceManager.isHeadingLineEnabled() && headingPolyline != null) {
                headingPolyline?.remove()
                headingPolyline = null
            }
            headingPolyline?.let { it.color = preferenceManager.getHeadLineColor() }
            marker?.setIcon(R.drawable.ic_plane, preferenceManager.getPlaneColor())
        }
    }

    private fun showAndCopyCurrentGPSLocation() {
        marker?.let {
            val posString = "${it.position.lat},${it.position.lon}"
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.setPrimaryClip(ClipData.newPlainText("Location", posString))
            Toast.makeText(
                this,
                "Current plane location copied to clipboard ($posString)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showDirectionsToCurrentLocation() {
        marker?.let {
            val posString = "${it.position.lat},${it.position.lon}"
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("http://maps.google.com/maps?daddr=$posString")
            )
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
            if (shouldUseStorageAPI()) {
                replayWithStorageAPI()
            } else {
                replayWithFileAPI()
            }
        } else {
            Toast.makeText(this, "You need to disconnect first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun replayWithFileAPI() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                `REQUEST_READ_PERMISSION`
            )
        } else {
            val dir = Environment.getExternalStoragePublicDirectory("TelemetryLogs")
            if (dir.exists()) {
                val files =
                    dir.listFiles { file -> file.extension == "log" && file.length() > 0 }
                        .reversed()
                AlertDialog.Builder(this)
                    .setAdapter(
                        ArrayAdapter(
                            this,
                            android.R.layout.simple_list_item_1,
                            files.map { i -> "${i.nameWithoutExtension} (${i.length() / 1024} Kb)" })
                    ) { _, i ->
                        startReplay(StandardLogFile(files[i]))
                    }
                    .show()
            }
        }
    }

    private fun storageIntent() = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, preferenceManager.getLogsStorageFolder())
    }

    private fun replayWithStorageAPI() {
        if (preferenceManager.getLogsStorageFolder() == null) {
            startActivityForResult(storageIntent(), REQUEST_FILE_TREE_REPLAY)
        } else {
            val tree = DocumentFile.fromTreeUri(
                this,
                Uri.parse(preferenceManager.getLogsStorageFolder())
            )
            if (tree?.canRead() == true) {
                val files = tree.listFiles().reversed()
                AlertDialog.Builder(this)
                    .setAdapter(
                        ArrayAdapter<String>(
                            this,
                            android.R.layout.simple_list_item_1,
                            files.map { i -> "${i.name} (${i.length() / 1024} Kb)" }
                                .toMutableList()
                        )
                    ) { _, i ->
                        startReplay(DocumentLogFile(files[i], contentResolver))
                    }
                    .show()
            } else {
                startActivityForResult(intent, REQUEST_FILE_TREE_REPLAY)
            }
        }
    }

    private fun startReplay(file: LogFile) {
        file.also {
            updateWindowFullscreenDecoration()
            val progressDialog = ProgressDialog(this)
            progressDialog.setCancelable(false)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            progressDialog.max = 100

            progressDialog.window?.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
            progressDialog.show()
            if (!this.fullscreenWindow) {
                progressDialog.window?.decorView?.systemUiVisibility = 0
            } else {
                progressDialog.window?.decorView?.systemUiVisibility =
                    (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE)
            }
            progressDialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

            switchToReplayMode()

            if (shouldUseStorageAPI()) {
                replayFileString = it.uri.toString()
            } else {
                replayFileString = it.name
            }

            val logPlayer =
                LogPlayer(this, contentResolver)
            logPlayer.load(file, object : LogPlayer.DataReadyListener {
                override fun onUpdate(percent: Int) {
                    progressDialog.progress = percent
                }

                override fun onDataReady(size: Int) {
                    progressDialog.dismiss()
                    seekBar.max = size
                    seekBar.visibility = View.VISIBLE
                    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekbar: SeekBar,
                            position: Int,
                            p2: Boolean
                        ) {
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
            DataDecoder.Companion.FlyMode.CIRCLE -> {
                mode.text = mode.text.toString() + " | Circle"
            }
            DataDecoder.Companion.FlyMode.STABILIZE -> {
                mode.text = mode.text.toString() + " | Stabilize"
            }
            DataDecoder.Companion.FlyMode.TRAINING -> {
                mode.text = mode.text.toString() + " | Training"
            }
            DataDecoder.Companion.FlyMode.FBWA -> {
                mode.text = mode.text.toString() + " | FBWA"
            }
            DataDecoder.Companion.FlyMode.FBWB -> {
                mode.text = mode.text.toString() + " | FBWB"
            }
            DataDecoder.Companion.FlyMode.AUTOTUNE -> {
                mode.text = mode.text.toString() + " | Autotune"
            }
            DataDecoder.Companion.FlyMode.LOITER -> {
                mode.text = mode.text.toString() + " | Loiter"
            }
            DataDecoder.Companion.FlyMode.TAKEOFF -> {
                mode.text = mode.text.toString() + " | Takeoff"
            }
            DataDecoder.Companion.FlyMode.AVOID_ADSB -> {
                mode.text = mode.text.toString() + " | AVOID_ADSB"
            }
            DataDecoder.Companion.FlyMode.GUIDED -> {
                mode.text = mode.text.toString() + " | Guided"
            }
            DataDecoder.Companion.FlyMode.INITIALISING -> {
                mode.text = mode.text.toString() + " | Initializing"
            }
            DataDecoder.Companion.FlyMode.LANDING -> {
                mode.text = mode.text.toString() + " | Landing"
            }
            DataDecoder.Companion.FlyMode.MISSION -> {
                mode.text = mode.text.toString() + " | Mission"
            }
            DataDecoder.Companion.FlyMode.QSTABILIZE -> {
                mode.text = mode.text.toString() + " | QSTABILIZE"
            }
            DataDecoder.Companion.FlyMode.QHOVER -> {
                mode.text = mode.text.toString() + " | QHOVER"
            }
            DataDecoder.Companion.FlyMode.QLOITER -> {
                mode.text = mode.text.toString() + " | QLOITER"
            }
            DataDecoder.Companion.FlyMode.QLAND -> {
                mode.text = mode.text.toString() + " | QLAND"
            }
            DataDecoder.Companion.FlyMode.QRTL -> {
                mode.text = mode.text.toString() + " | QRTL"
            }
            DataDecoder.Companion.FlyMode.QAUTOTUNE -> {
                mode.text = mode.text.toString() + " | QAUTOTUNE"
            }
            DataDecoder.Companion.FlyMode.QACRO -> {
                mode.text = mode.text.toString() + " | QACRO"
            }
            null -> {
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        map?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        map?.onSaveInstanceState(outState)
        outState.putBoolean("follow_mode", followMode)
        outState.putString("replay_file_name", replayFileString)
        preferenceManager.setFullscreenWindow(fullscreenWindow)
    }

    override fun onStart() {
        super.onStart()
        map?.onStart()
        if (preferenceManager.showArtificialHorizonView()) {
            horizonView.visibility = View.VISIBLE
        } else {
            horizonView.visibility = View.GONE
        }
        updateSensorsPlacement()
    }

    private fun updateSensorsPlacement() {
        val sensorsSettings = preferenceManager.getSensorsSettings()
        topList.removeAllViews()
        bottomList.removeAllViews()
        sensorsSettings.forEach {
            val sensorView = sensorViewMap[it.name]
            sensorView?.visibility = if (it.shown) View.VISIBLE else View.GONE
            if (it.position == "top") {
                topList.addView(sensorView)
            } else {
                bottomList.addView(sensorView)
            }
        }
    }

    private fun connect() {
        if (!shouldUseStorageAPI()) {
            if (!storageWriteCheck()) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_PERMISSION
                )
                return
            }
        } else {
            if (preferenceManager.getLogsStorageFolder() == null) {
                Toast.makeText(this, "Please select log files save folder", Toast.LENGTH_LONG)
                    .show()
                startActivityForResult(storageIntent(), REQUEST_FILE_TREE_CREATE_LOG)
                return
            } else {
                val tree = DocumentFile.fromTreeUri(
                    this,
                    Uri.parse(preferenceManager.getLogsStorageFolder())
                )
                if (tree?.canWrite() == false) {
                    Toast.makeText(this, "Please select log files save folder", Toast.LENGTH_LONG)
                        .show()
                    startActivityForResult(storageIntent(), REQUEST_FILE_TREE_CREATE_LOG)
                    return
                }
            }

        }
        val showcaseView = MaterialShowcaseView.Builder(this)
            .setTarget(replayButton)
            .setMaskColour(Color.argb(230, 0, 0, 0))
            .setDismissText("GOT IT")
            .setContentText("You can replay your logged flights by clicking this button")
            .singleUse("replay_guide").build()

        if (showcaseView.hasFired()) {
            AlertDialog.Builder(this)
                .setItems(
                    arrayOf(
                        "Bluetooth (Classic)",
                        "Bluetooth LE",
                        "USB Serial"
                    )
                ) { dialogInterface, i ->
                    when (i) {
                        0 -> connectBluetooth()
                        1 -> connectBluetoothLE()
                        2 -> connectUSB()
                    }
                }
                .setTitle("Choose connection method")
                .show()
        } else {
            showcaseView.show(this)
        }
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
                    Toast.makeText(this, "No valid usb port has been found", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    connectToUSBDevice(port, connection)
                }
            } else {
                val pendingIntent =
                    PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_DEVICE), 0)
                registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (ACTION_USB_DEVICE == intent?.action) {
                            synchronized(this) {
                                val device: UsbDevice? =
                                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                                if (intent.getBooleanExtra(
                                        UsbManager.EXTRA_PERMISSION_GRANTED,
                                        false
                                    )
                                ) {
                                    device?.apply {
                                        connectUSB()
                                    }
                                } else {
                                    Toast.makeText(
                                        this@MapsActivity,
                                        "You need to allow permission in order to connect with a usb",
                                        Toast.LENGTH_SHORT
                                    ).show()
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

    override fun onResume() {
        super.onResume()
        map?.onResume()
        updateWindowFullscreenDecoration()
    }

    override fun onPause() {
        super.onPause()
        map?.onPause()
        updateFullscreenState()//check if user has brought system ui with swipe
    }

    override fun onStop() {
        super.onStop()
        map?.onStop()
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

        val devices = ArrayList<BluetoothDevice>(adapter.bondedDevices)
        val deviceNames = ArrayList<String>(devices.map {
            var result = it.name
            if (result == null) {
                result = it.address
            }
            result
        }.filterNotNull())
        val deviceAdapter =
            ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, deviceNames)

        AlertDialog.Builder(this)
            .setNeutralButton(R.string.pair_new_device) { dialog, which ->
                showPairDeviceDialog()
            }
            .setAdapter(deviceAdapter) { _, i ->
                runOnUiThread {
                    connectToBluetoothDevice(devices[i], false)
                }
            }.show()
    }

    private fun showPairDeviceDialog() {
        val devices = ArrayList<BluetoothDevice>()
        val deviceNames = ArrayList<String>()
        val deviceAdapter =
            ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, deviceNames)
        AlertDialog.Builder(this)
            .setAdapter(deviceAdapter) { _, i ->
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                pairDevice(devices[i])
            }.show()
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        unregisterReceiver(this)
                    }
                    BluetoothDevice.ACTION_FOUND -> {
                        val device =
                            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)!!
                        val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                            ?: device.address
                        if (!deviceNames.contains(name) && device.bondState == BluetoothDevice.BOND_NONE) {
                            devices.add(device)
                            deviceNames.add(name)
                            deviceAdapter.notifyDataSetChanged()
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {

                    }
                }
            }
        }
        registerReceiver(listener, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED).apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
        })
        BluetoothAdapter.getDefaultAdapter().startDiscovery()
    }

    private fun pairDevice(bluetoothDevice: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (!bluetoothDevice.createBond()) {
                Toast.makeText(this, "Failed to pair bluetooth device", Toast.LENGTH_LONG).show()
            } else {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                            val device =
                                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            val newBondState: Int =
                                intent.getIntExtra(
                                    BluetoothDevice.EXTRA_BOND_STATE,
                                    BluetoothDevice.BOND_NONE
                                )
                            if (newBondState == BluetoothDevice.BOND_BONDED) {
                                device?.let { connectToBluetoothDevice(it, false) }
                                unregisterReceiver(this)
                            } else if (newBondState == BluetoothDevice.BOND_NONE) {
                                Toast.makeText(
                                    this@MapsActivity,
                                    "Failed to pair new device",
                                    Toast.LENGTH_LONG
                                ).show()
                                unregisterReceiver(this)
                            }
                        }
                    }
                }

                registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            }
        } else {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.pair_not_supported_message))
                .show()
        }
    }

    private fun connectBluetoothLE() {
        if (!bleCheck()) {
            Toast.makeText(this, "Bluetooth LE is not supported or application does not have needed permissions", Toast.LENGTH_LONG).show()
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val devices = ArrayList<BluetoothDevice>()
        val deviceNames = ArrayList<String>()
        val deviceAdapter =
            ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, deviceNames)

        val callback = BluetoothAdapter.LeScanCallback { bluetoothDevice, i, bytes ->
            if (!devices.contains(bluetoothDevice) && bluetoothDevice.name != null) {
                devices.add(bluetoothDevice)
                deviceNames.add(bluetoothDevice.name)
                deviceAdapter.notifyDataSetChanged()
            }
        }

        adapter.startLeScan(callback)

        AlertDialog.Builder(this).setOnDismissListener {
            adapter.stopLeScan(callback)
        }.setAdapter(deviceAdapter) { _, i ->
            adapter.stopLeScan(callback)
            connectToBluetoothDevice(devices[i], true)
        }.show()
    }

    private fun resetUI() {
        satellites.text = "0"
        voltage.text = "-"
        phoneBattery.text = "-"
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

    private fun storageWriteCheck() = ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED


    private fun connectToBluetoothDevice(device: BluetoothDevice, isBLE: Boolean) {
        startDataService()
        dataService?.let {
            connectButton.text = getString(R.string.connecting)
            connectButton.isEnabled = false
            createLogFile()?.let { file ->
                it.connect(device, file, isBLE)
            }
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
            createLogFile()?.let { file ->
                it.connect(port, connection, file)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        map?.onDestroy()
        if (!isChangingConfigurations) {
            dataService?.setDataListener(null)
        }
        this.unregisterReceiver(this.batInfoReceiver)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            connectBluetooth()
        } else if (requestCode == REQUEST_FILE_TREE_REPLAY && resultCode == RESULT_OK) {
            preferenceManager.setLogsStorageFolder(data?.dataString)
            contentResolver.takePersistableUriPermission(
                data?.data!!,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            replay()
        } else if (requestCode == REQUEST_FILE_TREE_CREATE_LOG && resultCode == RESULT_OK) {
            contentResolver.takePersistableUriPermission(
                data?.data!!,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            preferenceManager.setLogsStorageFolder(data.dataString)
            connect()
        }
    }

    override fun onVSpeedData(vspeed: Float) {

    }

    override fun onAltitudeData(altitude: Float) {
        runOnUiThread {
            this.altitude.text = "${"%.2f".format(altitude)} m"
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
                marker =
                    map?.addMarker(R.drawable.ic_plane, preferenceManager.getPlaneColor(), lastGPS)
                if (headingPolyline == null && preferenceManager.isHeadingLineEnabled()) {
                    headingPolyline = createHeadingPolyline()
                }
                map?.moveCamera(lastGPS, 15f)
            }
            this.satellites.text = satellites.toString()
        }
    }

    private fun createHeadingPolyline(): MapLine? {
        return map?.addPolyline(3f, preferenceManager.getHeadLineColor(), lastGPS, lastGPS)
    }

    override fun onRSSIData(rssi: Int) {

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
            dialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
                LinkMovementMethod.getInstance()
        }
    }

    private fun showMapTypeSelectorDialog() {
        val fDialogTitle = "Select Map Type"
        val builder = AlertDialog.Builder(this)
        builder.setTitle(fDialogTitle)

        val checkItem = preferenceManager.getMapType() - 1

        builder.setSingleChoiceItems(
            MAP_TYPE_ITEMS,
            checkItem
        ) { dialog, item ->
            mapHolder.removeAllViews()
            map = null
            mapType = item + 1
            preferenceManager.setMapType(mapType)
            initMap(true)
            dialog.dismiss()
        }

        val fMapTypeDialog = builder.create()
        fMapTypeDialog.setCanceledOnTouchOutside(true)
        fMapTypeDialog.show()
    }

    override fun onVBATData(voltage: Float) {
        lastVBAT = voltage
        runOnUiThread {
            updateVoltage()
        }
    }

    override fun onCurrentData(current: Float) {
        runOnUiThread {
            this.current.text = "${"%.2f".format(current)} A"
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
            headingLine.setPoint(0, lastGPS)
            val computeOffset =
                SphericalUtil.computeOffset(lastGPS.toLatLng(), 1000.0, lastHeading.toDouble())
            headingLine.setPoint(1, Position(computeOffset.latitude, computeOffset.longitude))
        }
    }

    override fun onCellVoltageData(voltage: Float) {
        lastCellVoltage = voltage
        runOnUiThread {
            updateVoltage()
        }
    }

    private fun updateVoltage() {
        if (lastCellVoltage > 0)
            this.voltage.text = "${"%.2f".format(lastVBAT)} (${"%.2f".format(lastCellVoltage)}) V"
        else
            this.voltage.text = "${"%.2f".format(lastVBAT)} V"
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
        polyLine?.clear()
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
                    if (lastCellVoltage > 0)
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

    override fun onGPSData(list: List<Position>, addToEnd: Boolean) {
        runOnUiThread {
            if (hasGPSFix && list.isNotEmpty()) {
                if (!addToEnd) {
                    polyLine?.clear()
                }
                polyLine?.addPoints(list)
                polyLine?.removeAt(polyLine?.size!! - 1)
                onGPSData(list[list.size - 1].lat, list[list.size - 1].lon)
            }
        }
    }

    override fun onGPSData(latitude: Double, longitude: Double) {
        runOnUiThread {
            if (Position(latitude, longitude) != lastGPS) {
                lastGPS = Position(latitude, longitude)
                marker?.let { it.position = lastGPS }
                updateHeading()
                if (followMode) {
                    map?.moveCamera(lastGPS)
                }
                if (hasGPSFix) {
                    polyLine?.addPoints(listOf(lastGPS))
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

    private fun createLogFile(): OutputStream? {
        var fileOutputStream: OutputStream? = null
        if (preferenceManager.isLoggingEnabled()) {
            val name = SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())
            if (!shouldUseStorageAPI()) {
                val dir = Environment.getExternalStoragePublicDirectory("TelemetryLogs")
                dir.mkdirs()
                val file = File(dir, "$name.log")
                fileOutputStream = FileOutputStream(file)
            } else {
                val tree = DocumentFile.fromTreeUri(
                    this,
                    Uri.parse(preferenceManager.getLogsStorageFolder())
                )
                val documentFile =
                    tree?.createFile("application/octet-stream", "$name.log")!!
                fileOutputStream =
                    DocumentLogFile(documentFile, contentResolver).outputStream
            }

            return fileOutputStream
        }

        return null
    }

    private val batInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context?, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            lastPhoneBattery = level
            runOnUiThread {
                updatePhoneBattery()
            }
        }
    }

    private fun updatePhoneBattery() {
        this.phoneBattery.text = "$lastPhoneBattery%"
    }
}

fun shouldUseStorageAPI(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
