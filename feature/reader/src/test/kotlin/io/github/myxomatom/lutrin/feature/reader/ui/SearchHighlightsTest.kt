// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageRotation
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchExcerpt
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SearchPageResult
import io.github.myxomatom.lutrin.feature.reader.search.HitRef
import io.github.myxomatom.lutrin.feature.reader.search.SearchResults
import org.junit.Test

/** What the pages highlight ([SearchHighlights]) and how the excerpt card writes a result. */
class SearchHighlightsTest {
    private fun quad(top: Float) = PdfQuad.of(PdfRect(10f, top, 90f, top + 12f))

    private val results =
        SearchResults.Empty +
            SearchPageResult(1, listOf(SearchHit(1, listOf(quad(10f))), SearchHit(1, listOf(quad(40f), quad(52f))))) +
            SearchPageResult(2, emptyList())

    @Test
    fun `the current result is drawn apart from the others of its page`() {
        val highlights = SearchHighlights(results, current = HitRef(1, 1)).forPage(1)

        assertThat(
            highlights,
        ).isEqualTo(PageHighlights(others = listOf(quad(10f)), current = listOf(quad(40f), quad(52f))))
    }

    @Test
    fun `on turned pages, the results are drawn where their words are shown`() {
        val pages = List(3) { PageSize(100f, 200f) }
        val frames = PageFrames(PageRotation.HALF, pages)

        val highlights = SearchHighlights(results, current = HitRef(1, 1), frames).forPage(1)

        // Upside down: (10, 10)-(90, 22) of the page is drawn at (10, 178)-(90, 190).
        assertThat(highlights?.others?.single()?.bounds).isEqualTo(PdfRect(10f, 178f, 90f, 190f))
        assertThat(highlights?.current?.map { it.bounds })
            .isEqualTo(listOf(quad(40f), quad(52f)).map { PageRotation.HALF.toDisplay(it.bounds, pages[1]) })
    }

    @Test
    fun `a page whose result is not the current one draws them all alike`() {
        val highlights = SearchHighlights(results, current = HitRef(5, 0)).forPage(1)

        assertThat(highlights?.others).containsExactly(quad(10f), quad(40f), quad(52f))
        assertThat(highlights?.current).isEmpty()
    }

    @Test
    fun `a page without result draws nothing`() {
        assertThat(SearchHighlights(results, HitRef(1, 0)).forPage(2)).isNull()
        assertThat(SearchHighlights.None.forPage(1)).isNull()
    }

    @Test
    fun `the excerpt shows ellipses around a context and the match in its style`() {
        val bold = SpanStyle(fontWeight = FontWeight.Bold)

        val text = excerptText(SearchExcerpt("demander la ", "résiliation", " avec un"), "…", bold)

        assertThat(text.text).isEqualTo("… demander la résiliation avec un …")
        val match = text.spanStyles.single()
        assertThat(text.text.substring(match.start, match.end)).isEqualTo("résiliation")
        assertThat(match.item).isEqualTo(bold)
    }

    @Test
    fun `an excerpt without context has no ellipsis`() {
        val text = excerptText(SearchExcerpt("", "Lutrin", ""), "…", SpanStyle())

        assertThat(text.text).isEqualTo("Lutrin")
    }
}
