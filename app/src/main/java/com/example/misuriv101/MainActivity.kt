package com.example.misuriv101

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar


@RequiresApi(Build.VERSION_CODES.M)
class MainActivity : AppCompatActivity(), SensorEventListener {
    private val startoffset = 5
    private var startlat : Double = 0.0
    private var startlong : Double = 0.0
    private var endlat : Double = 0.0
    private var endlong : Double = 0.0
    private var gotstartloc = false
    private val stepsUntilBreak = 30
    private var stepstaken = 0
    private var stepCounterRunning = false
    private var sensorManager:SensorManager? = null
    private fun renderLog(msg: String) {
        val errors: TextView = findViewById(R.id.errorlogs)
        errors.append(System.getProperty("line.separator")!! + msg)
    }
    private lateinit var binding: View
    // The Fused Location Provider provides access to location APIs.
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(applicationContext)
    }

    // Allows class to cancel the location request if it exits the activity.
    // Typically, you use one cancellation source per lifecycle.
    private var cancellationTokenSource = CancellationTokenSource()

    // If the user denied a previous permission request, but didn't check "Don't ask again", this
    // Snackbar provides an explanation for why user should approve, i.e., the additional rationale.
    private val fineLocationRationalSnackbar by lazy {
        Snackbar.make(
            binding,
            R.string.fine_location_permission_rationale,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.ok) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FINE_LOCATION_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    // When app is first launched
    @SuppressLint("CutPasteId")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = findViewById(R.id.activity_main)

        // Get health permissions
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                2
            )
        }
        renderLog("Health permission granted")

        if (!applicationContext.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissionWithRationale(
                Manifest.permission.ACCESS_FINE_LOCATION,
                REQUEST_FINE_LOCATION_PERMISSIONS_REQUEST_CODE,
                fineLocationRationalSnackbar
            )
        }
        renderLog("Location permission granted")

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

        // Bad way to set font
        val textView: TextView = findViewById(R.id.stepsLbl)
        val textView2: TextView = findViewById(R.id.height_title)
        val textView3: TextView = findViewById(R.id.calculation_result)
        val textView4: TextView = findViewById(R.id.errorlogs)
        val spacegrotesk: Typeface = Typeface.createFromAsset(this.assets, "fonts/spacegrotesk.ttf")
        textView.typeface = spacegrotesk
        textView2.typeface = spacegrotesk
        textView3.typeface = spacegrotesk
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR && stepCounterRunning) {
            stepstaken += 1
            if (stepstaken == startoffset) {
                // Request location updates
                try {
                    requestCurrentLocation()
                    renderLog("Requested current location")
                } catch (ex: SecurityException) {
                    renderLog("ERROR: no location available")
                }
            }
            if (stepstaken == startoffset + stepsUntilBreak) {
                try {
                    requestCurrentLocation()
                } catch (ex: SecurityException) {
                    renderLog("ERROR: no location available")
                }
                stepCounterRunning = false
                calculate()
            }
        }
    }

    // This function is called once the steps surpass stepsUntilBreak + 5
    private fun calculate() {

        // Create location objects for start and end location
        val endLocation = Location("")
        endLocation.latitude = endlat
        endLocation.longitude = endlong
        val startLocation = Location("")
        startLocation.latitude = startlat
        startLocation.longitude = startlong

        // Calculate walked distance
        // TODO: Customize this so that user can walk curved distances
        val distanceInMeters = endLocation.distanceTo(startLocation)
        renderLog("Calculated distance")

        val strideLength = distanceInMeters / stepsUntilBreak
        val heightMin = strideLength / 0.39
        val heightMax = strideLength / 0.46
        val heightAvg = strideLength / 0.42
        renderLog("Calculated proportions")

        renderToScreen(heightMin, heightMax, heightAvg)
    }

    @SuppressLint("SetTextI18n")
    fun renderToScreen(min: Double, max: Double, avg: Double) {
        val textView: TextView = findViewById(R.id.calculation_result)
        textView.text = "I calculated your height to be between ${min}cm and ${max}cm. My guess is ${avg}cm."
    }

    override fun onStop() {
        super.onStop()
        // Cancels location request (if in flight).
        cancellationTokenSource.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        renderLog("onRequestPermissionResult()")

        if (requestCode == REQUEST_FINE_LOCATION_PERMISSIONS_REQUEST_CODE) {
            when {
                grantResults.isEmpty() ->
                    // If user interaction was interrupted, the permission request
                    // is cancelled and you receive an empty array.
                    renderLog("User interaction was cancelled.")

                grantResults[0] == PackageManager.PERMISSION_GRANTED ->
                    Snackbar.make(
                        binding,
                        R.string.permission_approved_explanation,
                        Snackbar.LENGTH_LONG
                    )
                        .show()

                else -> {
                    Snackbar.make(
                        binding,
                        R.string.fine_permission_denied_explanation,
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
     * Gets current location.
     * Note: The code checks for permission before calling this method, that is, it's never called
     * from a method with a missing permission. Also, I include a second check with my extension
     * function in case devs just copy/paste this code.
     */
    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation() {
        renderLog("requestCurrentLocation()")
        if (applicationContext.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {

            // Returns a single current location fix on the device. Unlike getLastLocation() that
            // returns a cached location, this method could cause active location computation on the
            // device. A single fresh location will be returned if the device location can be
            // determined within reasonable time (tens of seconds), otherwise null will be returned.
            //
            // Both arguments are required.
            // PRIORITY type is self-explanatory. (Other options are PRIORITY_BALANCED_POWER_ACCURACY,
            // PRIORITY_LOW_POWER, and PRIORITY_NO_POWER.)
            // The second parameter, [CancellationToken] allows the activity to cancel the request
            // before completion.
            val currentLocationTask: Task<Location> = fusedLocationClient.getCurrentLocation(
                PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            )

            currentLocationTask.addOnCompleteListener { task: Task<Location> ->
                if (task.isSuccessful && task.result != null) {
                    if (!gotstartloc) {
                        startlat = task.result.latitude
                        startlong = task.result.longitude
                        gotstartloc = true
                        renderLog("Start location set")
                    } else {
                        endlat = task.result.latitude
                        endlong = task.result.longitude
                        renderLog("End location set")
                    }
                } else {
                    val exception = task.exception
                    renderLog("Location (failure): $exception")
                }
            }
        }
    }

    companion object {
        private const val REQUEST_FINE_LOCATION_PERMISSIONS_REQUEST_CODE = 34
    }
}

