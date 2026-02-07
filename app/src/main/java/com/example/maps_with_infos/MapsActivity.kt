package com.example.maps_with_infos

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.*
import android.location.Geocoder
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.maps_with_infos.databinding.ActivityMapsBinding
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import retrofit2.*
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, SensorEventListener, TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMapsBinding
    private lateinit var googleMap: GoogleMap
    private var currentLatLng: LatLng? = null
    private lateinit var sensorManager: SensorManager
    private var lastShake = 0L
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var currentCityInfo: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.mapContainer, mapFragment)
            .commit()
        mapFragment.getMapAsync(this)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(this, this)
        android.util.Log.d("MapsActivity", "TTS initialization started")

        // Setup search functionality
        setupSearch()
    }

    private fun setupSearch() {
        binding.searchButton.setOnClickListener {
            performSearch()
        }

        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performSearch() {
        val cityName = binding.searchEditText.text.toString().trim()
        if (cityName.isNotEmpty()) {
            // Hide keyboard
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)

            searchCityByName(cityName)
        } else {
            android.widget.Toast.makeText(this, "Please enter a city name", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        } else {
            googleMap.isMyLocationEnabled = true
            googleMap.setOnMyLocationChangeListener {
                currentLatLng = LatLng(it.latitude, it.longitude)
                // Uygulama ilk açıldığında bilgiyi otomatik yükle
                val currentText = binding.infoText.text.toString()
                if (currentText == "Information will appear here..." ||
                    currentText == "Bilgi buraya gelecek" ||
                    currentText.isEmpty()) {
                    loadCityInfo()
                }
            }
        }
    }

    private fun searchCityByName(cityName: String) {
        val geocoder = Geocoder(this, Locale.ENGLISH)

        runOnUiThread {
            binding.infoText.text = "Searching for $cityName..."
        }

        try {
            // Use geocoding to find the city coordinates
            val addresses = geocoder.getFromLocationName(cityName, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                val latLng = LatLng(address.latitude, address.longitude)
                val foundCity = address.locality ?: address.adminArea ?: cityName

                // Update map
                googleMap.clear()
                googleMap.addMarker(MarkerOptions().position(latLng).title(foundCity))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 11f))

                // Update current location for future reference
                currentLatLng = latLng

                // Fetch Wikipedia info
                fetchCityInfoFromName(foundCity)
            } else {
                // If geocoding fails, try searching Wikipedia directly with the entered name
                runOnUiThread {
                    binding.infoText.text = "City not found via geocoding. Searching Wikipedia for: $cityName..."
                }
                fetchCityInfoFromName(cityName)
            }
        } catch (e: Exception) {
            android.util.Log.e("MapsActivity", "Error searching city: ${e.message}")
            // If geocoding fails, try Wikipedia search directly
            fetchCityInfoFromName(cityName)
        }
    }

    private fun fetchCityInfoFromName(cityName: String) {
        // Show loading message
        runOnUiThread {
            binding.infoText.text = "Loading information about $cityName..."
            android.util.Log.d("MapsActivity", "Loading info for city: $cityName")
        }

        RetrofitClient.wikiApi.getCityInfo(titles = cityName).enqueue(object : Callback<WikiResponse> {
            override fun onResponse(call: Call<WikiResponse>, response: Response<WikiResponse>) {
                if (response.isSuccessful) {
                    val body = response.body()
                    val pagesMap = body?.query?.pages

                    if (pagesMap != null && pagesMap.isNotEmpty()) {
                        // Find the first page with valid extract
                        val page = pagesMap.values.firstOrNull {
                            it?.pageid != null && it.pageid != -1 && !it.extract.isNullOrBlank()
                        }

                        val info = page?.extract

                        if (!info.isNullOrBlank()) {
                            val truncatedInfo = truncateText(info.trim(), 350)
                            currentCityInfo = truncatedInfo

                            // Try to get coordinates from Wikipedia page title and update map
                            val pageTitle = page?.title ?: cityName
                            updateMapForCity(pageTitle)

                            runOnUiThread {
                                binding.infoText.text = truncatedInfo
                                android.util.Log.d("MapsActivity", "Info text set: ${truncatedInfo.take(50)}...")
                            }
                            playShakeAnimation()
                        } else {
                            // Try to get any page, even without extract
                            val anyPage = pagesMap.values.firstOrNull { it?.pageid != null && it.pageid != -1 }
                            val errorMessage = if (anyPage != null) {
                                "Information found for ${anyPage.title}, but summary is not available. Try shaking to search again!"
                            } else {
                                "No information found for $cityName. Try shaking to search again!"
                            }
                            currentCityInfo = errorMessage
                            runOnUiThread {
                                binding.infoText.text = errorMessage
                            }
                        }
                    } else {
                        val errorMessage = "No information found for $cityName. Try shaking to search again!"
                        currentCityInfo = errorMessage
                        runOnUiThread {
                            binding.infoText.text = errorMessage
                        }
                    }

                    // Debug log
                    android.util.Log.d("WikipediaAPI", "URL: ${response.raw().request.url}")
                    android.util.Log.d("WikipediaAPI", "Response: ${response.body()}")
                } else {
                    val errorMessage = "Error: ${response.code()} - ${response.message()}"
                    currentCityInfo = errorMessage
                    runOnUiThread {
                        binding.infoText.text = errorMessage
                    }
                    android.util.Log.e("WikipediaAPI", "Error response: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<WikiResponse>, t: Throwable) {        // Handle request failure
                val errorMessage = "Connection Error: ${t.localizedMessage}"
                currentCityInfo = errorMessage
                runOnUiThread {
                    binding.infoText.text = errorMessage
                }
                android.util.Log.e("WikipediaAPI", "Request failed", t)
            }
        })
    }

    private fun updateMapForCity(cityName: String) {        // Try to get coordinates from Wikipedia page title
        // Try to geocode the city name to update the map
        val geocoder = Geocoder(this, Locale.ENGLISH)
        try {
            val addresses = geocoder.getFromLocationName(cityName, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                val latLng = LatLng(address.latitude, address.longitude)
                googleMap.clear()
                googleMap.addMarker(MarkerOptions().position(latLng).title(cityName))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 11f))
                currentLatLng = latLng
            }
        } catch (e: Exception) {
            android.util.Log.w("MapsActivity", "Could not update map for city: ${e.message}")
        }
    }

    private fun loadCityInfo() {
        val latLng = currentLatLng ?: return
        val geocoder = Geocoder(this, Locale.ENGLISH)

        try {
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            val address = addresses?.firstOrNull()
            val city = address?.locality ?: address?.adminArea ?: return

            googleMap.clear()
            googleMap.addMarker(MarkerOptions().position(latLng).title(city))
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 11f))

            // Show loading message
            runOnUiThread {
                binding.infoText.text = "Loading information about $city..."
                android.util.Log.d("MapsActivity", "Loading info for city: $city")
            }

            RetrofitClient.wikiApi.getCityInfo(titles = city).enqueue(object : Callback<WikiResponse> {
                override fun onResponse(call: Call<WikiResponse>, response: Response<WikiResponse>) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        val pagesMap = body?.query?.pages

                        if (pagesMap != null && pagesMap.isNotEmpty()) {
                            // Find the first page with valid extract
                            val page = pagesMap.values.firstOrNull {
                                it?.pageid != null && it.pageid != -1 && !it.extract.isNullOrBlank()
                            }

                            val info = page?.extract

                            if (!info.isNullOrBlank()) {
                                val truncatedInfo = truncateText(info.trim(), 350)
                                currentCityInfo = truncatedInfo
                                runOnUiThread {
                                    binding.infoText.text = truncatedInfo
                                    android.util.Log.d("MapsActivity", "Info text set: ${truncatedInfo.take(50)}...")
                                }
                                playShakeAnimation()
                            } else {
                                // Try to get any page, even without extract
                                val anyPage = pagesMap.values.firstOrNull { it?.pageid != null && it.pageid != -1 }
                                val errorMessage = if (anyPage != null) {
                                    "Information found for ${anyPage.title}, but summary is not available. Try shaking to search again!"
                                } else {
                                    "No information found for $city. Try shaking to search again!"
                                }
                                currentCityInfo = errorMessage
                                runOnUiThread {
                                    binding.infoText.text = errorMessage
                                }
                            }
                        } else {
                            val errorMessage = "No information found for $city. Try shaking to search again!"
                            currentCityInfo = errorMessage
                            runOnUiThread {
                                binding.infoText.text = errorMessage
                            }
                        }

                        // Debug log
                        android.util.Log.d("WikipediaAPI", "URL: ${response.raw().request.url}")
                        android.util.Log.d("WikipediaAPI", "Response: ${response.body()}")
                    } else {
                        val errorMessage = "Error: ${response.code()} - ${response.message()}"
                        currentCityInfo = errorMessage
                        runOnUiThread {
                            binding.infoText.text = errorMessage
                        }
                        android.util.Log.e("WikipediaAPI", "Error response: ${response.errorBody()?.string()}")
                    }
                }

                override fun onFailure(call: Call<WikiResponse>, t: Throwable) {
                    val errorMessage = "Connection Error: ${t.localizedMessage}"
                    currentCityInfo = errorMessage
                    runOnUiThread {
                        binding.infoText.text = errorMessage
                    }
                    android.util.Log.e("WikipediaAPI", "Request failed", t)
                }
            })
        } catch (e: Exception) {
            val errorMessage = "Error: ${e.localizedMessage}"
            currentCityInfo = errorMessage
            binding.infoText.text = errorMessage
        }
    }

    private fun playShakeAnimation() {
        binding.infoText.apply {
            translationX = 0f // Reset translation
            animate()
                .translationXBy(20f)
                .setDuration(80)
                .withEndAction {
                    animate().translationXBy(-40f).setDuration(80).withEndAction {
                        animate().translationXBy(20f).setDuration(80).start()
                    }.start()
                }
                .start()
        }
    }

    override fun onResume() {
        super.onResume()
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Yerçekimini çıkararak gerçek ivmeyi hesapla
        val acceleration = Math.sqrt((x * x + y * y + z * z).toDouble()) - SensorManager.GRAVITY_EARTH

        if (acceleration > 12) { // Hassasiyet eşiği
            val now = System.currentTimeMillis()
            if (now - lastShake > 2000) { // 2 saniye bekleme süresi
                lastShake = now
                android.util.Log.d("MapsActivity", "Shake detected, reading city info aloud")
                readCityInfoAloud()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.ENGLISH)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                android.util.Log.e("TTS", "Language not supported")
                isTtsReady = false
            } else {
                isTtsReady = true
                android.util.Log.d("TTS", "Text-to-Speech initialized successfully")
            }
        } else {
            android.util.Log.e("TTS", "Text-to-Speech initialization failed with status: $status")
            isTtsReady = false
        }
    }

    private fun readCityInfoAloud() {
        val infoToRead = currentCityInfo.ifBlank {
            binding.infoText.text.toString().takeIf {
                it != "Information will appear here..." &&
                        it != "Bilgi buraya gelecek" &&
                        !it.startsWith("Loading") &&
                        !it.startsWith("Error") &&
                        !it.startsWith("Connection Error") &&
                        !it.startsWith("No information found")
            }
        }

        android.util.Log.d("TTS", "Attempting to read. isTtsReady: $isTtsReady, infoToRead: ${infoToRead?.take(50)}...")

        if (infoToRead.isNullOrBlank()) {
            android.util.Log.d("TTS", "No valid info available, reloading city info")
            loadCityInfo()
            return
        }

        if (isTtsReady && textToSpeech != null) {
            // Stop any ongoing speech
            textToSpeech?.stop()
            // Read the city information
            val result = textToSpeech?.speak(infoToRead, TextToSpeech.QUEUE_FLUSH, null, null)
            if (result == TextToSpeech.ERROR) {
                android.util.Log.e("TTS", "Error speaking text")
            } else {
                android.util.Log.d("TTS", "Successfully started reading city info aloud")
            }
        } else {
            android.util.Log.w("TTS", "Text-to-Speech not ready (isTtsReady: $isTtsReady), reloading info instead")
            loadCityInfo()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release Text-to-Speech resources
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        android.util.Log.d("TTS", "TTS resources released")
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
}