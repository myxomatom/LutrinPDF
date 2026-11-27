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

/**
 * Chooses how the reader lays out the pages: continuous scroll or page by page (F03, ADR 0011).
 *
 * @param repository where the settings are stored.
 */
class SetScrollModeUseCase @Inject constructor(private val repository: SettingsRepository) {
    /** @param mode the layout to use for every document from now on. */
    suspend operator fun invoke(mode: ScrollMode) {
        repository.setScrollMode(mode)
    }
}

/**
 * Turns the page preview of the reader's slider on or off (F05, ADR 0018): on, dragging the
 * slider shows the page aimed at and the document moves there on release; off, the document
 * follows the slider.
 *
 * @param repository where the settings are stored.
 */
class SetPagePreviewUseCase @Inject constructor(private val repository: SettingsRepository) {
    /** @param enabled whether the slider previews the page, for every document from now on. */
    suspend operator fun invoke(enabled: Boolean) {
        repository.setPagePreview(enabled)
    }
}

/**
 * Chooses whether the reader may copy the text (F08, ADR 0024) and print (F15, ADR 0026) a
 * document whose author forbids it. Off by default; the reader follows the change at once.
 *
 * @param repository where the settings are stored.
 */
class SetIgnoreDocumentRestrictionsUseCase @Inject constructor(private val repository: SettingsRepository) {
    /** @param enabled whether the copy and print restrictions of documents are ignored from now on. */
    suspend operator fun invoke(enabled: Boolean) {
        repository.setIgnoreDocumentRestrictions(enabled)
    }
}

/**
 * Chooses the theme of the interface: light, dark or that of the system (F13, ADR 0028). The
 * colours of the documents do not change with it, unless the user asked them to follow it.
 *
 * @param repository where the settings are stored.
 */
class SetAppThemeUseCase @Inject constructor(private val repository: SettingsRepository) {
    /** @param theme the theme of the interface from now on. */
    suspend operator fun invoke(theme: AppTheme) {
        repository.setAppTheme(theme)
    }
}

/**
 * Stores the colours the user picked for the documents in the reader (F13, V6, ADR 0028): one
 * choice for every document, which wins over the dark theme of the app, even when it is
 * « Normal ».
 *
 * @param repository where the settings are stored.
 */
class SetDocumentColorModeUseCase @Inject constructor(private val repository: SettingsRepository) {
    /** @param mode the colours to draw every document with from now on. */
    suspend operator fun invoke(mode: DocumentColorMode) {
        repository.setDocumentColorMode(mode)
    }
}

/**
 * Chooses whether documents turn to night while the app is dark (E3, ADR 0028). Turning it on
 * forgets the colours picked in the reader, otherwise it would have no visible effect; turning it
 * off keeps them.
 *
 * @param repository where the settings are stored.
 */
class SetFollowDarkThemeUseCase @Inject constructor(private val repository: SettingsRepository) {
    /** @param enabled whether documents follow the dark theme of the app from now on. */
    suspend operator fun invoke(enabled: Boolean) {
        repository.setFollowDarkTheme(enabled)
        if (enabled) repository.setDocumentColorMode(null)
    }
}
