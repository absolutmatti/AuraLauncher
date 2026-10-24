package com.example.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.data.AppPreferences
import com.example.data.AppRepository
import com.example.model.AppInfo
import com.example.ui.dialog.AppContextMenuDialog
import com.example.ui.presenter.AppCardPresenter
import com.example.ui.settings.SettingsActivity
import com.example.ui.viewmodel.LauncherUiState
import com.example.ui.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch

/**
 * Haupt-Browse-Fragment für den Leanback Android TV Launcher.
 *
 * Im Android TV Leanback-System ist [RowsSupportFragment] das Kernstück eines jeden
 * Browse-Layouts ("BrowseFragment"). Es stellt horizontal scrollbare App-Reihen bereit:
 * 1. Favoriten
 * 2. Zuletzt genutzt
 * 3. Alle Apps
 * 4. System & Werkzeuge (Einstellungen)
 *
 * Diese row-basierte Leanback-Struktur wird über das [LauncherViewModel] mit Daten versorgt
 * und reagiert reaktiv auf Änderungen des [LauncherUiState].
 */
class MainBrowseFragment : RowsSupportFragment() {

    private lateinit var viewModel: LauncherViewModel
    private lateinit var appRepository: AppRepository
    private lateinit var appPreferences: AppPreferences
    private lateinit var rowsAdapter: ArrayObjectAdapter

    // Callbacks zur Kommunikation mit der übergeordneten MainActivity
    var onChangeIconRequested: ((AppInfo) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[LauncherViewModel::class.java]
        appRepository = AppRepository(requireContext())
        appPreferences = AppPreferences(requireContext())

        setupAdapter()
        setupListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Aktualisiere App-Listen bei Rückkehr zum Launcher
        viewModel.loadApps()
    }

    /**
     * Initialisiert den Haupt-Rows-Adapter mit dem Leanback [ListRowPresenter].
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
     * Richtet Klick- und Fokus-Listener für die Fernbedienung (D-Pad) ein.
     */
    private fun setupListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is AppInfo -> {
                    if (item.packageName == "action.settings") {
                        startActivity(Intent(requireContext(), SettingsActivity::class.java))
                    } else {
                        viewModel.launchApp(item)
                    }
                }
            }
        }

        // Zuletzt fokussierte Zeile merken, um den Fokus nach Drücken der HOME-Taste wiederherzustellen
        onItemViewSelectedListener = OnItemViewSelectedListener { _, _, _, row ->
            if (row is ListRow) {
                val rowIndex = rowsAdapter.indexOf(row)
                if (rowIndex >= 0) {
                    appPreferences.setLastFocusedRow(rowIndex)
                }
            }
        }
    }

    /**
     * Beobachtet den reaktiven [LauncherUiState] aus dem [LauncherViewModel].
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                renderState(state)
            }
        }
    }

    /**
     * Baut die Zeilenstruktur anhand des aktuellen Zustands auf.
     */
    private fun renderState(state: LauncherUiState) {
        rowsAdapter.clear()

        val appCardPresenter = AppCardPresenter { appInfo, _ ->
            showContextMenu(appInfo)
        }

        // 1. Zeile: Favoriten
        if (state.favoriteApps.isNotEmpty()) {
            val favAdapter = ArrayObjectAdapter(appCardPresenter)
            state.favoriteApps.forEach { favAdapter.add(it) }
            val favHeader = HeaderItem(0, getString(R.string.favorites_title))
            rowsAdapter.add(ListRow(favHeader, favAdapter))
        }

        // 2. Zeile: Zuletzt genutzt (sofern im Modus aktiviert)
        val recentsMode = state.recentsMode
        if (recentsMode == AppPreferences.RECENTS_MODE_ROW_ONLY || recentsMode == AppPreferences.RECENTS_MODE_BOTH) {
            if (state.recentApps.isNotEmpty()) {
                val recentsAdapter = ArrayObjectAdapter(appCardPresenter)
                state.recentApps.forEach { recentsAdapter.add(it) }
                val recentsHeader = HeaderItem(1, getString(R.string.recents_title))
                rowsAdapter.add(ListRow(recentsHeader, recentsAdapter))
            }
        }

        // 3. Zeile: Alle Apps
        if (state.allApps.isNotEmpty()) {
            val allAppsAdapter = ArrayObjectAdapter(appCardPresenter)
            state.allApps.forEach { allAppsAdapter.add(it) }
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

        // Zuletzt fokussierte Zeile wiederherstellen
        val lastRow = appPreferences.getLastFocusedRow()
        if (lastRow in 0 until rowsAdapter.size()) {
            setSelectedPosition(lastRow)
        }
    }

    /**
     * Manuelle Nachlade-Methode (z. B. nach Custom-Icon Änderungen).
     */
    fun loadData() {
        viewModel.loadApps()
    }

    /**
     * Öffnet das Kontextmenü bei langem Druck auf die OK/D-Pad-Mitte Taste.
     */
    private fun showContextMenu(appInfo: AppInfo) {
        if (appInfo.packageName == "action.settings") return

        AppContextMenuDialog(
            context = requireContext(),
            appInfo = appInfo,
            appRepository = appRepository,
            appPreferences = appPreferences,
            onDataChanged = {
                viewModel.loadApps()
            },
            onChangeIconRequested = { app ->
                onChangeIconRequested?.invoke(app)
            }
        ).show()
    }
}

/**
 * Typ-Alias zur Abwärtskompatibilität, falls MainFragment referenziert wird.
 */
typealias MainFragment = MainBrowseFragment
