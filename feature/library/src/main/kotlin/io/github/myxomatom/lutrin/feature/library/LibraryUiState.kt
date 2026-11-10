// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** State of the library screen (E1), exposed by [LibraryViewModel]. */
sealed interface LibraryUiState {
    /** The recent list is being read. */
    data object Loading : LibraryUiState

    /** No recent document: the empty state of `E1-Bibliotheque-Vide.png`. */
    data object Empty : LibraryUiState

    /** The recent list could not be read. */
    data object Error : LibraryUiState

    /**
     * The recent documents (F11), split as the library shows them (ADR 0018).
     *
     * @property resume the most recently opened document, shown in the « Reprendre la lecture »
     *   card, whatever it is: protected, of a single page or read to the end.
     * @property others the other recent documents, most recently opened first, listed under
     *   « Récents · n autres documents »; empty when there is only one recent document, and the
     *   section is then not shown.
     */
    data class Content(val resume: RecentDocumentItem, val others: List<RecentDocumentItem> = emptyList()) :
        LibraryUiState
}

/**
 * A recent document as the library shows it, in the resume card or in a row (E1 mock-up:
 * thumbnail, name, page or number of pages, date, progress).
 *
 * @property documentId identifier of the entry.
 * @property uri the document, opened again when the row is tapped.
 * @property name name shown.
 * @property pageCount number of pages, at least 1.
 * @property lastPage zero-based page where the user stopped reading.
 * @property openedOn when the document was last opened, relative to today.
 * @property thumbnailPath picture of the first page, or `null` while it is missing.
 * @property isProtected whether the document needs a password (F10): the row shows a lock and
 *   never a picture of the first page (E1 mock-up, ADR 0017).
 * @property thumbnailOutdated whether the picture is missing or older than the last opening, and
 *   has to be drawn (again); never for a protected document.
 * @property isReadingInProgress whether the user stopped before the last page of a document of
 *   several pages (`RecentDocument.isReadingInProgress`): the library then writes the page
 *   reached and shows a progress bar; otherwise it writes the number of pages, without bar.
 */
data class RecentDocumentItem(
    val documentId: String,
    val uri: String,
    val name: String,
    val pageCount: Int,
    val lastPage: Int,
    val openedOn: OpenedOn,
    val thumbnailPath: String?,
    val isProtected: Boolean = false,
    val thumbnailOutdated: Boolean = thumbnailPath == null && !isProtected,
    val isReadingInProgress: Boolean = false,
) {
    /** Reading progress, from 1 / [pageCount] on the first page to 1 on the last; shown in progress only. */
    val progress: Float get() = (lastPage + 1).toFloat() / pageCount.coerceAtLeast(1)

    /**
     * The document whose thumbnail grows into the reader's page when it is opened (ADR 0019), or
     * `null` for a protected document: it has no picture to grow, and the reader asks for its
     * password first; it only fades.
     */
    val transitionDocumentId: String? get() = documentId.takeUnless { isProtected }
}

/** When a document was last opened, as the library writes it (« aujourd'hui, 20:41 », « hier », « 28 oct. »). */
sealed interface OpenedOn {
    /**
     * Earlier today.
     *
     * @property time local time of the opening.
     */
    data class Today(val time: LocalTime) : OpenedOn

    /** Yesterday, whatever the time. */
    data object Yesterday : OpenedOn

    /**
     * Before yesterday, in the current year: shown without the year.
     *
     * @property date local date of the opening.
     */
    data class ThisYear(val date: LocalDate) : OpenedOn

    /**
     * In an earlier year, or in the future (clock changed): shown with the year.
     *
     * @property date local date of the opening.
     */
    data class OtherYear(val date: LocalDate) : OpenedOn
}

/**
 * A recent document removed by the user while « Annuler » is still offered (ADR 0016): its row
 * is hidden, but the entry, its permission and its thumbnail are still there.
 *
 * @property documentId the entry.
 * @property name its name, for the message.
 * @property openedAt its last opening when it was removed: an entry opened again since is kept.
 */
data class PendingRemoval(val documentId: String, val name: String, val openedAt: Instant)
