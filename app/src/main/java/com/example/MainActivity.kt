package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.data.AppPreferences
import com.example.data.AppRepository
import com.example.data.weather.WeatherRepository
import com.example.model.AppInfo
import com.example.model.WeatherInfo
import com.example.ui.MainFragment
import com.example.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

/**
 * Haupt-Activity des TV Launchers für Philips Google TV.
 * Verwaltet die Hero-Kachel (Uhrzeit, Datum, Wetter, Schnellstart) und bindet
 * das Leanback MainFragment für die Zeilenanzeige ein.
 */
class MainActivity : FragmentActivity() {

    private lateinit var appRepository: AppRepository
    private lateinit var appPreferences: AppPreferences
    private lateinit var weatherRepository: WeatherRepository

    private var currentTargetAppForIcon: AppInfo? = null

    // Hero-Karten Views
    private lateinit var heroRecentAppButton: LinearLayout
    private lateinit var heroRecentAppIcon: ImageView
    private lateinit var heroRecentAppName: TextView
    private lateinit var heroRecentAppSubtext: TextView
    private lateinit var heroLeftColumn: LinearLayout
    private lateinit var heroSettingsButton: FrameLayout
    private lateinit var heroWeatherTemp: TextView
    private lateinit var heroWeatherDesc: TextView
    private lateinit var heroWeatherIcon: ImageView

    private var mostRecentApp: AppInfo? = null

    // Launcher zur Bildauswahl für benutzerdefinierte Icons
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val target = currentTargetAppForIcon
        if (uri != null && target != null) {
            lifecycleScope.launch {
                val success = appRepository.saveCustomIcon(target.packageName, uri)
                if (success) {
                    Toast.makeText(this@MainActivity, "Icon für ${target.label} aktualisiert", Toast.LENGTH_SHORT).show()
                    val fragment = supportFragmentManager.findFragmentById(R.id.rowsContainer) as? MainFragment
                    fragment?.loadData()
                } else {
                    Toast.makeText(this@MainActivity, "Fehler beim Speichern des Icons", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Berechtigungsabfrage für Standort (für lokale Wetterdaten)
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Nach Berechtigungsentscheidung Wetter abrufen
        loadWeather()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appRepository = AppRepository(this)
        appPreferences = AppPreferences(this)
        weatherRepository = WeatherRepository(this)

        initHeroViews()
        setupMainFragment()
        checkLocationPermissionsAndLoadWeather()
    }

    override fun onResume() {
        super.onResume()
        updateHeroRecentApp()
    }

    /**
     * Initialisiert die Views und D-Pad Interaktionen der Hero-Karte.
     */
    private fun initHeroViews() {
        heroLeftColumn = findViewById(R.id.heroLeftColumn)
        heroRecentAppButton = findViewById(R.id.heroRecentAppButton)
        heroRecentAppIcon = findViewById(R.id.heroRecentAppIcon)
        heroRecentAppName = findViewById(R.id.heroRecentAppName)
        heroRecentAppSubtext = findViewById(R.id.heroRecentAppSubtext)
        heroSettingsButton = findViewById(R.id.heroSettingsButton)

        heroWeatherTemp = findViewById(R.id.heroWeatherTemp)
        heroWeatherDesc = findViewById(R.id.heroWeatherDesc)
        heroWeatherIcon = findViewById(R.id.heroWeatherIcon)

        // Schnellstart der letzten App per Klick auf die Hero-Kachel
        heroRecentAppButton.setOnClickListener {
            val app = mostRecentApp
            if (app != null) {
                appRepository.launchApp(app)
            } else {
                Toast.makeText(this, "Keine vorherige App vorhanden", Toast.LENGTH_SHORT).show()
            }
        }

        // Klick auf das Einstellungs-Zahnrad in der Kopfzeile
        heroSettingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Fokus beim ersten Start standardmäßig auf die Hero-Karte legen
        heroRecentAppButton.requestFocus()
    }

    /**
     * Bindet das Leanback MainFragment in den unteren Bildschirmbereich ein.
     */
    private fun setupMainFragment() {
        var fragment = supportFragmentManager.findFragmentById(R.id.rowsContainer) as? MainFragment
        if (fragment == null) {
            fragment = MainFragment().apply {
                onRecentAppUpdated = { app ->
                    mostRecentApp = app
                    updateHeroRecentApp()
                }
                onChangeIconRequested = { app ->
                    currentTargetAppForIcon = app
                    pickImageLauncher.launch("image/*")
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.rowsContainer, fragment)
                .commit()
        }
    }

    /**
     * Aktualisiert die Vorschau der zuletzt genutzten App in der Hero-Karte.
     */
    private fun updateHeroRecentApp() {
        val mode = appPreferences.getRecentAppsMode()
        val showHero = mode == AppPreferences.RECENTS_MODE_HERO_ONLY || mode == AppPreferences.RECENTS_MODE_BOTH

        if (!showHero) {
            heroLeftColumn.visibility = View.INVISIBLE
            return
        }

        heroLeftColumn.visibility = View.VISIBLE
        val app = mostRecentApp
        if (app != null) {
            heroRecentAppName.text = app.label
            heroRecentAppSubtext.text = "Klicke zum Fortsetzen"
            val drawable = app.getDisplayDrawable()
            if (drawable != null) {
                heroRecentAppIcon.setImageDrawable(drawable)
            }
        } else {
            heroRecentAppName.text = getString(R.string.no_recent_app)
            heroRecentAppSubtext.text = "Starte eine App aus der Liste"
            heroRecentAppIcon.setImageResource(R.drawable.ic_app_default)
        }
    }

    /**
     * Prüft die Berechtigungen für Standortdaten und fordert diese bei Bedarf an.
     */
    private fun checkLocationPermissionsAndLoadWeather() {
        val hasCoarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCoarse) {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
            loadWeather()
        }
    }

    /**
     * Ruft asynchron die Live-Wetterdaten ab und aktualisiert die Hero-Karte.
     */
    private fun loadWeather() {
        lifecycleScope.launch {
            val weatherInfo: WeatherInfo = weatherRepository.fetchCurrentWeather()

            if (weatherInfo.isLoaded) {
                heroWeatherTemp.text = weatherInfo.formattedTemperature()
                heroWeatherDesc.text = "${weatherInfo.conditionText} (${weatherInfo.cityName})"
                heroWeatherIcon.setImageResource(R.drawable.ic_weather_placeholder)
            } else {
                heroWeatherTemp.text = "--°C"
                heroWeatherDesc.text = weatherInfo.conditionText.ifEmpty { getString(R.string.weather_unavailable) }
            }
        }
    }

    /**
     * Verhindert das Schließen des Launchers bei Druck auf die Zurück-Taste (TV Home-Verhalten).
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Fokus auf die Hero-Karte oder erste Reihe setzen statt die App zu beenden
        heroRecentAppButton.requestFocus()
    }
}
