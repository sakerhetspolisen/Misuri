package com.example.misuriv101

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34

@RequiresApi(Build.VERSION_CODES.M)
class MainActivity : AppCompatActivity(), SensorEventListener {

    private var foregroundOnlyLocationService: ForegroundOnlyLocationService? = null
    private lateinit var foregroundOnlyBroadcastReceiver: ForegroundOnlyBroadcastReceiver

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

        // Get health permissions
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                2
            )
        }
        renderLog("Health permission granted")

        // Get Location permission
        if (!foregroundPermissionApproved()) {
            requestForegroundPermissions()
        }
        renderLog("Location permission granted")

        // Register sensor manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager!!.registerListener(
            this,
            sensorManager!!.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR),
            SensorManager.SENSOR_DELAY_FASTEST
        )
        renderLog("Registered sensor manager")


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
            if (!stepCounterRunning) {
                renderLog("Button clicked")
                                                                                                
                // Activate step counter                                                          
                stepCounterRunning = true                                                         
                Toast.makeText(this, "Please take 5 steps to initiate", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
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
                // Request location updates
                foregroundOnlyLocationService?.subscribeToLocationUpdates()
                    ?: renderLog("Location service not found")
            }
            if (stepstaken == startoffset + stepsUntilBreak) {
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
        textView.text = "Startlocation: (${startla},${startlo}), Stoplocation: (${endla}, ${endlo}), Distance: ${dist}"
    }

    override fun onStop() {
        foregroundOnlyLocationService?.unsubscribeToLocationUpdates()
        super.onStop()
    }

    private fun foregroundPermissionApproved(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun requestForegroundPermissions() {
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
            )
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

                else -> {
                    renderLog("Location permission denied")
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

