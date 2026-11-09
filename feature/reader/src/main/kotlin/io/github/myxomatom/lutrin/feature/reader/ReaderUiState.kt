// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer

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
     * The document is open.
     *
     * @property title document title, or file name.
     * @property pageSizes size of every page, in points; never empty.
     * @property renderer draws the pages into bitmaps.
     * @property scrollMode continuous scroll or page by page (F03), from the settings.
     * @property currentPage zero-based page last reported by the screen; where the reader
     *   starts again when the scroll mode changes.
     */
    data class Ready(
        val title: String,
        val pageSizes: List<PageSize>,
        val renderer: PageRenderer,
        val scrollMode: ScrollMode = AppSettings.DEFAULT_SCROLL_MODE,
        val currentPage: Int = 0,
    ) : ReaderUiState
}

/** Why a document could not be opened (`SPEC.md` §6). */
enum class ReaderError {
    /** Encrypted document: the password dialog (F10) arrives with milestone M3. */
    PASSWORD_REQUIRED,

    /** Damaged PDF. */
    UNREADABLE,

    /** Not a PDF. */
    UNSUPPORTED_FORMAT,

    /** File missing or permission revoked. */
    NOT_ACCESSIBLE,
}
