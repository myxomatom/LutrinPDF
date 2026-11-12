// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfTextTest {
    @Test
    fun `search is insensitive to case and accents by default and stops at 1000 results`() {
        val options = SearchOptions()

        assertThat(options.matchCase).isFalse()
        assertThat(options.matchAccents).isFalse()
        assertThat(options.maxHits).isEqualTo(1_000)
    }

    @Test
    fun `search options reject a limit that is not positive`() {
        assertThrows(IllegalArgumentException::class.java) { SearchOptions(maxHits = 0) }
    }

    @Test
    fun `an excerpt keeps short context whole`() {
        val text = "la bibliothèque municipale"

        val excerpt = SearchExcerpt.around(text, matchStart = 3, matchEnd = 15)

        assertThat(excerpt).isEqualTo(SearchExcerpt("la ", "bibliothèque", " municipale"))
    }

    @Test
    fun `an excerpt cuts long context on whole words`() {
        val text = "un deux trois quatre MOT cinq six sept huit"

        val excerpt = SearchExcerpt.around(text, matchStart = 21, matchEnd = 24, maxContext = 10)

        // The last 10 characters, "is quatre ", would cut "trois": the partial word is dropped.
        assertThat(excerpt.before).isEqualTo("quatre ")
        assertThat(excerpt.match).isEqualTo("MOT")
        assertThat(excerpt.after).isEqualTo(" cinq six")
    }

    @Test
    fun `an excerpt keeps a word that starts or ends exactly at the cut`() {
        val text = "abc defg MOT hijk lmn"

        val excerpt = SearchExcerpt.around(text, matchStart = 9, matchEnd = 12, maxContext = 5)

        assertThat(excerpt.before).isEqualTo("defg ")
        assertThat(excerpt.after).isEqualTo(" hijk")
    }

    @Test
    fun `an excerpt keeps a cut word when no space allows a cleaner cut`() {
        val text = "anticonstitutionnellement MOT"

        val excerpt = SearchExcerpt.around(text, matchStart = 26, matchEnd = 29, maxContext = 8)

        assertThat(excerpt.before).hasLength(8)
        assertThat(excerpt.after).isEmpty()
    }

    @Test
    fun `an excerpt collapses whitespace and trims its outer ends`() {
        val text = "  avant\n\tle   MOT\n après  "

        val excerpt = SearchExcerpt.around(text, matchStart = 14, matchEnd = 17)

        assertThat(excerpt).isEqualTo(SearchExcerpt("avant le ", "MOT", " après"))
    }

    @Test
    fun `an excerpt rejects a match outside the text`() {
        assertThrows(IllegalArgumentException::class.java) { SearchExcerpt.around("abc", 2, 5) }
        assertThrows(IllegalArgumentException::class.java) { SearchExcerpt.around("abc", 2, 1) }
        assertThrows(IllegalArgumentException::class.java) { SearchExcerpt.around("abc", 0, 1, maxContext = -1) }
    }

    @Test
    fun `a search hit is bounded by all its areas`() {
        val hit = SearchHit(
            pageIndex = 0,
            areas = listOf(PdfQuad.of(PdfRect(300f, 100f, 400f, 112f)), PdfQuad.of(PdfRect(72f, 120f, 130f, 132f))),
        )

        assertThat(hit.bounds).isEqualTo(PdfRect(72f, 100f, 400f, 132f))
    }

    @Test
    fun `a search hit needs an area`() {
        assertThrows(IllegalArgumentException::class.java) { SearchHit(0, emptyList()) }
    }

    @Test
    fun `a destination to an area keeps its size`() {
        val destination = PdfDestination(pageIndex = 1, x = 200f, y = 100f, width = 150f, height = 80f)

        assertThat(destination.width).isEqualTo(150f)
        assertThat(PdfDestination(pageIndex = 1).height).isNull()
    }

    @Test
    fun `an empty selection has no text and no area`() {
        val selection = TextSelection.empty(pageIndex = 4)

        assertThat(selection.isEmpty).isTrue()
        assertThat(selection.quads).isEmpty()
        assertThat(selection.start).isNull()
        assertThat(selection.pageIndex).isEqualTo(4)
    }

    @Test
    fun `a selection with text is not empty`() {
        val selection = TextSelection(0, listOf(PdfQuad.of(PdfRect(0f, 0f, 10f, 10f))), "mot")

        assertThat(selection.isEmpty).isFalse()
    }
}
