// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.render

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import org.junit.Test

class ReaderGeometryTest {
    private val a4 = PageSize(595f, 842f)

    @Test
    fun `zoom is clamped between 25 and 800 percent`() {
        assertThat(clampZoom(0.1f)).isEqualTo(0.25f)
        assertThat(clampZoom(3f)).isEqualTo(3f)
        assertThat(clampZoom(20f)).isEqualTo(8f)
    }

    @Test
    fun `double tap zooms in from fit width and back out when zoomed`() {
        assertThat(doubleTapZoom(1f)).isEqualTo(DOUBLE_TAP_ZOOM)
        assertThat(doubleTapZoom(0.5f)).isEqualTo(DOUBLE_TAP_ZOOM)
        assertThat(doubleTapZoom(3f)).isEqualTo(1f)
    }

    @Test
    fun `page keeps its aspect ratio`() {
        assertThat(pageWidthPx(1000, 1.5f)).isEqualTo(1500)
        assertThat(pageHeightPx(a4, 595)).isEqualTo(842)
        assertThat(pageWidthPx(1000, 0f)).isEqualTo(1)
    }

    @Test
    fun `base rendering never exceeds the viewport and moves in steps when zoomed out`() {
        assertThat(baseRenderWidth(1000, 1f)).isEqualTo(1000)
        assertThat(baseRenderWidth(1000, 4f)).isEqualTo(1000)
        assertThat(baseRenderWidth(1000, 0.5f)).isEqualTo(500)
        // 0.51 and 0.6 fall in the same step: no new rendering while pinching between them.
        assertThat(baseRenderWidth(1000, 0.51f)).isEqualTo(baseRenderWidth(1000, 0.6f))
    }

    @Test
    fun `zooming keeps the focal point still`() {
        // Content point at 100 + 50 = 150 px; after doubling it is at 300 px, still under the 50 px focus.
        assertThat(scrollAfterZoom(scroll = 100f, focus = 50f, factor = 2f)).isEqualTo(250f)
        assertThat(scrollAfterZoom(scroll = 0f, focus = 0f, factor = 3f)).isEqualTo(0f)
    }

    @Test
    fun `visible part of a page partly scrolled out`() {
        val part =
            visiblePart(
                pageLeft = -200f,
                pageTop = -300f,
                pageWidth = 2000,
                pageHeight = 2800,
                viewportWidth = 1000,
                viewportHeight = 1500,
            )

        assertThat(part).isEqualTo(PixelRect(200, 300, 1200, 1800))
    }

    @Test
    fun `visible part is clipped to the page and null when off-screen`() {
        assertThat(visiblePart(100f, 1000f, 800, 1200, 1000, 1500)).isEqualTo(PixelRect(0, 0, 800, 500))
        assertThat(visiblePart(0f, 1600f, 800, 1200, 1000, 1500)).isNull()
        assertThat(visiblePart(0f, -1300f, 800, 1200, 1000, 1500)).isNull()
    }

    @Test
    fun `pixel part converts to page points`() {
        val rect = PixelRect(0, 0, 1190, 842).toPageRect(a4, pageWidthPx = 1190)

        assertThat(rect).isEqualTo(PdfRect(0f, 0f, 595f, 421f))
        assertThat(PixelRect(1000, 1600, 1300, 1700).toPageRect(a4, 1190).right).isEqualTo(595f)
    }

    @Test
    fun `current page switches when the next page fills the middle`() {
        assertThat(
            currentPage(firstVisible = 2, firstVisibleOffset = 100, firstVisibleHeight = 1000, pageCount = 10),
        ).isEqualTo(2)
        assertThat(
            currentPage(firstVisible = 2, firstVisibleOffset = 600, firstVisibleHeight = 1000, pageCount = 10),
        ).isEqualTo(3)
        assertThat(
            currentPage(firstVisible = 9, firstVisibleOffset = 900, firstVisibleHeight = 1000, pageCount = 10),
        ).isEqualTo(9)
        assertThat(
            currentPage(firstVisible = 0, firstVisibleOffset = 0, firstVisibleHeight = 0, pageCount = 0),
        ).isEqualTo(0)
    }

    @Test
    fun `pixel rect reports its size`() {
        assertThat(PixelRect(10, 20, 110, 70).width).isEqualTo(100)
        assertThat(PixelRect(10, 20, 110, 70).height).isEqualTo(50)
    }
}
