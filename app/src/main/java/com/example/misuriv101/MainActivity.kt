package com.example.misuriv101
import android.Manifest
import android.content.Context
import android.content.SharedPreferences
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity(), LocationListener, SensorEventListener {
    private lateinit var locationManager: LocationManager
    private val locationPermissionCode = 2

    var currentLat:Double = 0.0;
    var currentLong:Double = 0.0;
    var stepsUntilBreak = 30f;
    var stepCounterRunning = false
    var sensorManager:SensorManager? = null
    var stepsonInit = 0f;
    var stepsUntilNow = 0f;
    var firstStepTaken = false;
    var prefs: LocationPrefs? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        prefs = LocationPrefs(this)
        val Latitude = prefs!!.Latitude
        val Longitude = prefs!!.Longitude

        val button: Button = findViewById(R.id.init_button)
        button.setOnClickListener {
            RenderLog("Button clicked")
            getLocation()
            healthPermissions()
        }
    }
    fun InitCalculate() {
        prefs!!.Latitude = currentLat
        prefs!!.Longitude = currentLong

        RenderLog("Started measuring steps")
        Toast.makeText(this, "Step logging initiated", Toast.LENGTH_SHORT).show()
    }
    fun Quitcalculate() {
        stepCounterRunning = false
        RenderLog("Stopped measuring steps")

        val endLocation =
            Location("")
        endLocation.latitude = currentLat
        endLocation.longitude = currentLong
        RenderLog("Logged end location")
        val startLocation =
            Location("")
        startLocation.latitude = prefs!!.Latitude
        startLocation.longitude = prefs!!.Longitude
        RenderLog("Got start location")

        val distanceInMeters = endLocation.distanceTo(startLocation)
        RenderLog("Calculated distance")

        val strideLength = distanceInMeters / stepsUntilNow
        val heightMin = strideLength / 0.39
        val heightMax = strideLength / 0.46
        val heightAvg = strideLength / 0.42
        RenderLog("Calculated proportions")

        RenderToScreen(heightMin,heightMax,heightAvg)
    }

    fun RenderToScreen(min: Double, max: Double, avg: Double) {
        Toast.makeText(this, "Step logging stopped", Toast.LENGTH_SHORT).show()

        val textView: TextView = findViewById(R.id.calculation_result) as TextView
        textView.text = "I calculated your height to be between ${min}cm and ${max}cm. My guess is ${avg}cm."
    }
    fun RenderLog(msg: String) {
        val errors: TextView = findViewById(R.id.errorlogs) as TextView
        errors.append(" | $msg")
    }

    override fun onResume() {
        super.onResume()
        var stepsSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepsSensor == null) {
            Toast.makeText(this, "No Step Counter Sensor", Toast.LENGTH_SHORT).show()
        } else {
            sensorManager?.registerListener(this, stepsSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    override fun onPause() {
        super.onPause()
        stepCounterRunning = false
        stepsUntilNow = 0f
        stepsonInit = 0f
        firstStepTaken = false
        sensorManager?.unregisterListener(this)
    }

    private fun healthPermissions() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), locationPermissionCode)
        }
        RenderLog("Health data granted")
        stepCounterRunning = true
        Toast.makeText(this, "Please take 5 steps to initiate", Toast.LENGTH_SHORT).show()
    }

    private fun getLocation() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionCode)
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0.1f, this)
        RenderLog("Location data granted")
    }

    override fun onLocationChanged(location: Location) {
        currentLat = location.latitude
        currentLong = location.longitude
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
            }
            else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (stepCounterRunning) {
            // Set initial steps value
            if (!firstStepTaken) {
                firstStepTaken = true
                stepsonInit = event.values[0]
                RenderLog("Initial steps value set")
            }
            // Calcuate steps taken since init
            val newSteps = event.values[0]
            stepsUntilNow = newSteps.toInt() - stepsonInit

            //Init app
            if (stepsUntilNow >= 5f) {
                RenderLog("Initialized calculations")
                InitCalculate()
            }
            if (stepsUntilNow >= stepsUntilBreak + 5f) {
                RenderLog("Stopped calculation")
                Quitcalculate()
            }
        }
    }

    class LocationPrefs (context: Context) {
        val prefsFileName = "com.misuriv101.location.init"
        val latPrefName = "latitude"
        val lonPrefName = "longitude"
        var prefs: SharedPreferences = context.getSharedPreferences(prefsFileName, 0)

        var Latitude: Double
            get() = prefs.getLong(latPrefName, 0L).toDouble()
            set(value) = prefs.edit().putLong(latPrefName, value.toLong()).apply()
        var Longitude: Double
            get() = prefs.getLong(lonPrefName, 0L).toDouble()
            set(value) = prefs.edit().putLong(lonPrefName, value.toLong()).apply()
    }
}
