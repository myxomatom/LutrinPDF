// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
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

    override val settings: StateFlow<AppSettings> = state

    override suspend fun setDynamicColor(enabled: Boolean) {
        state.value = state.value.copy(dynamicColor = enabled)
    }
}
