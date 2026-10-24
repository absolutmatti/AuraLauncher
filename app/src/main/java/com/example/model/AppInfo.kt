package com.example.model

import android.graphics.drawable.Drawable

/**
 * Repräsentiert eine auf dem Android TV installierte Anwendung.
 *
 * @property id Eindeutige Kennung (typischerweise der Paketname der App).
 * @property packageName Der Android-Paketname (z. B. "com.netflix.ninja").
 * @property activityName Der Name der Start-Activity (z. B. "com.netflix.ninja.MainActivity").
 * @property label Der im Launcher sichtbare Anzeigename der App.
 * @property bannerDrawable Optionales horizontales TV-Banner (16:9), bevorzugt für Leanback UI.
 * @property iconDrawable Standard-App-Icon als Fallback, falls kein Leanback-Banner existiert.
 * @property customIconPath Lokaler Dateipfad zu einem vom Nutzer ausgewählten benutzerdefinierten Icon.
 * @property isFavorite Gibt an, ob die App in der Favoriten-Reihe angezeigt werden soll.
 * @property isSystemApp Gibt an, ob es sich um eine vorinstallierte Systemanwendung handelt.
 */
data class AppInfo(
    val id: String,
    val packageName: String,
    val activityName: String,
    val label: String,
    val bannerDrawable: Drawable? = null,
    val iconDrawable: Drawable? = null,
    val customIconPath: String? = null,
    var isFavorite: Boolean = false,
    val isSystemApp: Boolean = false
) {
    /**
     * Ermittelt die beste visuelle Darstellung für die Leanback-Karte:
     * Bevorzugt wird ein TV-Banner, ansonsten das normale Icon.
     */
    fun getDisplayDrawable(): Drawable? {
        return bannerDrawable ?: iconDrawable
    }
}
