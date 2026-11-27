// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.AppTheme
import io.github.myxomatom.lutrin.core.domain.settings.DocumentColorMode
import io.github.myxomatom.lutrin.core.domain.settings.FitMode
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory [SettingsRepository] for tests.
 *
 * @param initial settings before any change.
 */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    /** Current settings; a [StateFlow], so tests can read [StateFlow.value] directly. */
    override val settings: StateFlow<AppSettings> = state

    /** Changes the setting in memory; [settings] emits the new value at once. */
    override suspend fun setDynamicColor(enabled: Boolean) {
        state.value = state.value.copy(dynamicColor = enabled)
    }

    /** Changes the scroll mode in memory; [settings] emits the new value at once. */
    override suspend fun setScrollMode(mode: ScrollMode) {
        state.value = state.value.copy(scrollMode = mode)
    }

    /** Changes the page preview setting in memory; [settings] emits the new value at once. */
    override suspend fun setPagePreview(enabled: Boolean) {
        state.value = state.value.copy(pagePreview = enabled)
    }

    /** Changes the document restrictions setting in memory; [settings] emits the new value at once. */
    override suspend fun setIgnoreDocumentRestrictions(enabled: Boolean) {
        state.value = state.value.copy(ignoreDocumentRestrictions = enabled)
    }

    /** Changes the app theme in memory; [settings] emits the new value at once. */
    override suspend fun setAppTheme(theme: AppTheme) {
        state.value = state.value.copy(appTheme = theme)
    }

    /** Changes, or forgets, the document colours in memory; [settings] emits the new value at once. */
    override suspend fun setDocumentColorMode(mode: DocumentColorMode?) {
        state.value = state.value.copy(documentColors = state.value.documentColors.copy(chosen = mode))
    }

    /** Changes the dark theme follow in memory; [settings] emits the new value at once. */
    override suspend fun setFollowDarkTheme(enabled: Boolean) {
        state.value = state.value.copy(documentColors = state.value.documentColors.copy(followDarkTheme = enabled))
    }

    /** Changes the fit mode in memory; [settings] emits the new value at once. */
    override suspend fun setFitMode(mode: FitMode) {
        state.value = state.value.copy(chosenFitMode = mode)
    }

    /** Changes the keep-screen-on setting in memory; [settings] emits the new value at once. */
    override suspend fun setKeepScreenOn(enabled: Boolean) {
        state.value = state.value.copy(keepScreenOn = enabled)
    }

    /** Changes the orientation lock in memory; [settings] emits the new value at once. */
    override suspend fun setLockOrientation(enabled: Boolean) {
        state.value = state.value.copy(lockOrientation = enabled)
    }
}
