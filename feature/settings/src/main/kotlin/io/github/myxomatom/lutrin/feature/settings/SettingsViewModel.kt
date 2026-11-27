// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.myxomatom.lutrin.core.domain.dispatchers.ApplicationScope
import io.github.myxomatom.lutrin.core.domain.recents.ClearRecentDocumentsUseCase
import io.github.myxomatom.lutrin.core.domain.recents.ObserveRecentDocumentsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.AppTheme
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetAppThemeUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetDynamicColorUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetFollowDarkThemeUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetIgnoreDocumentRestrictionsUseCase
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
 * @property ignoreDocumentRestrictions whether the reader copies the text and prints documents
 *   whose author forbids it (ADR 0024, ADR 0026); off by default.
 * @property recentCount number of documents in the recent list; « Vider les récents » is
 *   disabled at 0, and when the list cannot be read.
 * @property appTheme light, dark or the system theme for the interface (ADR 0028).
 * @property followDarkTheme whether documents turn to night while the app is dark, until colours
 *   are chosen in the reader (ADR 0028); off by default.
 */
data class SettingsUiState(
    val loading: Boolean = true,
    val dynamicColor: Boolean = AppSettings.DEFAULT_DYNAMIC_COLOR,
    val pagePreview: Boolean = AppSettings.DEFAULT_PAGE_PREVIEW,
    val ignoreDocumentRestrictions: Boolean = AppSettings.DEFAULT_IGNORE_DOCUMENT_RESTRICTIONS,
    val recentCount: Int = 0,
    val appTheme: AppTheme = AppSettings.DEFAULT_APP_THEME,
    val followDarkTheme: Boolean = false,
)

/**
 * State holder of the settings screen (E3).
 *
 * @param observeSettings reads the stored settings.
 * @param setDynamicColor stores the dynamic colour choice.
 * @param setPagePreview stores the page preview choice of the reader's slider.
 * @param observeRecentDocuments counts the recent documents.
 * @param clearRecentDocuments empties the recent list (« Vider les récents », ADR 0016).
 * @param applicationScope where the clearing runs: leaving the screen at once never stops it
 *   half-way.
 * @param setIgnoreDocumentRestrictions stores whether the restrictions of documents are ignored.
 * @param setAppTheme stores the theme of the interface (ADR 0028).
 * @param setFollowDarkTheme stores whether documents follow the dark theme (ADR 0028).
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
    private val setIgnoreDocumentRestrictions: SetIgnoreDocumentRestrictionsUseCase,
    private val setAppTheme: SetAppThemeUseCase,
    private val setFollowDarkTheme: SetFollowDarkThemeUseCase,
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
                ignoreDocumentRestrictions = settings.ignoreDocumentRestrictions,
                recentCount = recentCount,
                appTheme = settings.appTheme,
                followDarkTheme = settings.documentColors.followDarkTheme,
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
     * Chooses the theme of the interface; the whole app follows at once (ADR 0028). The colours
     * of the documents do not change, unless they follow the dark theme.
     *
     * @param theme light, dark or the theme of the system.
     */
    fun onAppThemeChange(theme: AppTheme) {
        viewModelScope.launch { setAppTheme(theme) }
    }

    /**
     * Chooses whether documents turn to night while the app is dark (ADR 0028). Turning it on
     * forgets the colours chosen in the reader, so that it applies at once.
     *
     * @param enabled whether documents follow the dark theme of the app.
     */
    fun onFollowDarkThemeChange(enabled: Boolean) {
        viewModelScope.launch { setFollowDarkTheme(enabled) }
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
     * Chooses whether the reader copies the text and prints documents whose author forbids it
     * (ADR 0024, ADR 0026); an open document follows at once.
     *
     * @param enabled whether the copy and print restrictions of documents are ignored.
     */
    fun onIgnoreDocumentRestrictionsChange(enabled: Boolean) {
        viewModelScope.launch { setIgnoreDocumentRestrictions(enabled) }
    }

    /**
     * Empties the recent list, once the user confirmed it: every entry, its saved page, its
     * persisted permission and its thumbnail. The documents themselves are not touched.
     */
    fun onClearRecents() {
        applicationScope.launch { clearRecentDocuments() }
    }
}
