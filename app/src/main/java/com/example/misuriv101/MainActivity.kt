package com.example.misuriv101

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.util.Log

import com.google.android.material.snackbar.Snackbar

private const val TAG = "MainActivity"
private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34

@RequiresApi(Build.VERSION_CODES.M)
class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener, SensorEventListener {

    private var foregroundOnlyLocationServiceBound = false

    private var foregroundOnlyLocationService: ForegroundOnlyLocationService? = null

    private lateinit var foregroundOnlyBroadcastReceiver: ForegroundOnlyBroadcastReceiver

    private lateinit var sharedPreferences:SharedPreferences

    private val foregroundOnlyServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as ForegroundOnlyLocationService.LocalBinder
            foregroundOnlyLocationService = binder.service
            foregroundOnlyLocationServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            foregroundOnlyLocationService = null
            foregroundOnlyLocationServiceBound = false
        }
    }

    // Steps that the user takes before the app starts to log distance, (warm-up steps)
    private val startoffset = 5

    // How many steps the user takes before app stops registering distance
    private val stepsUntilBreak = 20

    private var locationArray: MutableList<Location> = ArrayList()

    // Used in onSensorChanged
    private var stepCounterRunning = false

    // Increments for every taken step
    private var stepstaken = 0

    private lateinit var stepstextView : TextView
    private var sensorManager:SensorManager? = null

    // Renders updates to a textView
    private fun renderLog(msg: String) {
        val errors: TextView = findViewById(R.id.errorlogs)
        errors.append(System.getProperty("line.separator")!! + msg)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        foregroundOnlyBroadcastReceiver = ForegroundOnlyBroadcastReceiver()

        sharedPreferences =
            getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)

        // Get health permissions
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                2
            )
            renderLog("Health permission prompted")
        }

        // Register sensor manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager!!.registerListener(
            this,
            sensorManager!!.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR),
            SensorManager.SENSOR_DELAY_FASTEST
        )


        // Make textView scrollable for event logging
        val errors: TextView = findViewById(R.id.errorlogs)
        errors.movementMethod = ScrollingMovementMethod()

        // Display taken steps on screen
        stepstextView = findViewById(R.id.takensteps)

        // Bad way to set font
        val textView: TextView = findViewById(R.id.stepsLbl)
        val textView2: TextView = findViewById(R.id.height_title)
        val textView3: TextView = findViewById(R.id.calculation_result)
        val textView4: TextView = findViewById(R.id.showversion)
        val spacegrotesk: Typeface = Typeface.createFromAsset(this.assets, "fonts/spacegrotesk.ttf")
        textView.typeface = spacegrotesk
        textView2.typeface = spacegrotesk
        textView3.typeface = spacegrotesk
        errors.typeface = spacegrotesk
        stepstextView.typeface = spacegrotesk
        textView4.typeface = spacegrotesk

        // Create an onClick for the "Start"-button
        val button: Button = findViewById(R.id.init_button)
        button.setOnClickListener {
            val enabled = sharedPreferences.getBoolean(
                SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)

            if (enabled) {
                foregroundOnlyLocationService?.unsubscribeToLocationUpdates()
            } else {
                if (foregroundPermissionApproved()) {
                    foregroundOnlyLocationService?.subscribeToLocationUpdates()
                        ?: renderLog("Service not bound")
                } else {
                    requestForegroundPermissions()
                }
            }
            if (!stepCounterRunning) {
                // Activate step counter
                stepCounterRunning = true
                Toast.makeText(this, "Please take 5 steps to initiate", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        val serviceIntent = Intent(this, ForegroundOnlyLocationService::class.java)
        bindService(serviceIntent, foregroundOnlyServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            foregroundOnlyBroadcastReceiver,
            IntentFilter(
                ForegroundOnlyLocationService.ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST
            )
        )
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            foregroundOnlyBroadcastReceiver
        )
        super.onPause()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR && stepCounterRunning) {
            stepstaken += 1
            stepstextView.text = stepstaken.toString()

            if (stepstaken == startoffset) {
                renderLog("Steps = $startoffset")
            }
            if (stepstaken == startoffset + stepsUntilBreak) {
                foregroundOnlyLocationService?.unsubscribeToLocationUpdates()
                calculateResult()
                stepCounterRunning = false
                renderLog("Steps = " + (startoffset + stepsUntilBreak).toString())
            }
        }
    }

    // This function is called once the steps surpass stepsUntilBreak + startoffset
    private fun calculateResult() {

        // Calculate walked distance
        // TODO: Customize this so that user can walk curved distances
        val distanceInMeters = locationArray.last().distanceTo(locationArray[0])
        renderLog("Calculated distance")

        // This part of code will be different, we will be using a model developed with linear regression
        val strideLength = distanceInMeters / stepsUntilBreak
        val heightMin = strideLength / 0.39
        val heightMax = strideLength / 0.46
        val heightAvg = strideLength / 0.42
        renderLog("Calculated proportions")

        renderToScreen(locationArray[0].latitude, locationArray[0].longitude, locationArray.last().latitude, locationArray.last().longitude, distanceInMeters)
    }

    @SuppressLint("SetTextI18n")
    fun renderToScreen(startla: Double, startlo: Double, endla: Double, endlo: Double, dist: Float) {
        val textView: TextView = findViewById(R.id.calculation_result)
        textView.text = "Startlocation: (${startla},${startlo}), Stoplocation: (${endla}, ${endlo}), Distance: $dist"
    }

    override fun onStop() {
        if (foregroundOnlyLocationServiceBound) {
            unbindService(foregroundOnlyServiceConnection)
            foregroundOnlyLocationServiceBound = false
        }
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        // Updates button states if new while in use location is added to SharedPreferences.
        if (key == SharedPreferenceUtil.KEY_FOREGROUND_ENABLED) {
            renderLog("onSharedPreferenceChanged")
        }
    }

    private fun foregroundPermissionApproved(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun requestForegroundPermissions() {
        val provideRationale = foregroundPermissionApproved()

        // If the user denied a previous request, but didn't check "Don't ask again", provide
        // additional rationale.
        if (provideRationale) {
            Snackbar.make(
                findViewById(R.id.activity_main),
                R.string.permission_rationale,
                Snackbar.LENGTH_LONG
            )
                .setAction(R.string.ok) {
                    // Request permission
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
                    )
                }
                .show()
        } else {
            renderLog("Requested foreground-only permission")
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        renderLog("onRequestPermissionsResult()")

        when (requestCode) {
            REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE -> when {
                grantResults.isEmpty() ->
                    // If user interaction was interrupted, the permission request
                    // is cancelled and you receive empty arrays.
                    renderLog("User interaction was cancelled.")

                grantResults[0] == PackageManager.PERMISSION_GRANTED ->
                    // Permission was granted.
                    foregroundOnlyLocationService?.subscribeToLocationUpdates()
                        ?: renderLog( "Service Not Bound 2")

                else -> {
                    // Permission denied.
                    Snackbar.make(
                        findViewById(R.id.activity_main),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_LONG
                    )
                        .setAction(R.string.settings) {
                            // Build intent that displays the App settings screen.
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri = Uri.fromParts(
                                "package",
                                BuildConfig.APPLICATION_ID,
                                null
                            )
                            intent.data = uri
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }
                        .show()
                }
            }
        }
    }

    /**
     * Receiver for location broadcasts from [ForegroundOnlyLocationService].
     */
    private inner class ForegroundOnlyBroadcastReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(
                ForegroundOnlyLocationService.EXTRA_LOCATION
            )
            if (location != null) {
                locationArray.add(location)
            }
        }
    }
}

