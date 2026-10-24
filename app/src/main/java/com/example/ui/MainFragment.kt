package com.example.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.data.AppPreferences
import com.example.data.AppRepository
import com.example.model.AppInfo
import com.example.ui.dialog.AppContextMenuDialog
import com.example.ui.presenter.AppCardPresenter
import com.example.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

/**
 * Leanback RowsSupportFragment für den Android TV Home-Screen.
 * Organisiert Apps in horizontal scrollbaren Zeilen ("Favoriten", "Zuletzt genutzt", "Alle Apps").
 */
class MainFragment : RowsSupportFragment() {

    private lateinit var appRepository: AppRepository
    private lateinit var appPreferences: AppPreferences
    private lateinit var rowsAdapter: ArrayObjectAdapter

    // Callbacks zur Kommunikation mit der MainActivity
    var onRecentAppUpdated: ((AppInfo?) -> Unit)? = null
    var onChangeIconRequested: ((AppInfo) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appRepository = AppRepository(requireContext())
        appPreferences = AppPreferences(requireContext())

        setupAdapter()
        setupListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
    }

    override fun onResume() {
        super.onResume()
        // Aktualisiere App-Reihen bei Rückkehr (z. B. nach Deinstallation oder Einstellungsänderung)
        loadData()
    }

    /**
     * Initialisiert den Haupt-Rows-Adapter mit angepasstem ListRowPresenter.
     */
    private fun setupAdapter() {
        val listRowPresenter = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE).apply {
            shadowEnabled = false
            selectEffectEnabled = false
        }
        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
        adapter = rowsAdapter
    }

    /**
     * Richtet Klick- und Fokus-Listener für die TV-Fernbedienung ein.
     */
    private fun setupListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is AppInfo -> {
                    // Prüfen, ob es sich um die Kachel für die Einstellungen handelt
                    if (item.packageName == "action.settings") {
                        startActivity(Intent(requireContext(), SettingsActivity::class.java))
                    } else {
                        val launched = appRepository.launchApp(item)
                        if (launched) {
                            onRecentAppUpdated?.invoke(item)
                        }
                    }
                }
            }
        }

        // Zuletzt fokussierte Zeile merken für die HOME-Tasten-Wiederherstellung
        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, row ->
            if (row is ListRow) {
                val rowIndex = rowsAdapter.indexOf(row)
                if (rowIndex >= 0) {
                    appPreferences.setLastFocusedRow(rowIndex)
                }
            }
        }
    }

    /**
     * Lädt installierte Apps asynchron und baut die Reihenstruktur auf.
     */
    fun loadData() {
        lifecycleScope.launch {
            val showSystem = appPreferences.isShowSystemApps()
            val allApps = appRepository.loadInstalledApps(showSystem)

            rowsAdapter.clear()

            val appCardPresenter = AppCardPresenter { appInfo, _ ->
                showContextMenu(appInfo)
            }

            // 1. Zeile: Favoriten
            val favoriteApps = allApps.filter { it.isFavorite }
            if (favoriteApps.isNotEmpty()) {
                val favAdapter = ArrayObjectAdapter(appCardPresenter)
                favoriteApps.forEach { favAdapter.add(it) }
                val favHeader = HeaderItem(0, getString(R.string.favorites_title))
                rowsAdapter.add(ListRow(favHeader, favAdapter))
            }

            // 2. Zeile: Zuletzt genutzt (sofern im Modus aktiviert)
            val recentsMode = appPreferences.getRecentAppsMode()
            if (recentsMode == AppPreferences.RECENTS_MODE_ROW_ONLY || recentsMode == AppPreferences.RECENTS_MODE_BOTH) {
                val recentPackages = appPreferences.getRecentApps()
                val recentApps = recentPackages.mapNotNull { pkg ->
                    allApps.find { it.packageName == pkg }
                }
                if (recentApps.isNotEmpty()) {
                    val recentsAdapter = ArrayObjectAdapter(appCardPresenter)
                    recentApps.forEach { recentsAdapter.add(it) }
                    val recentsHeader = HeaderItem(1, getString(R.string.recents_title))
                    rowsAdapter.add(ListRow(recentsHeader, recentsAdapter))
                }
            }

            // 3. Zeile: Alle Apps
            if (allApps.isNotEmpty()) {
                val allAppsAdapter = ArrayObjectAdapter(appCardPresenter)
                allApps.forEach { allAppsAdapter.add(it) }
                val allHeader = HeaderItem(2, getString(R.string.all_apps_title))
                rowsAdapter.add(ListRow(allHeader, allAppsAdapter))
            }

            // 4. Zeile: Werkzeuge / Einstellungen
            val toolsAdapter = ArrayObjectAdapter(appCardPresenter)
            val settingsAppCard = AppInfo(
                id = "action.settings",
                packageName = "action.settings",
                activityName = "",
                label = getString(R.string.settings_title),
                iconDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_settings_gear),
                isFavorite = false,
                isSystemApp = false
            )
            toolsAdapter.add(settingsAppCard)
            val toolsHeader = HeaderItem(3, getString(R.string.tools_title))
            rowsAdapter.add(ListRow(toolsHeader, toolsAdapter))

            // Neueste App an Hero-Karte übermitteln
            val mostRecentPackage = appPreferences.getRecentApps().firstOrNull()
            val mostRecentApp = allApps.find { it.packageName == mostRecentPackage }
                ?: allApps.firstOrNull()
            onRecentAppUpdated?.invoke(mostRecentApp)

            // Fokus wiederherstellen falls zuvor gemerkt
            val lastRow = appPreferences.getLastFocusedRow()
            if (lastRow in 0 until rowsAdapter.size()) {
                setSelectedPosition(lastRow)
            }
        }
    }

    /**
     * Öffnet den Kontextmenü-Dialog bei langem Klick auf ein App-Banner.
     */
    private fun showContextMenu(appInfo: AppInfo) {
        if (appInfo.packageName == "action.settings") return

        AppContextMenuDialog(
            context = requireContext(),
            appInfo = appInfo,
            appRepository = appRepository,
            appPreferences = appPreferences,
            onDataChanged = {
                loadData()
            },
            onChangeIconRequested = { app ->
                onChangeIconRequested?.invoke(app)
            }
        ).show()
    }
}
