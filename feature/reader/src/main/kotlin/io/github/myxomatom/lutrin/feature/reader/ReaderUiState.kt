// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.feature.reader.links.PageLinks
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.Dispatchers

/** State of the reader screen (E2), exposed by [ReaderViewModel]. */
sealed interface ReaderUiState {
    /** The document is being opened. */
    data object Loading : ReaderUiState

    /**
     * The document could not be opened (V8).
     *
     * @property reason what went wrong, mapped to a message by the screen.
     * @property inRecents whether the document is in the recent list: the screen then offers
     *   « Retirer des récents » (`SPEC.md` §6, ADR 0016).
     */
    data class Error(val reason: ReaderError, val inRecents: Boolean = false) : ReaderUiState

    /**
     * The document is encrypted and needs its user password (F10): the password dialog (V4)
     * asks for it. The password itself is never part of the state (ADR 0017): it stays in the
     * field of the dialog until it is tried, so that no state, log or `toString()` can hold it.
     *
     * @property documentName name of the document, shown in the question.
     * @property isIncorrect whether the last password tried did not open the document.
     * @property isChecking whether a password is being tried: the dialog ignores new attempts.
     */
    data class PasswordRequired(
        val documentName: String,
        val isIncorrect: Boolean = false,
        val isChecking: Boolean = false,
    ) : ReaderUiState

    /**
     * The document is open.
     *
     * @property title document title, or file name.
     * @property pageSizes size of every page, in points; never empty.
     * @property renderer draws the pages into bitmaps.
     * @property scrollMode continuous scroll or page by page (F03), from the settings.
     * @property pagePreview whether dragging the page slider previews the page aimed at and
     *   moves the document on release only (F05, ADR 0018), from the settings.
     * @property currentPage zero-based page last reported by the screen; where the reader
     *   starts again when the scroll mode changes.
     * @property links links of the pages (F09), read on demand; none by default.
     */
    data class Ready(
        val title: String,
        val pageSizes: List<PageSize>,
        val renderer: PageRenderer,
        val scrollMode: ScrollMode = AppSettings.DEFAULT_SCROLL_MODE,
        val currentPage: Int = 0,
        val pagePreview: Boolean = AppSettings.DEFAULT_PAGE_PREVIEW,
        val links: PageLinks = PageLinks(document = null, dispatcher = Dispatchers.Unconfined),
    ) : ReaderUiState
}

/**
 * Why a document could not be opened (`SPEC.md` §6). An encrypted document is not an error: the
 * reader asks for its password ([ReaderUiState.PasswordRequired]).
 */
enum class ReaderError {
    /** Damaged PDF. */
    UNREADABLE,

    /** Not a PDF. */
    UNSUPPORTED_FORMAT,

    /** File missing or permission revoked. */
    NOT_ACCESSIBLE,
}
