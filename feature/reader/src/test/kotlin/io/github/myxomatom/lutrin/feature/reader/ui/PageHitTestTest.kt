// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.feature.reader.render.pageWidthPx
import org.junit.Test

class PageHitTestTest {
    // 200 × 400 points: at 400 px wide, 2 px per point.
    private val page = PageSize(200f, 400f)
    private val pages = listOf(page, page, page)

    private fun assertPoint(hit: PageHit?, pageIndex: Int, x: Float, y: Float) {
        assertThat(hit).isNotNull()
        assertThat(hit?.pageIndex).isEqualTo(pageIndex)
        assertThat(hit?.point?.x).isWithin(TOLERANCE).of(x)
        assertThat(hit?.point?.y).isWithin(TOLERANCE).of(y)
    }

    // Continuous scroll.

    @Test
    fun `continuous, a tap on the first page at zoom 1 gives its point`() {
        // Viewport 432 px, 16 px margins: page 400 px wide, 800 px high, left edge at 16.
        val placements = continuousPlacements(listOf(VisibleItem(0, 8)), pages, pageWidthPx(400, 1f), 432, 0)

        assertPoint(hitTest(16f + 100f, 8f + 50f, placements, pages), 0, 50f, 25f)
    }

    @Test
    fun `continuous, the gap between two pages hits no page`() {
        val items = listOf(VisibleItem(0, 8), VisibleItem(1, 816))
        val placements = continuousPlacements(items, pages, pageWidthPx(400, 1f), 432, 0)

        assertThat(hitTest(200f, 812f, placements, pages)).isNull()
        assertPoint(hitTest(216f, 816f, placements, pages), 1, 100f, 0f)
    }

    @Test
    fun `continuous, the side margins hit no page`() {
        val placements = continuousPlacements(listOf(VisibleItem(0, 0)), pages, pageWidthPx(400, 1f), 432, 0)

        assertThat(hitTest(10f, 100f, placements, pages)).isNull()
        assertThat(hitTest(420f, 100f, placements, pages)).isNull()
    }

    @Test
    fun `continuous, a page scrolled past the top keeps its coordinates`() {
        // The second page starts 300 px above the reading area.
        val placements = continuousPlacements(listOf(VisibleItem(1, -300)), pages, pageWidthPx(400, 1f), 432, 0)

        assertPoint(hitTest(16f, 100f, placements, pages), 1, 0f, 200f)
    }

    @Test
    fun `continuous, zoomed in and scrolled sideways`() {
        // Zoom 2: page 800 px wide (4 px per point), content 864 px, page left edge at 32.
        // Scrolled 232 px to the right: the page left edge is at -200.
        val placements = continuousPlacements(listOf(VisibleItem(0, 0)), pages, pageWidthPx(400, 2f), 864, 232)

        val hit = hitTest(200f, 400f, placements, pages)

        assertPoint(hit, 0, 100f, 100f)
        assertThat(hit?.pointsPerPixel).isWithin(TOLERANCE).of(0.25f)
    }

    @Test
    fun `continuous, zoomed out the page is centred in the viewport`() {
        // Zoom 0.5: page 200 px wide in a content of 432 px, left edge at 116.
        val placements = continuousPlacements(listOf(VisibleItem(0, 0)), pages, pageWidthPx(400, 0.5f), 432, 0)

        assertPoint(hitTest(116f + 100f, 50f, placements, pages), 0, 100f, 50f)
        assertThat(hitTest(100f, 50f, placements, pages)).isNull()
    }

    @Test
    fun `continuous, an item of an unknown page is ignored`() {
        val placements = continuousPlacements(listOf(VisibleItem(7, 0)), pages, pageWidthPx(400, 1f), 432, 0)

        assertThat(placements).isEmpty()
    }

    // Page by page.

    @Test
    fun `paged, the whole page is centred above the clearance at zoom 1`() {
        // Viewport 600 × 1000, clearance 100: area 600 × 900; page fits 868 px high minus margins.
        val layout = pagedPageLayout(page, IntSize(600, 1000), 100, 16, 1f)
        // Height available 900 - 32 = 868 → width 434; width available 568: fit width 434.
        assertThat(layout.fitWidth).isEqualTo(434)
        val placement = layout.placement(0, page, 1f, 0, 0)

        assertThat(placement.left).isEqualTo(83f)
        assertThat(placement.top).isEqualTo(16f)
        assertPoint(hitTest(83f + 217f, 16f + 434f, listOf(placement), pages), 0, 100f, 200f)
    }

    @Test
    fun `paged, outside the page hits nothing`() {
        val placement = pagedPageLayout(page, IntSize(600, 1000), 100, 16, 1f).placement(2, page, 1f, 0, 0)

        assertThat(hitTest(40f, 500f, listOf(placement), pages)).isNull()
        assertThat(hitTest(300f, 950f, listOf(placement), pages)).isNull()
    }

    @Test
    fun `paged, zoomed in and scrolled in both directions`() {
        val layout = pagedPageLayout(page, IntSize(600, 1000), 100, 16, 2f)
        assertThat(layout.areaWidth).isEqualTo(1200)
        assertThat(layout.areaHeight).isEqualTo(1800)
        // Page 868 × 1736, left edge at 166, top edge at 32; scrolled by (366, 232).
        val placement = layout.placement(0, page, 2f, 366, 232)

        assertPoint(hitTest(0f, 0f, listOf(placement), pages), 0, 200f * 200f / 868f, 200f * 400f / 1736f)
    }

    @Test
    fun `paged, the clearance never takes more than half the viewport`() {
        val layout = pagedPageLayout(page, IntSize(600, 300), 400, 16, 1f)

        assertThat(layout.clearance).isEqualTo(150)
        assertThat(layout.areaHeight).isEqualTo(150)
    }

    // Links.

    private val internal = PdfLink.Internal(PdfRect(10f, 10f, 60f, 30f), PdfDestination(2))
    private val external = PdfLink.External(PdfRect(10f, 100f, 60f, 120f), "https://example.org")

    @Test
    fun `the link whose area holds the point is found`() {
        assertThat(linkAt(listOf(internal, external), PdfPoint(20f, 110f), 0f)).isEqualTo(external)
        assertThat(linkAt(listOf(internal, external), PdfPoint(10f, 10f), 0f)).isEqualTo(internal)
    }

    @Test
    fun `a point away from every link finds none`() {
        assertThat(linkAt(listOf(internal, external), PdfPoint(150f, 60f), 5f)).isNull()
        assertThat(linkAt(emptyList(), PdfPoint(20f, 20f), 5f)).isNull()
    }

    @Test
    fun `a point just beside a link finds it within the tolerance`() {
        assertThat(linkAt(listOf(internal), PdfPoint(63f, 20f), 4f)).isEqualTo(internal)
        assertThat(linkAt(listOf(internal), PdfPoint(63f, 20f), 2f)).isNull()
    }

    @Test
    fun `between two links the nearest one wins`() {
        // 60 points between the bottom of the first link and the top of the second one.
        assertThat(linkAt(listOf(internal, external), PdfPoint(30f, 90f), 20f)).isEqualTo(external)
        assertThat(linkAt(listOf(internal, external), PdfPoint(30f, 40f), 20f)).isEqualTo(internal)
    }

    @Test
    fun `overlapping links give the first one in document order`() {
        val overlapping = PdfLink.External(PdfRect(0f, 0f, 100f, 100f), "https://example.org/b")

        assertThat(linkAt(listOf(internal, overlapping), PdfPoint(20f, 20f), 0f)).isEqualTo(internal)
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
