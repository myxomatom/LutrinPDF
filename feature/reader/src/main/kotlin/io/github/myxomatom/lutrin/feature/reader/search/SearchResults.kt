// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.search

import androidx.compose.runtime.Immutable
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SearchPageResult

/**
 * One occurrence among the [SearchResults], by its page and its rank on the page: unlike an
 * index in the whole list, it stays the same when the results of earlier pages arrive later (the
 * search starts from the page shown and wraps around, ADR 0020).
 *
 * @property pageIndex zero-based page of the occurrence.
 * @property indexOnPage rank of the occurrence on its page, in reading order, from 0.
 */
data class HitRef(val pageIndex: Int, val indexOnPage: Int)

/**
 * What a search found so far (F07, ADR 0023): the occurrences in document order, whatever the
 * order in which their pages were searched. Immutable; [plus] gives the results with one more page.
 *
 * Looking up the occurrences of a page is a binary search, so that drawing the highlights of the
 * pages on screen never walks the whole list.
 *
 * @property hits every occurrence found, in document order (page, then reading order).
 * @property searchedPages number of pages searched so far, for the progress.
 * @property isLimitReached whether the search stopped at the most results it returns
 *   (`MAX_SEARCH_HITS`): there may be more in the pages not searched.
 */
@Immutable
class SearchResults private constructor(
    val hits: List<SearchHit>,
    val searchedPages: Int,
    val isLimitReached: Boolean,
) {
    /** Number of occurrences found. */
    val size: Int get() = hits.size

    /** Whether no occurrence was found. */
    fun isEmpty(): Boolean = hits.isEmpty()

    /**
     * The results with those of one more page: its occurrences are inserted at their place in
     * document order. A page already searched is not expected.
     *
     * @param page what the search found on the page.
     */
    operator fun plus(page: SearchPageResult): SearchResults {
        val position = firstIndexAfter(page.pageIndex)
        val merged =
            if (page.hits.isEmpty()) {
                hits
            } else {
                ArrayList<SearchHit>(hits.size + page.hits.size).apply {
                    addAll(hits.subList(0, position))
                    addAll(page.hits)
                    addAll(hits.subList(position, hits.size))
                }
            }
        return SearchResults(merged, searchedPages + 1, isLimitReached || page.isLimitReached)
    }

    /** Occurrences on the page at [pageIndex], in reading order; empty when it has none. */
    fun onPage(pageIndex: Int): List<SearchHit> = hits.subList(firstIndexOf(pageIndex), firstIndexAfter(pageIndex))

    /** Position of [ref] in [hits], or -1 when the results have no such occurrence. */
    fun indexOf(ref: HitRef): Int {
        val first = firstIndexOf(ref.pageIndex)
        val index = first + ref.indexOnPage
        return if (ref.indexOnPage >= 0 && index < firstIndexAfter(ref.pageIndex)) index else -1
    }

    /**
     * The occurrence at [index] in [hits], as a [HitRef].
     *
     * @throws IndexOutOfBoundsException if [index] is not in [hits].
     */
    fun refAt(index: Int): HitRef {
        val pageIndex = hits[index].pageIndex
        return HitRef(pageIndex, index - firstIndexOf(pageIndex))
    }

    /** The occurrence [ref] points to, or `null` when the results have none. */
    operator fun get(ref: HitRef): SearchHit? = indexOf(ref).takeIf { it >= 0 }?.let(hits::get)

    /** Index of the first occurrence on a page at or after [pageIndex] (lower bound). */
    private fun firstIndexOf(pageIndex: Int): Int = lowerBound { it >= pageIndex }

    /** Index of the first occurrence on a page after [pageIndex] (upper bound). */
    private fun firstIndexAfter(pageIndex: Int): Int = lowerBound { it > pageIndex }

    /** First index whose page satisfies [isAtOrAfter], which is monotonic over [hits]. */
    private inline fun lowerBound(isAtOrAfter: (Int) -> Boolean): Int {
        var low = 0
        var high = hits.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (isAtOrAfter(hits[middle].pageIndex)) high = middle else low = middle + 1
        }
        return low
    }

    /** Factories of [SearchResults]. */
    companion object {
        /** No page searched yet, no occurrence. */
        val Empty: SearchResults = SearchResults(emptyList(), searchedPages = 0, isLimitReached = false)
    }
}
