// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import io.github.myxomatom.lutrin.core.domain.pdf.PageSize

/** What the reader screen (E2) shows. Immutable; produced by [ReaderViewModel]. */
sealed interface ReaderUiState {
    /** Name of the document, shown in the top bar of every state. */
    val title: String

    /**
     * The document is being opened.
     *
     * @property title name of the document.
     */
    data class Loading(override val title: String) : ReaderUiState

    /**
     * The document could not be opened (view V8).
     *
     * @property title name of the document.
     * @property reason why, which selects the message shown.
     */
    data class Error(override val title: String, val reason: ReaderError) : ReaderUiState

    /**
     * The document is open.
     *
     * @property title name of the document.
     * @property pageSizes size of every page in points, rotation applied; never empty.
     */
    data class Content(override val title: String, val pageSizes: List<PageSize>) : ReaderUiState {
        /** Number of pages. */
        val pageCount: Int get() = pageSizes.size
    }
}

/** Why a document could not be opened, as the user is told. */
enum class ReaderError {
    /** Damaged, truncated or not a PDF. */
    UNREADABLE,

    /** Moved, deleted, or the read permission was revoked. */
    NOT_ACCESSIBLE,

    /** Encrypted with a user password; the password dialog (V4) comes with milestone M3. */
    PASSWORD_PROTECTED,
}
