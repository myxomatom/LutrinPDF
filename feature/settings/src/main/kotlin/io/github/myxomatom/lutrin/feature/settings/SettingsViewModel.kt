// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.myxomatom.lutrin.core.domain.dispatchers.ApplicationScope
import io.github.myxomatom.lutrin.core.domain.recents.ClearRecentDocumentsUseCase
import io.github.myxomatom.lutrin.core.domain.recents.ObserveRecentDocumentsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetDynamicColorUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How long the settings keep being observed after the screen stops (configuration change). */
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * What the settings screen (E3) shows.
 *
 * @property loading whether the stored settings are still being read.
 * @property dynamicColor whether colours follow the wallpaper.
 * @property recentCount number of documents in the recent list; « Vider les récents » is
 *   disabled at 0, and when the list cannot be read.
 */
data class SettingsUiState(
    val loading: Boolean = true,
    val dynamicColor: Boolean = AppSettings.DEFAULT_DYNAMIC_COLOR,
    val recentCount: Int = 0,
)

/**
 * State holder of the settings screen (E3).
 *
 * @param observeSettings reads the stored settings.
 * @param setDynamicColor stores the dynamic colour choice.
 * @param observeRecentDocuments counts the recent documents.
 * @param clearRecentDocuments empties the recent list (« Vider les récents », ADR 0016).
 * @param applicationScope where the clearing runs: leaving the screen at once never stops it
 *   half-way.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val setDynamicColor: SetDynamicColorUseCase,
    observeRecentDocuments: ObserveRecentDocumentsUseCase,
    private val clearRecentDocuments: ClearRecentDocumentsUseCase,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {
    /** Current state of the screen. */
    val uiState: StateFlow<SettingsUiState> =
        combine(
            observeSettings(),
            // A list that cannot be read cannot be cleared either: the action is disabled.
            observeRecentDocuments().map { it.size }.catch { emit(0) },
        ) { settings, recentCount ->
            SettingsUiState(loading = false, dynamicColor = settings.dynamicColor, recentCount = recentCount)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    /**
     * Turns dynamic colours on or off; the whole app follows at once.
     *
     * @param enabled whether colours follow the wallpaper.
     */
    fun onDynamicColorChange(enabled: Boolean) {
        viewModelScope.launch { setDynamicColor(enabled) }
    }

    /**
     * Empties the recent list, once the user confirmed it: every entry, its saved page, its
     * persisted permission and its thumbnail. The documents themselves are not touched.
     */
    fun onClearRecents() {
        applicationScope.launch { clearRecentDocuments() }
    }
}
