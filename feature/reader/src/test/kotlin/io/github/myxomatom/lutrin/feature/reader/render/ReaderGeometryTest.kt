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
        val doubled = PinchTransform.zoomAround(factor = 2f, focusX = 0f, focusY = 50f)
        assertThat(scrollAfterGesture(scroll = 100f, scale = doubled.scale, translation = doubled.translationY))
            .isEqualTo(250f)
        val tripled = PinchTransform.zoomAround(factor = 3f, focusX = 0f, focusY = 0f)
        assertThat(scrollAfterGesture(scroll = 0f, scale = tripled.scale, translation = tripled.translationY))
            .isEqualTo(0f)
    }

    @Test
    fun `pinch steps accumulate their factors instead of restarting from the initial zoom`() {
        // Regression: the gesture used to read the zoom of its first frame, so every step
        // zoomed by its own small factor from 100 % and the page shook instead of zooming.
        val transform =
            (1..10).fold(PinchTransform()) { current, _ ->
                current.step(
                    1.1f,
                    centroidX = 0f,
                    centroidY = 0f,
                    panX = 0f,
                    panY = 0f,
                    minScale = 0.25f,
                    maxScale = 8f,
                )
            }

        assertThat(transform.scale).isWithin(1e-3f).of(2.5937f)
    }

    @Test
    fun `a pinch keeps the point under the fingers under them`() {
        val transform =
            PinchTransform().step(
                2f,
                centroidX = 100f,
                centroidY = 200f,
                panX = 0f,
                panY = 0f,
                minScale = 0.25f,
                maxScale = 8f,
            )

        // The content point at (100, 200) is still drawn at (100, 200).
        assertThat(100f * transform.scale + transform.translationX).isEqualTo(100f)
        assertThat(200f * transform.scale + transform.translationY).isEqualTo(200f)
    }

    @Test
    fun `a pinch follows the fingers when they move`() {
        val transform =
            PinchTransform().step(
                1f,
                centroidX = 50f,
                centroidY = 50f,
                panX = 30f,
                panY = -20f,
                minScale = 0.25f,
                maxScale = 8f,
            )

        assertThat(transform).isEqualTo(PinchTransform(scale = 1f, translationX = 30f, translationY = -20f))
    }

    @Test
    fun `a pinch stops at the zoom limits`() {
        val transform =
            PinchTransform().step(
                100f,
                centroidX = 0f,
                centroidY = 0f,
                panX = 0f,
                panY = 0f,
                minScale = 0.5f,
                maxScale = 4f,
            )

        assertThat(transform.scale).isEqualTo(4f)
        assertThat(PinchTransform().isIdentity).isTrue()
        assertThat(transform.isIdentity).isFalse()
    }

    @Test
    fun `committing a zoom in moves to the page now under the top of the screen`() {
        // Pages of 1000 px at the new zoom; doubling from 600 px into page 2 lands 200 px into page 3.
        val position =
            listPositionAfterGesture(ListPosition(2, 600), scale = 2f, translationY = 0f, itemCount = 10) { 1000 }

        assertThat(position).isEqualTo(ListPosition(3, 200))
    }

    @Test
    fun `committing a zoom out moves back to the previous pages`() {
        // Zooming out around a point 400 px below the top: the offset becomes negative.
        val transform = PinchTransform.zoomAround(factor = 0.5f, focusX = 0f, focusY = 400f)
        val position =
            listPositionAfterGesture(ListPosition(3, 100), transform.scale, transform.translationY, itemCount = 10) {
                500
            }

        assertThat(position).isEqualTo(ListPosition(2, 350))
    }

    @Test
    fun `a committed position never goes before the first page or past the last one`() {
        assertThat(listPositionAfterGesture(ListPosition(0, 10), 0.5f, translationY = 300f, itemCount = 3) { 100 })
            .isEqualTo(ListPosition(0, 0))
        assertThat(listPositionAfterGesture(ListPosition(2, 50), 4f, translationY = 0f, itemCount = 3) { 100 })
            .isEqualTo(ListPosition(2, 200))
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
