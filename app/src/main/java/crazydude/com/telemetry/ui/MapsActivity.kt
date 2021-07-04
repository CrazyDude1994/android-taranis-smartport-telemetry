package crazydude.com.telemetry.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
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
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Observer
import com.google.android.gms.maps.*
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.maps.android.SphericalUtil
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import crazydude.com.telemetry.R
import crazydude.com.telemetry.databinding.ActivityMapsBinding
import crazydude.com.telemetry.manager.PreferenceManager
import crazydude.com.telemetry.maps.MapLine
import crazydude.com.telemetry.maps.MapMarker
import crazydude.com.telemetry.maps.MapWrapper
import crazydude.com.telemetry.maps.Position
import crazydude.com.telemetry.maps.google.GoogleMapWrapper
import crazydude.com.telemetry.maps.osm.OsmMapWrapper
import crazydude.com.telemetry.protocol.TelemetryModel
import crazydude.com.telemetry.protocol.pollers.LogPlayer
import crazydude.com.telemetry.service.DataService
import crazydude.com.telemetry.ui.viewmodels.MapsViewModel
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


class MapsActivity : AppCompatActivity() {

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

    private lateinit var sensorViewMap: HashMap<String, TextView>

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private var mapType = GoogleMap.MAP_TYPE_NORMAL

    private var dataService: DataService? = null
    private var lastPhoneBattery = 0

    private var fullscreenWindow = false
    private lateinit var binding: ActivityMapsBinding
    private val viewModel: MapsViewModel by viewModels()
    private val telemetryObserver = Observer<TelemetryModel?> { telemetryModel ->
        telemetryModel?.let {
            val lastGPS = it.position.lastOrNull() ?: Position(0.0, 0.0)
            if (it.gpsFix) {
                polyLine?.setPoints(it.position)
                if (marker == null) {
                    marker =
                        map?.addMarker(
                            R.drawable.ic_plane,
                            preferenceManager.getPlaneColor(),
                            lastGPS
                        )
                    if (headingPolyline == null && preferenceManager.isHeadingLineEnabled()) {
                        headingPolyline = createHeadingPolyline()
                    }
                    map?.moveCamera(lastGPS, 15f)
                }
            }
            marker?.position = lastGPS
            if (viewModel.followMode) {
                map?.moveCamera(lastGPS)
            }
            updateHeading()
            updateFuel()
            updateHorizon()
            updateRSSI();
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(p0: ComponentName?) {
        }

        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            dataService = (p1 as DataService.DataBinder).getService()
            binding.telemetry = dataService?.telemetryLiveData
            binding.connectionState = dataService?.connectionStateLiveData
            dataService?.connectionStateLiveData?.observe(this@MapsActivity, {
                when (it) {
                    DataService.ConnectionState.DISCONNECTED -> switchToIdleState()
                    DataService.ConnectionState.CONNECTED -> switchToConnectedState()
                    DataService.ConnectionState.CONNECTING -> switchToConnectingState()
                    DataService.ConnectionState.REPLAY -> switchToReplayMode()
                }
            })
            dataService?.telemetryLiveData?.observe(this@MapsActivity, telemetryObserver)
        }
    }

    private fun updateHorizon() {
        binding.telemetry?.value?.pitch?.let { binding.horizonView.setPitch(it) }
        binding.telemetry?.value?.roll?.let { binding.horizonView.setRoll(it) }
    }

    private fun updateFuel() {
        val batteryUnits = preferenceManager.getBatteryUnits()
        var realFuel = binding.telemetry?.value?.fuel
        val lastCellVoltage = binding.telemetry?.value?.cellVoltage ?: 0f

        when (batteryUnits) {
            "mAh", "mWh" -> {
                binding.topLayout.fuel.text = "$realFuel $batteryUnits"
                if (lastCellVoltage > 0)
                    realFuel = ((1 - (4.2f - lastCellVoltage)).coerceIn(0f, 1f) * 100).toInt()
            }
            "Percentage" -> {
                binding.topLayout.fuel.text = "$realFuel%"
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
            binding.topLayout.fuel.setCompoundDrawablesWithIntrinsicBounds(
                null,
                ContextCompat.getDrawable(this, it),
                null,
                null
            )
        }
    }

    private fun switchToConnectingState() {
        binding.topLayout.connectButton.apply {
            text = getString(R.string.connecting)
            isEnabled = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_maps)
        binding.lifecycleOwner = this

        preferenceManager = PreferenceManager(this)

        mapType = preferenceManager.getMapType()
        fullscreenWindow = preferenceManager.isFullscreenWindow()

        sensorViewMap = hashMapOf(
            Pair(PreferenceManager.sensors.elementAt(0).name, binding.topLayout.satellites),
            Pair(PreferenceManager.sensors.elementAt(1).name, binding.topLayout.fuel),
            Pair(PreferenceManager.sensors.elementAt(2).name, binding.topLayout.voltage),
            Pair(PreferenceManager.sensors.elementAt(3).name, binding.topLayout.current),
            Pair(PreferenceManager.sensors.elementAt(4).name, binding.bottomLayout.speed),
            Pair(PreferenceManager.sensors.elementAt(5).name, binding.bottomLayout.distance),
            Pair(PreferenceManager.sensors.elementAt(6).name, binding.bottomLayout.altitude),
            Pair(PreferenceManager.sensors.elementAt(7).name, binding.topLayout.phoneBattery),
            Pair(PreferenceManager.sensors.elementAt(8).name, binding.topLayout.rssi)
        )

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        binding.topLayout.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.fullscreenButton.setOnClickListener {
            updateFullscreenState()
            this.fullscreenWindow = !this.fullscreenWindow
            preferenceManager.setFullscreenWindow(fullscreenWindow)
            updateWindowFullscreenDecoration()
        }

        binding.followButton.setOnClickListener {
            viewModel.followMode = true
            marker?.let {
                map?.moveCamera(it.position)
            }
        }

        binding.mapTypeButton.setOnClickListener {
            showMapTypeSelectorDialog()
        }

        binding.directionsButton.setOnClickListener {
            showDirectionsToCurrentLocation()
        }

        binding.directionsButton.setOnLongClickListener {
            showAndCopyCurrentGPSLocation()
            true
        }

        binding.topLayout.replayButton.setOnClickListener { replay() }
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

    fun setSeekbarListener() {
        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekbar: SeekBar,
                position: Int,
                p2: Boolean
            ) {
                dataService?.seekReplay(position)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {

            }
        })
    }

    fun removeSeekbarListener() {
        binding.seekbar.setOnSeekBarChangeListener(null)
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
        binding.mapHolder.addView(mapView)
        map = OsmMapWrapper(applicationContext, mapView) {
        }
        map?.setOnCameraMoveStartedListener {
            viewModel.followMode = false
        }
        polyLine = map?.addPolyline(preferenceManager.getRouteColor())
        showMyLocation()
        startDataService()
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
        binding.mapHolder.addView(mapView)
        map = GoogleMapWrapper(this, mapView) {
            showMyLocation()
            map?.mapType = mapType
            binding.topLayout.root.measure(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            polyLine = map?.addPolyline(preferenceManager.getRouteColor())
            map?.setOnCameraMoveStartedListener {
                viewModel.followMode = false
            }
            map?.setPadding(0, binding.topLayout.root.measuredHeight, 0, 0)
            startDataService()
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
        return binding.connectionState?.value == DataService.ConnectionState.REPLAY
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
                        ?.reversed()
                if (files == null) {
                    Toast.makeText(this, "No log files available", Toast.LENGTH_SHORT).show()
                    return
                }
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
                val files = tree.listFiles().reversed().filter { it.length() > 0 }
                AlertDialog.Builder(this)
                    .setAdapter(
                        ArrayAdapter(
                            this,
                            android.R.layout.simple_list_item_1,
                            files.map { i -> "${i.name} (${i.length() / 1024} Kb)" }
                        )
                    ) { _, i ->
                        startReplay(DocumentLogFile(files[i], contentResolver))
                    }
                    .show()
            } else {
                startActivityForResult(storageIntent(), REQUEST_FILE_TREE_REPLAY)
            }
        }
    }

    private fun startReplay(file: LogFile) {
        file.also {
            val progressDialog = ProgressDialog(this)
            progressDialog.setCancelable(false)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            progressDialog.max = 100
            progressDialog.show()

            dataService?.startReplay(file, object : LogPlayer.DataReadyListener {
                override fun onUpdate(percent: Int) {
                    progressDialog.progress = percent
                }

                override fun onDataReady(size: Int) {
                    binding.seekbar.max = size
                    progressDialog.dismiss()
                }
            })
        }

    }

    override fun onLowMemory() {
        super.onLowMemory()
        map?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        map?.onSaveInstanceState(outState)
        preferenceManager.setFullscreenWindow(fullscreenWindow)
    }

    override fun onStart() {
        super.onStart()
        map?.onStart()
        if (preferenceManager.showArtificialHorizonView()) {
            binding.horizonView.visibility = View.VISIBLE
        } else {
            binding.horizonView.visibility = View.GONE
        }
        updateSensorsPlacement()
    }

    private fun updateSensorsPlacement() {
        val sensorsSettings = preferenceManager.getSensorsSettings()
        binding.topLayout.topList.removeAllViews()
        binding.bottomLayout.bottomList.removeAllViews()
        sensorsSettings.forEach {
            val sensorView = sensorViewMap[it.name]
            sensorView?.visibility = if (it.shown) View.VISIBLE else View.GONE
            if (it.position == "top") {
                binding.topLayout.topList.addView(sensorView)
            } else {
                binding.bottomLayout.bottomList.addView(sensorView)
            }
        }
    }

    private fun connect() {
        if (preferenceManager.isLoggingEnabled()) {
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
                        Toast.makeText(
                            this,
                            "Please select log files save folder",
                            Toast.LENGTH_LONG
                        )
                            .show()
                        startActivityForResult(storageIntent(), REQUEST_FILE_TREE_CREATE_LOG)
                        return
                    }
                }

            }
        }
        val showcaseView = MaterialShowcaseView.Builder(this)
            .setTarget(binding.topLayout.replayButton)
            .setDismissTextColor(Color.GREEN)
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

        val customTable = UsbSerialProber.getDefaultProbeTable()
        customTable.addProduct(0x0483, 0x5740, CdcAcmSerialDriver::class.java) // STM32 Virtual COM Port
        val drivers = UsbSerialProber(customTable).findAllDrivers(usbManager)

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

        if (!bluetoothEnabled()) {
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

    private fun bluetoothEnabled(): Boolean {
        val enabled = BluetoothAdapter.getDefaultAdapter().isEnabled
        if (!enabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        return enabled
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
            Toast.makeText(
                this,
                "Bluetooth LE is not supported or application does not have needed permissions",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!bluetoothEnabled()) {
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
        dataService?.let {
            createLogFile()?.let { file ->
                it.connect(device, file, isBLE)
            }
        }
    }

    private fun connectToUSBDevice(
        port: UsbSerialPort,
        connection: UsbDeviceConnection
    ) {
        dataService?.let {
            createLogFile()?.let { file ->
                it.connect(port, connection, file)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        map?.onDestroy()
        this.unregisterReceiver(this.batInfoReceiver)
        unbindService(serviceConnection)
        dataService?.let {
            if (!isChangingConfigurations && !it.isConnected()) {
                stopDataService()
            }
        }
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

    private fun stopDataService() {
        val intent = Intent(this, DataService::class.java)
        stopService(intent)
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

    @SuppressLint("NewApi")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            connect()
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

    private fun createHeadingPolyline(): MapLine? {
        val lastGPS = binding.telemetry?.value?.position?.lastOrNull() ?: Position(0.0, 0.0)
        return map?.addPolyline(3f, preferenceManager.getHeadLineColor(), lastGPS, lastGPS)
    }

    private fun setRSSIIcon( rssi : Int )  {
        when (rssi) {
            in 81..100 -> R.drawable.ic_rssi_5
            in 61..80 -> R.drawable.ic_rssi_4
            in 41..69 -> R.drawable.ic_rssi_3
            in 21..40 -> R.drawable.ic_rssi_2
            in 0..20 -> R.drawable.ic_rssi_1
            else -> R.drawable.ic_rssi_5
        }.let {
            binding.topLayout.rssi.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable( this, it ),
                    null, null
                )
        }
    }

    private fun updateRSSI() {
        var rssi = binding.telemetry?.value?.rssi
        this.binding.topLayout.rssi.text = if (rssi == -1) "-" else rssi.toString()
        this.setRSSIIcon(rssi?:-1);
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
            dataService?.telemetryLiveData?.removeObserver(telemetryObserver)
            binding.mapHolder.removeAllViews()
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

    private fun updateHeading() {
        headingPolyline?.let { headingLine ->
            val lastGPS = binding.telemetry?.value?.position?.lastOrNull() ?: Position(0.0, 0.0)
            val lastHeading = binding.telemetry?.value?.heading
            lastHeading?.let {
                marker?.rotation = lastHeading
                headingLine.setPoint(0, lastGPS)
                val computeOffset =
                    SphericalUtil.computeOffset(lastGPS.toLatLng(), 1000.0, lastHeading.toDouble())
                headingLine.setPoint(1, Position(computeOffset.latitude, computeOffset.longitude))
            }
        }
    }

    private fun switchToReplayMode() {
        removeSeekbarListener()
        binding.seekbar.max = dataService?.getReplaySize() ?: 0
        binding.seekbar.progress = dataService?.getSeekPosition() ?: 0
        setSeekbarListener()
        binding.directionsButton.show()
        binding.topLayout.replayButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.ic_close
            )
        )
        binding.topLayout.replayButton.setOnClickListener {
            dataService?.stopReplay()
        }
        binding.topLayout.connectButton.visibility = View.GONE
        initHeadingLine()
    }

    private fun switchToIdleState() {
        binding.topLayout.connectButton.apply {
            setOnClickListener {
                connect()
            }
            text = getString(R.string.connect)
            isEnabled = true
            visibility = View.VISIBLE
        }

        binding.topLayout.replayButton.apply {
            setImageDrawable(ContextCompat.getDrawable(this@MapsActivity, R.drawable.ic_replay))
            setOnClickListener { replay() }
        }
        marker?.remove()
        marker = null
        polyLine?.clear()
        headingPolyline?.remove()
        headingPolyline = null
    }

    private fun switchToConnectedState() {
        initHeadingLine()
        binding.topLayout.connectButton.apply {
            text = getString(R.string.disconnect)
            isEnabled = true
            setOnClickListener {
                isEnabled = false
                text = getString(R.string.disconnecting)
                dataService?.disconnect()
            }
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
        binding.topLayout.phoneBattery.text = "$lastPhoneBattery%"
    }
}

fun shouldUseStorageAPI(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
