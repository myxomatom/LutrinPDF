// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.search

import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit

/**
 * State of the search in the reader (F07, V1, ADR 0023), exposed by [SearchController].
 *
 * @property isOpen whether the search bar replaces the top bar of the reader.
 * @property query text typed in the search field.
 * @property matchCase « Respecter la casse »: off by default, kept for the session only.
 * @property matchAccents « Respecter les accents »: off by default, kept for the session only.
 * @property results occurrences found so far, in document order.
 * @property current the occurrence shown and highlighted differently, `null` before the first
 *   one is found.
 * @property hasMoved whether the user moved to another occurrence since the search started
 *   (« suivant », « précédent »): TalkBack then announces the occurrence instead of the count.
 * @property status whether a search runs, ended or failed.
 * @property pageCount number of pages of the document, for the progress.
 */
data class SearchState(
    val isOpen: Boolean = false,
    val query: String = "",
    val matchCase: Boolean = false,
    val matchAccents: Boolean = false,
    val results: SearchResults = SearchResults.Empty,
    val current: HitRef? = null,
    val hasMoved: Boolean = false,
    val status: SearchStatus = SearchStatus.Idle,
    val pageCount: Int = 0,
) {
    /** Rank of [current] among the results, from 0, or -1 without a current occurrence. */
    val currentIndex: Int get() = current?.let(results::indexOf) ?: -1

    /** The occurrence [current] points to, with its areas and its excerpt. */
    val currentHit: SearchHit? get() = current?.let(results::get)

    /** Share of the document searched, from 0 to 1, for the progress indicator. */
    val progress: Float get() = if (pageCount > 0) results.searchedPages.toFloat() / pageCount else 0f
}

/** Where the search stands. */
sealed interface SearchStatus {
    /** Nothing searched: the field is empty, or the search waits for the end of the typing. */
    data object Idle : SearchStatus

    /** Pages are being searched; results arrive page by page. */
    data object Searching : SearchStatus

    /** Every page was searched, or the search stopped at the most results it returns. */
    data object Done : SearchStatus

    /** The search failed (document closed or unreadable); the results found so far stay. */
    data object Failed : SearchStatus
}

/** One-off effect of the search, handled once by the screen. */
sealed interface SearchEffect {
    /**
     * Brings [hit] into the middle of the reading area: the first occurrence found, or the one
     * reached with « suivant » or « précédent ».
     *
     * @property hit the occurrence to show.
     * @property byUser whether the user moved to it (« suivant », « précédent »): TalkBack then
     *   announces it; the first occurrence is announced with the count at the end of the search.
     */
    data class Reveal(val hit: SearchHit, val byUser: Boolean) : SearchEffect
}
