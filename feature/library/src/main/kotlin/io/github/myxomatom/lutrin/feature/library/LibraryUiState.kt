// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

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
     * The recent documents (F11), most recently opened first.
     *
     * @property documents never empty.
     */
    data class Content(val documents: List<RecentDocumentItem>) : LibraryUiState
}

/**
 * One row of the recent list (E1 mock-up: thumbnail, name, page, date, progress).
 *
 * @property documentId identifier of the entry.
 * @property uri the document, opened again when the row is tapped.
 * @property name name shown.
 * @property pageCount number of pages, at least 1.
 * @property lastPage zero-based page where the user stopped reading.
 * @property openedOn when the document was last opened, relative to today.
 * @property thumbnailPath picture of the first page, or `null` while it is missing.
 * @property thumbnailOutdated whether the picture is missing or older than the last opening, and
 *   has to be drawn (again).
 */
data class RecentDocumentItem(
    val documentId: String,
    val uri: String,
    val name: String,
    val pageCount: Int,
    val lastPage: Int,
    val openedOn: OpenedOn,
    val thumbnailPath: String?,
    val thumbnailOutdated: Boolean = thumbnailPath == null,
) {
    /** Reading progress, from 1 / [pageCount] on the first page to 1 on the last. */
    val progress: Float get() = (lastPage + 1).toFloat() / pageCount.coerceAtLeast(1)
}

/** When a document was last opened, as the library writes it (« Aujourd'hui, 20:41 », « Hier », « 28 oct. »). */
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
