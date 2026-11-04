// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer

/** State of the reader screen (E2), exposed by [ReaderViewModel]. */
sealed interface ReaderUiState {
    /** The document is being opened. */
    data object Loading : ReaderUiState

    /**
     * The document could not be opened (V8).
     *
     * @property reason what went wrong, mapped to a message by the screen.
     */
    data class Error(val reason: ReaderError) : ReaderUiState

    /**
     * The document is open.
     *
     * @property title document title, or file name.
     * @property pageSizes size of every page, in points; never empty.
     * @property renderer draws the pages into bitmaps.
     */
    data class Ready(val title: String, val pageSizes: List<PageSize>, val renderer: PageRenderer) : ReaderUiState
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
