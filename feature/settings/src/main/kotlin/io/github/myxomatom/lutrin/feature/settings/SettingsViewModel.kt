// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetDynamicColorUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
 */
data class SettingsUiState(val loading: Boolean = true, val dynamicColor: Boolean = AppSettings.DEFAULT_DYNAMIC_COLOR)

/**
 * State holder of the settings screen (E3).
 *
 * @param observeSettings reads the stored settings.
 * @param setDynamicColor stores the dynamic colour choice.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val setDynamicColor: SetDynamicColorUseCase,
) : ViewModel() {
    /** Current state of the screen. */
    val uiState: StateFlow<SettingsUiState> =
        observeSettings()
            .map { SettingsUiState(loading = false, dynamicColor = it.dynamicColor) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    /**
     * Turns dynamic colours on or off; the whole app follows at once.
     *
     * @param enabled whether colours follow the wallpaper.
     */
    fun onDynamicColorChange(enabled: Boolean) {
        viewModelScope.launch { setDynamicColor(enabled) }
    }
}
