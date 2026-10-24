package com.example.data.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.model.WeatherInfo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Ruft aktuelle Live-Wetterdaten von OpenWeatherMap ab.
 * Beinhaltet Standortabfrage per FusedLocationProviderClient und sichere Fehlerbehandlung.
 */
class WeatherRepository(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val weatherApi: WeatherApiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WeatherApiService::class.java)
    }

    /**
     * Ermittelt die aktuellen Wetterdaten.
     * Wenn der API-Key fehlt, kein Internet verfügbar ist oder der Standort nicht
     * freigegeben wurde, stürzt die App niemals ab, sondern liefert einen neutralen Status.
     */
    suspend fun fetchCurrentWeather(): WeatherInfo = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.OPENWEATHERMAP_API_KEY
        } catch (_: Exception) {
            ""
        }

        // 1. Prüfen, ob ein gültiger API-Schlüssel hinterlegt wurde
        if (apiKey.isNullOrBlank() || apiKey == "OPENWEATHERMAP_API_KEY_PLACEHOLDER" || apiKey == "MY_OPENWEATHERMAP_KEY") {
            return@withContext WeatherInfo(
                temperatureCelsius = 20.0,
                conditionText = "API-Key nicht hinterlegt",
                cityName = "Wetter-Status",
                iconCode = null,
                isLoaded = false,
                errorMessage = "Trage deinen OpenWeather-Key in local.properties ein"
            )
        }

        // 2. Koordinaten ermitteln (Standort oder Fallback)
        val location = getLastKnownLocation()
        val lat = location?.latitude ?: 52.5200 // Fallback: Berlin Breitengrad
        val lon = location?.longitude ?: 13.4050 // Fallback: Berlin Längengrad

        // 3. OpenWeatherMap API abfragen
        try {
            val response = weatherApi.getCurrentWeather(
                latitude = lat,
                longitude = lon,
                apiKey = apiKey
            )

            val temp = response.main?.temp ?: 0.0
            val desc = response.weatherList?.firstOrNull()?.description
                ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                ?: "Klar"
            val icon = response.weatherList?.firstOrNull()?.icon
            val city = response.cityName ?: "Lokal"

            WeatherInfo(
                temperatureCelsius = temp,
                conditionText = desc,
                cityName = city,
                iconCode = icon,
                isLoaded = true
            )
        } catch (e: Exception) {
            WeatherInfo(
                temperatureCelsius = 0.0,
                conditionText = "Offline / Kein Empfang",
                cityName = "Lokal",
                iconCode = null,
                isLoaded = false,
                errorMessage = e.localizedMessage
            )
        }
    }

    /**
     * Ruft den letzten bekannten Standort ab, falls die Berechtigung erteilt wurde.
     */
    private suspend fun getLastKnownLocation(): Location? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return null

        return suspendCancellableCoroutine { continuation ->
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        continuation.resume(location)
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            } catch (_: SecurityException) {
                continuation.resume(null)
            }
        }
    }
}
