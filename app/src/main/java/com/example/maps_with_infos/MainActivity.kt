
/*package com.example.maps_with_infos

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.maps_with_infos.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlin.random.Random

class MainActivity : AppCompatActivity(), OnMapReadyCallback, SensorEventListener {

    private lateinit var binding: ActivityMapsBinding
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastUpdate = 0L

    private val infoList = listOf(
        "Burası çok güzel bir yer!",
        "Tarihi bir öneme sahip.",
        "Ünlü bir simge burada bulunuyor.",
        "Burası çok popüler bir destinasyon.",
        "Doğal güzellikleriyle ünlü."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapContainer) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    mMap.addMarker(MarkerOptions().position(userLatLng).title("Ben buradayım"))
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 12f))

                    // Şehir bilgisi al
                    getCityFromLocation(it.latitude, it.longitude) { city ->
                        fetchCityInfo(city)
                    }
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onMapReady(mMap)
        }
    }


    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val curTime = System.currentTimeMillis()
        if (curTime - lastUpdate > 500) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val delta = Math.abs(x + y + z - lastX - lastY - lastZ)
            if (delta > 12) showRandomInfo()
            lastX = x
            lastY = y
            lastZ = z
            lastUpdate = curTime
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun showRandomInfo() {
        val info = infoList[Random.nextInt(infoList.size)]
        binding.infoText.text = info
    }
    private fun getCityFromLocation(lat: Double, lon: Double, callback: (String) -> Unit) {
        // Basit çözüm: Google Maps Geocoding API veya Android Geocoder
        val geocoder = android.location.Geocoder(this)
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        if (addresses != null && addresses.isNotEmpty()) {
            val city = addresses[0].locality ?: addresses[0].subAdminArea ?: "Unknown"
            callback(city)
        } else {
            callback("Unknown")
        }
    }

    private fun fetchCityInfo(city: String) {
        binding.infoText.text = "Loading information about $city..."

        RetrofitClient.wikiApi.getCityInfo(titles = city).enqueue(object : retrofit2.Callback<WikiResponse> {
            override fun onResponse(call: retrofit2.Call<WikiResponse>, response: retrofit2.Response<WikiResponse>) {
                if (response.isSuccessful) {
                    val pages = response.body()?.query?.pages
                    if (pages != null && pages.isNotEmpty()) {
                        val page = pages.values.firstOrNull {
                            it?.pageid != null && it.pageid != -1 && !it.extract.isNullOrBlank()
                        }
                        val info = page?.extract
                        if (!info.isNullOrBlank()) {
                            binding.infoText.text = truncateText(info.trim(), 350)
                        } else {
                            binding.infoText.text = "Bilgi bulunamadı"
                        }
                    } else {
                        binding.infoText.text = "Bilgi bulunamadı"
                    }
                } else {
                    binding.infoText.text = "Bilgi bulunamadı: ${response.code()}"
                }
            }

            override fun onFailure(call: retrofit2.Call<WikiResponse>, t: Throwable) {
                binding.infoText.text = "Bilgi alınamadı: ${t.message}"
            }
        })
    }

    /**
     * Truncates text to a maximum length, trying to cut at sentence boundaries
     */
    private fun truncateText(text: String, maxLength: Int): String {
        if (text.length <= maxLength) {
            return text
        }

        // Try to find a sentence boundary (period, exclamation, question mark) near the max length
        val truncated = text.substring(0, maxLength)
        val lastSentenceEnd = maxOf(
            truncated.lastIndexOf(". "),
            truncated.lastIndexOf("! "),
            truncated.lastIndexOf("? ")
        )

        return if (lastSentenceEnd > maxLength * 0.7) {
            // If we found a sentence boundary in the last 30% of the text, use it
            text.substring(0, lastSentenceEnd + 1) + "..."
        } else {
            // Otherwise, just cut at word boundary
            val lastSpace = truncated.lastIndexOf(" ")
            if (lastSpace > maxLength * 0.8) {
                text.substring(0, lastSpace) + "..."
            } else {
                truncated + "..."
            }
        }
    }

}*/
