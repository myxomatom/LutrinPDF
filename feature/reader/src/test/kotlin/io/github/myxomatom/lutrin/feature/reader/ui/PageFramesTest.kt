// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageRotation
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import org.junit.Test

class PageFramesTest {
    // A portrait page 200 × 300 points, and a landscape one.
    private val pages = listOf(PageSize(200f, 300f), PageSize(400f, 100f))

    @Test
    fun `straight frames change nothing`() {
        val frames = PageFrames(PageRotation.NONE, pages)
        val hit = PageHit(0, PdfPoint(20f, 30f), pointsPerPixel = 0.5f)
        val selection =
            TextSelection(0, listOf(PdfQuad.of(PdfRect(1f, 2f, 3f, 4f))), "a", PdfPoint(1f, 4f), PdfPoint(3f, 4f))

        assertThat(frames.displaySizes).isSameInstanceAs(pages)
        assertThat(frames.toPage(hit)).isEqualTo(hit)
        assertThat(frames.toDisplay(selection)).isEqualTo(selection)
        assertThat(frames.displayOffset(120f)).isEqualTo(120f)
    }

    @Test
    fun `turned a quarter, every page shows with its sides swapped`() {
        val frames = PageFrames(PageRotation.QUARTER, pages)

        assertThat(frames.displaySizes).containsExactly(PageSize(300f, 200f), PageSize(100f, 400f)).inOrder()
    }

    @Test
    fun `a tap on a turned page finds the link drawn under the finger, at every angle`() {
        val link = PdfLink.External(PdfRect(10f, 20f, 50f, 40f), "https://example.org/")
        PageRotation.entries.forEach { rotation ->
            val frames = PageFrames(rotation, pages)
            // Where the view draws the middle of the link, in display space.
            val drawn = frames.toDisplay(0, link.bounds)
            val tap = PageHit(0, PdfPoint((drawn.left + drawn.right) / 2, (drawn.top + drawn.bottom) / 2), 1f)

            val hit = frames.toPage(tap)

            assertThat(hit?.point).isEqualTo(PdfPoint(30f, 30f))
            assertThat(hit?.let { linkAt(listOf(link), it.point, tolerance = 0f) }).isEqualTo(link)
        }
    }

    @Test
    fun `a tap on a page the document does not have finds nothing`() {
        val frames = PageFrames(PageRotation.HALF, pages)

        assertThat(frames.toPage(PageHit(7, PdfPoint(1f, 1f), 1f))).isNull()
        assertThat(frames.toPage(null)).isNull()
    }

    @Test
    fun `a selection is drawn where its text is shown, its handles at its turned ends`() {
        val frames = PageFrames(PageRotation.THREE_QUARTERS, pages)
        val word = PdfQuad.of(PdfRect(10f, 20f, 50f, 40f))
        val selection = TextSelection(0, listOf(word), "mot", start = PdfPoint(10f, 40f), end = PdfPoint(50f, 40f))

        val shown = frames.toDisplay(selection)

        assertThat(shown.quads.single().bounds).isEqualTo(PdfRect(20f, 150f, 40f, 190f))
        assertThat(shown.start).isEqualTo(PdfPoint(40f, 190f))
        assertThat(shown.end).isEqualTo(PdfPoint(40f, 150f))
        assertThat(shown.text).isEqualTo("mot")
        // A handle dragged to where the end is shown selects up to the end in the document.
        assertThat(frames.toPage(0, PdfPoint(40f, 150f))).isEqualTo(PdfPoint(50f, 40f))
    }

    @Test
    fun `search hits are drawn on the turned page`() {
        val frames = PageFrames(PageRotation.HALF, pages)
        val hit = PdfQuad.of(PdfRect(0f, 0f, 100f, 10f))

        assertThat(frames.toDisplay(1, listOf(hit)).single().bounds).isEqualTo(PdfRect(300f, 90f, 400f, 100f))
        assertThat(frames.toDisplay(1, PdfRect(0f, 0f, 100f, 10f))).isEqualTo(PdfRect(300f, 90f, 400f, 100f))
    }

    @Test
    fun `a destination on a turned page shows the top of the shown page`() {
        assertThat(PageFrames(PageRotation.QUARTER, pages).displayOffset(120f)).isEqualTo(0f)
        assertThat(PageFrames(PageRotation.HALF, pages).displayOffset(120f)).isEqualTo(0f)
    }
}
