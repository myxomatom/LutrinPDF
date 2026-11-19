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
 * Chooses whether the reader may copy the text of a document whose author forbids it (F08,
 * ADR 0024). Off by default; the reader follows the change at once.
 *
 * @param repository where the settings are stored.
 */
class SetIgnoreCopyRestrictionUseCase @Inject constructor(private val repository: SettingsRepository) {
    /** @param enabled whether the copy restriction of documents is ignored from now on. */
    suspend operator fun invoke(enabled: Boolean) {
        repository.setIgnoreCopyRestriction(enabled)
    }
}
