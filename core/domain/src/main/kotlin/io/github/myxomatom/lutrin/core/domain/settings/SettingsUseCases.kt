// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.settings

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observes the user preferences.
 *
 * @param repository where the settings are stored.
 */
class ObserveSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    /** @return the current settings, then every change. */
    operator fun invoke(): Flow<AppSettings> = repository.settings
}

/**
 * Turns dynamic colours (Material You) on or off (F13, ADR 0010).
 *
 * @param repository where the settings are stored.
 */
class SetDynamicColorUseCase @Inject constructor(private val repository: SettingsRepository) {
    /** @param enabled whether colours follow the wallpaper. */
    suspend operator fun invoke(enabled: Boolean) {
        repository.setDynamicColor(enabled)
    }
}
