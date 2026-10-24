package com.example.model

/**
 * Modell für die aktuellen Wetterdaten, die in der Hero-Karte angezeigt werden.
 *
 * @property temperatureCelsius Gemessene Temperatur in Grad Celsius.
 * @property conditionText Textuelle Beschreibung des Wetterzustands (z. B. "Leicht bewölkt").
 * @property cityName Name des erkannten oder konfigurierten Standorts.
 * @property iconCode Wetter-Symbolcode der API (z. B. "01d" für Sonnenschein).
 * @property isLoaded Zeigt an, ob gültige Live-Daten geladen werden konnten.
 * @property errorMessage Fehlermeldung bei Problemen mit Netzwerk oder Berechtigungen.
 */
data class WeatherInfo(
    val temperatureCelsius: Double = 0.0,
    val conditionText: String = "",
    val cityName: String = "",
    val iconCode: String? = null,
    val isLoaded: Boolean = false,
    val errorMessage: String? = null
) {
    /**
     * Formatiert die Temperatur gerundet mit Einheitszeichen für die TV-Anzeige.
     */
    fun formattedTemperature(): String {
        return "${Math.round(temperatureCelsius)}°C"
    }
}
