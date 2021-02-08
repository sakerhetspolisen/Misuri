package com.example.misuri

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
import android.media.MediaPlayer
import com.google.android.material.snackbar.Snackbar

private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34

@RequiresApi(Build.VERSION_CODES.M)
class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener, SensorEventListener {

    private var foregroundOnlyLocationServiceBound = false
    private var foregroundOnlyLocationService: ForegroundOnlyLocationService? = null
    private lateinit var foregroundOnlyBroadcastReceiver: ForegroundOnlyBroadcastReceiver
    private lateinit var sharedPreferences:SharedPreferences
    private var appIsRunning = false
    private val locationUpdatesUntilBreak = 10
    private var locationUpdatesReceived = 0
    private var sensorManager:SensorManager? = null
    private var mediaPlayer:MediaPlayer? = null

    // All location instances are appended to this array
    private var locationArray: MutableList<Location> = ArrayList()

    // Increments for every taken step since button click
    private var totalStepsTaken = 0
    // Increments for every taken step since first location update
    private var stepsRegistered = 0

    // textViews that are called more than once in code
    private lateinit var stepstextView : TextView
    private lateinit var resulttextView: TextView

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

        // Register listener for step detector updates
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager!!.registerListener(
            this,
            sensorManager!!.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR),
            SensorManager.SENSOR_DELAY_FASTEST
        )

        // Make textView scrollable for event logging
        val logtextView: TextView = findViewById(R.id.errorlogs)
        logtextView.movementMethod = ScrollingMovementMethod()

        stepstextView = findViewById(R.id.takensteps)
        resulttextView = findViewById(R.id.calculation_result)

        val locationupdatestextView: TextView = findViewById(R.id.numberoflocationupdates)
        locationupdatestextView.text = getString(R.string.app_loc_updates, locationUpdatesUntilBreak.toString())

        // Set sound file that plays when recording is stopped
        mediaPlayer = MediaPlayer.create(this, R.raw.success)

        // This part initiates the recording
        val initButton: Button = findViewById(R.id.init_button)
        initButton.setOnClickListener {
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
                Toast.makeText(this, "Start walking!", Toast.LENGTH_SHORT).show()
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
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            totalStepsTaken += 1
            stepstextView.text = totalStepsTaken.toString()
            if (appIsRunning) {
                stepsRegistered += 1
            }
        }
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
        // Adds to log if new while in use location is added to SharedPreferences.
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
        if (permissions.contains("ACCESS_FINE_LOCATION")) {
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
                            ?: renderLog("Service Not Bound")

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
    }

    /**
     * Calculation using known body proportions
     */
    private fun calculateResult() {
        val distanceInMeters = locationArray.last().distanceTo(locationArray[0])
        val strideLength = distanceInMeters / stepsRegistered

        //TODO: Replace with regression function and time integration
        val heightMin = (strideLength / 0.39).toInt().toString()
        val heightMax = (strideLength / 0.46).toInt().toString()
        val heightAvg = (strideLength / 0.42).toInt().toString()

        renderToScreen(distanceInMeters.toInt().toString(), heightMin, heightAvg, heightMax)
    }

    private fun renderToScreen(dist:String, minHeight:String, avgHeight:String, maxHeight:String) {
        resulttextView.text = getString(R.string.result_body,dist,minHeight,avgHeight,maxHeight)
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
                if (totalStepsTaken > 0) {
                    locationArray.add(location)
                    if (locationUpdatesReceived == 0) {
                        appIsRunning = true
                    }
                    if (locationUpdatesReceived == locationUpdatesUntilBreak) {
                        appIsRunning = false
                        calculateResult()
                        mediaPlayer?.start()
                    }
                    locationUpdatesReceived += 1
                    renderLog("(" + location.latitude.toString() + location.longitude.toString() + ")")
                }
            }
        }
    }
}

