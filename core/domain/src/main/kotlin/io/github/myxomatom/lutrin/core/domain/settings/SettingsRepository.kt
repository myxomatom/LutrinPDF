// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.settings

import kotlinx.coroutines.flow.Flow

/** Reads and writes the [AppSettings]. Implemented in `core:data` with DataStore. */
interface SettingsRepository {
    /** Current settings, then every change; starts with the defaults when nothing is stored. */
    val settings: Flow<AppSettings>

    /**
     * Turns dynamic colours on or off.
     *
     * @param enabled whether colours follow the wallpaper.
     */
    suspend fun setDynamicColor(enabled: Boolean)

    /**
     * Chooses how the reader lays out the pages, for every document.
     *
     * @param mode continuous vertical scroll or one page per screen.
     */
    suspend fun setScrollMode(mode: ScrollMode)
}
