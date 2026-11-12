// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.search

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SearchPageResult
import org.junit.Test

/** [SearchResults]: document order whatever the search order, lookups by page (F07). */
class SearchResultsTest {
    private fun hit(page: Int, top: Float = 100f) =
        SearchHit(page, listOf(PdfQuad.of(PdfRect(0f, top, 50f, top + 10f))))

    private fun page(index: Int, vararg tops: Float, isLimitReached: Boolean = false) =
        SearchPageResult(index, tops.map { hit(index, it) }, isLimitReached)

    /** Pages searched from page 3, wrapping around, as the engine does. */
    private val results =
        listOf(page(3, 10f, 20f), page(4), page(0, 5f), page(1), page(2, 30f))
            .fold(SearchResults.Empty) { results, page -> results + page }

    @Test
    fun `occurrences are kept in document order whatever the order of the pages`() {
        assertThat(results.hits.map { it.pageIndex to it.bounds.top })
            .containsExactly(0 to 5f, 2 to 30f, 3 to 10f, 3 to 20f)
            .inOrder()
        assertThat(results.size).isEqualTo(4)
        assertThat(results.searchedPages).isEqualTo(5)
        assertThat(results.isLimitReached).isFalse()
    }

    @Test
    fun `the occurrences of a page are found by page`() {
        assertThat(results.onPage(3)).containsExactly(hit(3, 10f), hit(3, 20f)).inOrder()
        assertThat(results.onPage(1)).isEmpty()
        assertThat(results.onPage(9)).isEmpty()
        assertThat(SearchResults.Empty.onPage(0)).isEmpty()
    }

    @Test
    fun `a reference finds its occurrence and back`() {
        val ref = HitRef(pageIndex = 3, indexOnPage = 1)

        assertThat(results.indexOf(ref)).isEqualTo(3)
        assertThat(results.refAt(3)).isEqualTo(ref)
        assertThat(results[ref]).isEqualTo(hit(3, 20f))
        assertThat(results.refAt(0)).isEqualTo(HitRef(0, 0))
    }

    @Test
    fun `a reference to no occurrence is not found`() {
        assertThat(results.indexOf(HitRef(3, 2))).isEqualTo(-1)
        assertThat(results.indexOf(HitRef(1, 0))).isEqualTo(-1)
        assertThat(results.indexOf(HitRef(0, -1))).isEqualTo(-1)
        assertThat(results[HitRef(7, 0)]).isNull()
        assertThat(SearchResults.Empty.indexOf(HitRef(0, 0))).isEqualTo(-1)
    }

    @Test
    fun `the limit stays reached once a page reached it`() {
        val limited = results + page(5, 1f, isLimitReached = true) + page(6)

        assertThat(limited.isLimitReached).isTrue()
    }
}
