// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchExcerpt
import io.github.myxomatom.lutrin.core.domain.pdf.SelectionMode
import org.junit.Test

/**
 * Selection and excerpts on synthetic pages: platform free, so these tests also run where the
 * MuPDF host library is not built (ADR 0013). Characters are 10 points wide and 12 points high.
 */
class PageTextTest {
    // "Le mot suivant" from x = 100 to 240, then "deux" from 100 to 140 on the next line.
    private val page = PageText(listOf(line("Le mot suivant", x = 100f, y = 100f), line("deux", x = 100f, y = 120f)))

    @Test
    fun `selects the characters between two points`() {
        val selection = page.select(0, PdfPoint(130f, 106f), PdfPoint(160f, 106f), SelectionMode.Characters)

        assertThat(selection.text).isEqualTo("mot")
        assertThat(selection.quads.single().bounds).isEqualTo(PdfRect(130f, 100f, 160f, 112f))
    }

    @Test
    fun `the order of the points does not matter`() {
        val forward = page.select(2, PdfPoint(130f, 106f), PdfPoint(160f, 106f), SelectionMode.Characters)
        val backward = page.select(2, PdfPoint(160f, 106f), PdfPoint(130f, 106f), SelectionMode.Characters)

        assertThat(backward).isEqualTo(forward)
        assertThat(backward.pageIndex).isEqualTo(2)
    }

    @Test
    fun `a long press selects the word under the finger`() {
        val press = PdfPoint(145f, 106f) // Inside the "o" of "mot".

        val selection = page.select(0, press, press, SelectionMode.Words)

        assertThat(selection.text).isEqualTo("mot")
    }

    @Test
    fun `words mode grows both ends to whole words`() {
        val selection = page.select(0, PdfPoint(115f, 106f), PdfPoint(175f, 106f), SelectionMode.Words)

        assertThat(selection.text).isEqualTo("Le mot suivant")
    }

    @Test
    fun `lines mode selects the whole line`() {
        val press = PdfPoint(145f, 106f)

        val selection = page.select(0, press, press, SelectionMode.Lines)

        assertThat(selection.text).isEqualTo("Le mot suivant")
        assertThat(selection.quads.single().bounds).isEqualTo(PdfRect(100f, 100f, 240f, 112f))
    }

    @Test
    fun `a selection over two lines has one quad per line`() {
        val selection = page.select(0, PdfPoint(130f, 106f), PdfPoint(120f, 126f), SelectionMode.Characters)

        assertThat(selection.text).isEqualTo("mot suivant\nde")
        assertThat(selection.quads.map { it.bounds })
            .containsExactly(PdfRect(130f, 100f, 240f, 112f), PdfRect(100f, 120f, 120f, 132f))
            .inOrder()
    }

    @Test
    fun `points beside the text select up to the edge of the closest line`() {
        val selection = page.select(0, PdfPoint(0f, 0f), PdfPoint(595f, 842f), SelectionMode.Characters)

        assertThat(selection.text).isEqualTo("Le mot suivant\ndeux")
    }

    @Test
    fun `two equal points select nothing in characters mode`() {
        val press = PdfPoint(145f, 106f)

        assertThat(page.select(0, press, press, SelectionMode.Characters).isEmpty).isTrue()
    }

    @Test
    fun `a page without text selects nothing`() {
        val empty = PageText(emptyList())

        val selection = empty.select(1, PdfPoint(0f, 0f), PdfPoint(595f, 842f), SelectionMode.Lines)

        assertThat(empty.isEmpty).isTrue()
        assertThat(selection.isEmpty).isTrue()
        assertThat(selection.pageIndex).isEqualTo(1)
    }

    @Test
    fun `a long press on right-to-left text selects the whole word in reading order`() {
        val arabic = PageText(listOf(line("مرحبا بكم", x = 500f, y = 200f, rightToLeft = true)))
        // The first letter is the rightmost one: from x = 490 to 500.
        val press = PdfPoint(495f, 206f)

        val selection = arabic.select(0, press, press, SelectionMode.Words)

        assertThat(selection.text).isEqualTo("مرحبا")
        assertThat(selection.quads.single().bounds).isEqualTo(PdfRect(450f, 200f, 500f, 212f))
    }

    @Test
    fun `dragging across right-to-left text selects it in reading order`() {
        val arabic = PageText(listOf(line("مرحبا بكم", x = 500f, y = 200f, rightToLeft = true)))

        val selection = arabic.select(0, PdfPoint(500f, 206f), PdfPoint(410f, 206f), SelectionMode.Characters)

        assertThat(selection.text).isEqualTo("مرحبا بكم")
    }

    @Test
    fun `a quad follows text written down the page`() {
        val down = PdfPoint(0f, 1f)
        val glyphs = "abc".mapIndexed { index, char ->
            Glyph(char.code, PdfQuad.of(PdfRect(300f, 100f + index * 10f, 312f, 110f + index * 10f)))
        }
        val vertical = PageText(listOf(GlyphLine(glyphs, direction = down)))

        val selection = vertical.select(0, PdfPoint(306f, 115f), PdfPoint(306f, 115f), SelectionMode.Lines)

        val quad = selection.quads.single()
        assertThat(selection.text).isEqualTo("abc")
        assertThat(quad.bounds).isEqualTo(PdfRect(300f, 100f, 312f, 130f))
        // The top of the text faces the right of the page, its start the top of the page.
        assertThat(quad.upperLeft).isEqualTo(PdfPoint(312f, 100f))
        assertThat(quad.lowerRight).isEqualTo(PdfPoint(300f, 130f))
    }

    @Test
    fun `an excerpt joins a word cut by a hyphen at the end of a line`() {
        val text = PageText(
            listOf(line("la biblio-", x = 100f, y = 100f), line("thèque municipale", x = 100f, y = 120f)),
        )
        val areas = listOf(PdfRect(130f, 100f, 200f, 112f), PdfRect(100f, 120f, 160f, 132f))

        val excerpt = text.excerpt(areas)

        assertThat(excerpt).isEqualTo(SearchExcerpt("la ", "bibliothèque", " municipale"))
    }

    @Test
    fun `an excerpt leaves out the spaces a hit area also covers`() {
        // From the space before "mot" to the space after it.
        val excerpt = page.excerpt(listOf(PdfRect(120f, 100f, 170f, 112f)))

        assertThat(excerpt).isEqualTo(SearchExcerpt("Le ", "mot", " suivant deux"))
    }

    @Test
    fun `an excerpt keeps a bounded context`() {
        val long = PageText(listOf(line("a".repeat(30) + " un deux trois MOT quatre cinq " + "b".repeat(30), 0f, 0f)))
        val start = 45f * 10f

        val excerpt = long.excerpt(listOf(PdfRect(start, 0f, start + 30f, 12f)), maxContext = 10)

        // The last 10 characters before the match are "eux trois ": "deux" is cut, so dropped.
        assertThat(excerpt).isEqualTo(SearchExcerpt("trois ", "MOT", " quatre"))
    }

    @Test
    fun `no excerpt when the areas cover no character`() {
        assertThat(page.excerpt(listOf(PdfRect(400f, 400f, 450f, 450f)))).isNull()
    }

    private companion object {
        const val WIDTH = 10f
        const val HEIGHT = 12f

        /** A horizontal line; [x] is its left edge, or its right edge for right-to-left text. */
        fun line(text: String, x: Float, y: Float, rightToLeft: Boolean = false): GlyphLine {
            val glyphs = text.codePoints().toArray().mapIndexed { index, codePoint ->
                val left = if (rightToLeft) x - (index + 1) * WIDTH else x + index * WIDTH
                Glyph(codePoint, PdfQuad.of(PdfRect(left, y, left + WIDTH, y + HEIGHT)), rightToLeft)
            }
            return GlyphLine(glyphs)
        }
    }
}
