package com.example.misuriv101
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.text.method.ScrollingMovementMethod
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi


class MainActivity : AppCompatActivity(), SensorEventListener {
    private var locationManager : LocationManager? = null
    private var startLat: Double? = null
    private var startLong: Double? = null
    private var currentLat: Double? = null
    private var currentLong: Double? = null
    private val startoffset = 5
    private val stepsUntilBreak = 30
    private var stepstaken = 0
    private var stepCounterRunning = false
    private var sensorManager:SensorManager? = null
    private fun renderLog(msg: String) {
        val errors: TextView = findViewById(R.id.errorlogs)
        errors.append(System.getProperty("line.separator")!! + msg)
    }

    // When app is first launched
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Get health permissions
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 2)
        }
        renderLog("Health permission granted")

        // Get location permissions
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 2)
        }
        renderLog("Location permission granted")

        // Create persistent LocationManager reference
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?
        
        // Register sensor manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager!!.registerListener(this, sensorManager!!.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR), SensorManager.SENSOR_DELAY_FASTEST)

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
            // Activate step counter
            stepCounterRunning = true
            Toast.makeText(this, "Please take 5 steps to initiate", Toast.LENGTH_SHORT).show()

            // Request location updates
            try {
                locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationListener)
                renderLog("Requested location updates")
            } catch(ex: SecurityException) {
                renderLog("ERROR: no location available")
            }

            renderLog("Button clicked")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR && stepCounterRunning) {
            stepstaken += 1
            if (stepstaken == startoffset) { 
                // Set start location
                val latsnapshot = currentLat
                startLat = latsnapshot
                val longsnapshot = currentLong
                startLong = longsnapshot

                renderLog("Start location set to lat:" + startLat.toString() + " long:" + startLong.toString())
            }
            if (stepstaken == startoffset + stepsUntilBreak) {
                calculate()
            }
        }
    }

    // This function is called once the steps surpass stepsUntilBreak + 5
    private fun calculate() {
        // Stop step counter
        stepCounterRunning = false
        renderLog("Step counter stopped")

        // Create location objects for start and end location
        val endLocation = Location("")
        endLocation.latitude = currentLat!!
        endLocation.longitude = currentLong!!
        val startLocation = Location("")
        startLocation.latitude = startLat!!
        startLocation.longitude = startLong!!

        // Calculate walked distance
        // TODO: Customize this so that user can walk curved distances
        val distanceInMeters = endLocation.distanceTo(startLocation)
        renderLog("Calculated distance")

        val strideLength = distanceInMeters / stepsUntilBreak
        val heightMin = strideLength / 0.39
        val heightMax = strideLength / 0.46
        val heightAvg = strideLength / 0.42
        renderLog("Calculated proportions")

        renderToScreen(heightMin,heightMax,heightAvg)
    }

    @SuppressLint("SetTextI18n")
    fun renderToScreen(min: Double, max: Double, avg: Double) {
        val textView: TextView = findViewById(R.id.calculation_result)
        textView.text = "I calculated your height to be between ${min}cm and ${max}cm. My guess is ${avg}cm."
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onResume() {
        super.onResume()
        val stepsSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        if (stepsSensor == null) {
            Toast.makeText(this, "No Step Detector Sensor", Toast.LENGTH_SHORT).show()
        } else {
            sensorManager?.registerListener(this, stepsSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
    }

    override fun onPause() {
        super.onPause()
        stepCounterRunning = false
    }

    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLat = location.latitude
            currentLong = location.longitude
        }
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
}

