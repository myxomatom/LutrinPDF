// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
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
}
