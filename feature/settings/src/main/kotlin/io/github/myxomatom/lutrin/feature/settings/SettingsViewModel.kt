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
import io.github.myxomatom.lutrin.core.domain.settings.SetIgnoreCopyRestrictionUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetPagePreviewUseCase
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
 * @property pagePreview whether dragging the page slider of the reader previews the page aimed
 *   at, the document moving there only on release (ADR 0018).
 * @property ignoreCopyRestriction whether the reader copies the text of documents whose author
 *   forbids it (ADR 0024); off by default.
 * @property recentCount number of documents in the recent list; « Vider les récents » is
 *   disabled at 0, and when the list cannot be read.
 */
data class SettingsUiState(
    val loading: Boolean = true,
    val dynamicColor: Boolean = AppSettings.DEFAULT_DYNAMIC_COLOR,
    val pagePreview: Boolean = AppSettings.DEFAULT_PAGE_PREVIEW,
    val ignoreCopyRestriction: Boolean = AppSettings.DEFAULT_IGNORE_COPY_RESTRICTION,
    val recentCount: Int = 0,
)

/**
 * State holder of the settings screen (E3).
 *
 * @param observeSettings reads the stored settings.
 * @param setDynamicColor stores the dynamic colour choice.
 * @param setPagePreview stores the page preview choice of the reader's slider.
 * @param setIgnoreCopyRestriction stores whether the copy restriction of documents is ignored.
 * @param observeRecentDocuments counts the recent documents.
 * @param clearRecentDocuments empties the recent list (« Vider les récents », ADR 0016).
 * @param applicationScope where the clearing runs: leaving the screen at once never stops it
 *   half-way.
 */
@Suppress("LongParameterList") // Dependencies injected by Hilt.
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val setDynamicColor: SetDynamicColorUseCase,
    private val setPagePreview: SetPagePreviewUseCase,
    observeRecentDocuments: ObserveRecentDocumentsUseCase,
    private val clearRecentDocuments: ClearRecentDocumentsUseCase,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val setIgnoreCopyRestriction: SetIgnoreCopyRestrictionUseCase,
) : ViewModel() {
    /** Current state of the screen. */
    val uiState: StateFlow<SettingsUiState> =
        combine(
            observeSettings(),
            // A list that cannot be read cannot be cleared either: the action is disabled.
            observeRecentDocuments().map { it.size }.catch { emit(0) },
        ) { settings, recentCount ->
            SettingsUiState(
                loading = false,
                dynamicColor = settings.dynamicColor,
                pagePreview = settings.pagePreview,
                ignoreCopyRestriction = settings.ignoreCopyRestriction,
                recentCount = recentCount,
            )
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
     * Turns the page preview of the reader's slider on or off (ADR 0018); every document, open or
     * not, follows at once.
     *
     * @param enabled whether dragging the slider previews the page and moves only on release.
     */
    fun onPagePreviewChange(enabled: Boolean) {
        viewModelScope.launch { setPagePreview(enabled) }
    }

    /**
     * Chooses whether the reader copies the text of documents whose author forbids it (ADR 0024);
     * an open document follows at once.
     *
     * @param enabled whether the copy restriction of documents is ignored.
     */
    fun onIgnoreCopyRestrictionChange(enabled: Boolean) {
        viewModelScope.launch { setIgnoreCopyRestriction(enabled) }
    }

    /**
     * Empties the recent list, once the user confirmed it: every entry, its saved page, its
     * persisted permission and its thumbnail. The documents themselves are not touched.
     */
    fun onClearRecents() {
        applicationScope.launch { clearRecentDocuments() }
    }
}
