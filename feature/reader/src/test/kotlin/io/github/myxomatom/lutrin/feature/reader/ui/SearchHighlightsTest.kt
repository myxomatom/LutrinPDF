// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
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
