# Philips Android TV Launcher (Phase 1 MVP)

Ein schlanker, maßgeschneiderter Leanback-Launcher für Philips Google TV (Android 14) mit D-Pad-optimierter Fernbedienungssteuerung, dynamischer App-Verwaltung, Hero-Karte mit Live-Wetter (OpenWeatherMap) und modularen TV-Einstellungen.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> Die folgenden Kernentscheidungen wurden in Phase 1 geklärt und sind im Plan fest verankert:

- **Wetterdienst:** OpenWeatherMap mit API-Key (konfigurierbar via `local.properties` / `.env` und `BuildConfig`) mit robuster Fehlerbehandlung und neutralem Fallback.
- **Akzentfarbe & Fokus-Glow:** Deep Purple (`#9C27B0`) mit 1,1x Skalierung, Kontur-Rahmen und weichem Leucht-Effekt.
- **Standardmodus für "Letzte Apps":** "Hero & Reihe" (Zuletzt genutzte App prominent links in der Hero-Karte + eigene "Zuletzt genutzt"-Reihe).
- **Architektur & Framework:** Klassische Android Views mit AndroidX Leanback (`RowsSupportFragment`, `ImageCardView`, `Presenter`) – **strikt ohne Jetpack Compose**, wie vorgegeben.

---

## 1. Overview & Core Concept

- **Was die App tut:** Ein eigenständiger Android TV Home-Screen (Launcher), der auf dem Philips Google TV (Android 14) als Standard-Oberfläche dient. Er bietet schnellen Zugriff auf Lieblings-Apps, eine sortierbare Liste aller installierten TV-Apps, App-Management per Fernbedienung (Lang-Klick) sowie eine Informations-Kopfzeile (Hero-Karte) mit Live-Uhrzeit, Datum und lokalem Wetter.
- **Zielgruppe:** Der Nutzer auf seinem Philips Google TV (Modell TA5) im Wohnzimmer, gesteuert ausschließlich über die D-Pad-Tasten der TV-Fernbedienung.
- **Mehrwert:** Keine Werbeeinblendungen, flüssige Navigation, maximale Übersichtlichkeit, volle Anpassbarkeit der App-Reihenfolge und eigene App-Icons.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Erststart & Navigation:**
   - Beim ersten Start fokussiert der Launcher automatisch auf die Hero-Karte.
   - Mit dem D-Pad (Runter) navigiert der Nutzer nahtlos in die "Favoriten"-Reihe, weiter in "Zuletzt genutzt" und in "Alle Apps".
   - Die zuletzt fokussierte Position wird persistent gemerkt, sodass ein Druck auf die HOME-Taste den Nutzer genau an die gewohnte Stelle zurückbringt.
2. **App-Interaktion & Kontextmenü:**
   - Klick (D-Pad Center): Startet die gewählte App sofort und aktualisiert die Liste der zuletzt genutzten Apps.
   - Lang-Klick (D-Pad Center gedrückt halten): Öffnet ein fokussierbares Leanback-Kontextmenü mit Optionen:
     - *Zu Favoriten hinzufügen / Aus Favoriten entfernen*
     - *App-Info* (öffnet die Android-Systemeinstellungen zur App)
     - *Deinstallieren* (öffnet den Android-Deinstallationsdialog)
     - *Icon ändern* (wählt ein Bild aus dem Gerätespeicher als Custom Banner)
3. **Einstellungen & Konfiguration:**
   - Klick auf das Einstellungs-Icon öffnet die übersichtliche `SettingsActivity` mit:
     - Toggle: *System-Apps anzeigen* (Filterung an/aus)
     - Auswahl: *Letzte-Apps-Modus* (Aus / Nur Hero / Nur Reihe / Hero & Reihe)
     - Dialog/Aktion: *App-Reihenfolge anpassen*
     - Aktion: *Auf Werkseinstellungen zurücksetzen*
4. **Hero-Karte & Live-Wetter:**
   - Linke Seite: Schnellstart-Kachel der zuletzt genutzten App.
   - Rechte Seite: Große Echtzeituhr (automatisch sekundengenau aktualisiert), deutsches Datumsformat ("Freitag, 24. Oktober") und Live-Wetteranzeige (Temperatur, Wetter-Icon, Zustand z.B. "Leicht bewölkt").

### Visual Identity & Theme
- **Farbpalette:**
  - Hintergrund: Deep Dark Grey `#121212`
  - Hero-Kartenfläche: Leicht abgehobenes Dunkelgrau `#1E1E1E` mit Elevation/Schatten
  - App-Karten: `#242424`
  - Akzentfarbe: Deep Purple `#9C27B0` für Fokusrahmen, Buttons und Glow
  - Textfarben: `#FFFFFF` (High Emphasis), `#B0B0B0` (Medium Emphasis)
- **Typografie (Roboto):**
  - Hero-Uhrzeit: 48sp Bold
  - Reihen-Titel: 18sp-20sp Medium
  - App-Titel: 14sp Regular
- **Fokus-Effekte:**
  - Animierte Skalierung auf 1,08x–1,10x beim D-Pad-Hover
  - 2dp lila Konturrahmen (`#9C27B0`)
  - Subtiler lila Glow-Schatten

---

## 3. Key Product Decisions & Trade-Offs

- **Leanback RowsSupportFragment + Custom Hero Header:**
  - *Ansatz:* Kombination aus einem ansprechend gestalteten Hero-Header-Layout oben und einem Leanback `RowsSupportFragment` darunter in der `MainActivity`.
  - *Warum:* Garantiert native D-Pad-Scroll-Physik von Android TV ohne Ruckler und hält den Code sauber modular.
- **Datenschicht mit SharedPreferences & JSON-Serialisierung:**
  - *Ansatz:* Speichern von Favoriten-IDs, benutzerdefinierter Reihenfolge, Custom-Icon-Pfaden und Launcher-Einstellungen in den `SharedPreferences` bzw. privaten App-Dateien.
  - *Warum:* Extrem leichtgewichtig, zuverlässig ohne Overhead einer schwergewichtigen Datenbank für Phase 1, und leicht über "Auf Standardeinstellungen zurücksetzen" zu leeren.
- **Berechtigungen für Android TV:**
  - *Ansatz:* `QUERY_ALL_PACKAGES` im Manifest deklarieren, da auf Android 11+ (API 30+) Apps sonst standardmäßig für andere Apps unsichtbar sind.
  - *Ansatz:* `ACCESS_COARSE_LOCATION` für die automatische Wetterstandort-Erkennung mit sicherem Fallback auf Standardkoordinaten bei Verweigerung.

---

## 4. Technical Architecture & Data Strategy

### System- & Komponenten-Diagramm

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MainActivity (Home-Screen)                      │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    HeroView (Kopfbereich)                        │  │
│  │   ┌─────────────────────────────┐  ┌──────────────────────────┐  │  │
│  │   │  Zuletzt genutzte App       │  │  Uhrzeit, Datum &        │  │  │
│  │   │  (Schnellstart-Button)      │  │  Live-Wetter (OpenWeather│  │  │
│  │   └─────────────────────────────┘  └──────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │              LauncherRowsFragment (RowsSupportFragment)           │  │
│  │                                                                  │  │
│  │   Row 1: [ Favoriten ]          -> [App 1] [App 2] [App 3] ...   │  │
│  │   Row 2: [ Zuletzt genutzt ]    -> [App 4] [App 1] ...           │  │
│  │   Row 3: [ Alle Apps ]          -> [App 1] [App 2] [App 3] ...   │  │
│  │   Row 4: [ Werkzeuge & Einstell.] -> [Einstellungen] [Hilfe]     │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
           ┌────────────────────────┴────────────────────────┐
           ▼                                                 ▼
┌─────────────────────────┐                       ┌───────────────────────┐
│     AppRepository       │                       │    WeatherRepository  │
│  - PackageManager Query │                       │  - OpenWeatherMap API │
│  - SharedPreferences    │                       │  - FusedLocation/     │
│    (Favoriten, Custom   │                       │    LastKnownLocation  │
│     Reihenfolge, Icons) │                       │  - Offline Fallback   │
└─────────────────────────┘                       └───────────────────────┘
```

### Module & geplante Schritte:
1. **Projekt-Setup & Leanback:**
   - `gradle/libs.versions.toml`: Hinzufügen von `androidx.leanback:leanback:1.0.0` und `androidx.leanback:leanback-preference:1.0.0`.
   - `app/build.gradle.kts`: Leanback-Dependencies einbinden, Compose deaktivieren (wie ausdrücklich gefordert).
   - `AndroidManifest.xml`: Konfiguration als Launcher (`CATEGORY_HOME`, `CATEGORY_DEFAULT`, `CATEGORY_LEANBACK_LAUNCHER`), Features `android.software.leanback` (false) und `android.hardware.touchscreen` (false), Berechtigungen (`QUERY_ALL_PACKAGES`, `INTERNET`, `ACCESS_COARSE_LOCATION`).
2. **Datenschicht & Modelle:**
   - `model/AppInfo.kt`: Datenklasse für installierte Apps (Titel, Paketname, Aktivitätsname, Icon/Banner, Favoritenstatus, Custom-Icon-Pfad).
   - `data/AppPreferences.kt`: Verwaltung von Favoriten-Liste, App-Reihenfolge, zuletzt gestarteten Apps und Launcher-Optionen.
   - `data/AppRepository.kt`: Asynchrones Laden aller Launcher-Apps via `PackageManager.queryIntentActivities()`.
3. **Home-Screen & Leanback-UI:**
   - `ui/presenter/AppCardPresenter.kt`: Leanback-Presenter für App-Karten (`ImageCardView`) mit Deep Purple Fokus-Rahmen, Skalierung und Kontextmenü-Unterstützung.
   - `ui/MainFragment.kt`: `RowsSupportFragment` mit `ListRow` für Favoriten, Zuletzt genutzt und Alle Apps.
   - `ui/hero/HeroCardView.kt` & Layout: Custom View / Layout für Uhrzeit, deutsches Datum und Wetter-Widget.
   - `MainActivity.kt`: Haupt-Activity mit Koordination von Hero-Header und RowsFragment, Fokus-Restoration.
4. **App-Kontextmenü:**
   - `ui/dialog/AppContextMenuDialog.kt`: Leanback-kompatibler Dialog bei Langklick auf App-Karten (Favorisieren, Deinstallieren, App-Info, Icon ändern).
5. **Settings-Activity:**
   - `ui/settings/SettingsActivity.kt` & `SettingsFragment.kt`: D-Pad-optimierte Einstellungen mit Schaltern für System-Apps, Letzte-Apps-Modus und Zurücksetzen.
6. **Wetter-Integration:**
   - `data/weather/WeatherService.kt` & `WeatherRepository.kt`: Retrofit-Client für OpenWeatherMap mit API-Key aus `BuildConfig.OPENWEATHERMAP_API_KEY`.
   - `.env.example` und Setup-Anleitung für `local.properties`.
   - Graceful Error Handling bei fehlender Internetverbindung oder ungültigem Key.
