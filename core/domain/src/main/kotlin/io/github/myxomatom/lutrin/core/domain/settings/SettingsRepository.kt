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

    /**
     * Turns the page preview of the reader's slider on or off, for every document.
     *
     * @param enabled whether dragging the slider previews the page and moves only on release.
     */
    suspend fun setPagePreview(enabled: Boolean)

    /**
     * Chooses whether the reader may copy the text (ADR 0024) and print (ADR 0026) documents whose
     * author forbids it.
     *
     * @param enabled whether the copy and print restrictions of a document are ignored.
     */
    suspend fun setIgnoreDocumentRestrictions(enabled: Boolean)

    /**
     * Chooses the theme of the interface (F13, ADR 0028).
     *
     * @param theme light, dark, or the theme of the system.
     */
    suspend fun setAppTheme(theme: AppTheme)

    /**
     * Stores the colours chosen for every document (F13, ADR 0028), or forgets the choice.
     *
     * @param mode the colours picked by the user; `null` forgets the choice, so that the
     *   original colours apply again, or night in the dark theme when it is followed.
     */
    suspend fun setDocumentColorMode(mode: DocumentColorMode?)

    /**
     * Chooses whether documents without chosen colours turn to night while the app is dark.
     *
     * @param enabled whether the dark theme of the app is followed.
     */
    suspend fun setFollowDarkTheme(enabled: Boolean)

    /**
     * Chooses what the pages are fitted to, for every document (F04, ADR 0029).
     *
     * @param mode the page fills the width, or is shown whole.
     */
    suspend fun setFitMode(mode: FitMode)

    /**
     * Chooses whether the screen stays on while a document is shown (ADR 0029).
     *
     * @param enabled whether the screen is kept awake in the reader.
     */
    suspend fun setKeepScreenOn(enabled: Boolean)

    /**
     * Chooses whether the reader keeps its current orientation (F17, ADR 0029).
     *
     * @param enabled whether the orientation is locked while a document is shown.
     */
    suspend fun setLockOrientation(enabled: Boolean)
}
