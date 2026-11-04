// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import org.junit.Test

class ReaderGeometryTest {
    @Test
    fun `pinch zoom multiplies and stays between 25 and 800 percent`() {
        assertThat(ZoomLevels.pinched(1f, 1.5f)).isEqualTo(1.5f)
        assertThat(ZoomLevels.pinched(6f, 2f)).isEqualTo(ZoomLevels.MAX)
        assertThat(ZoomLevels.pinched(0.5f, 0.1f)).isEqualTo(ZoomLevels.MIN)
    }

    @Test
    fun `double tap zooms in from the fitted width and back from any zoom`() {
        assertThat(ZoomLevels.doubleTapped(ZoomLevels.FIT_WIDTH)).isEqualTo(ZoomLevels.DOUBLE_TAP)
        assertThat(ZoomLevels.doubleTapped(0.5f)).isEqualTo(ZoomLevels.DOUBLE_TAP)
        assertThat(ZoomLevels.doubleTapped(3f)).isEqualTo(ZoomLevels.FIT_WIDTH)
    }

    @Test
    fun `render width is rounded up to the next step`() {
        assertThat(renderWidthFor(1)).isEqualTo(RENDER_WIDTH_STEP_PX)
        assertThat(renderWidthFor(RENDER_WIDTH_STEP_PX)).isEqualTo(RENDER_WIDTH_STEP_PX)
        assertThat(renderWidthFor(RENDER_WIDTH_STEP_PX + 1)).isEqualTo(RENDER_WIDTH_STEP_PX * 2)
        assertThat(renderWidthFor(0)).isEqualTo(RENDER_WIDTH_STEP_PX)
    }

    @Test
    fun `render size keeps the aspect ratio`() {
        assertThat(PageRenderer.renderSize(PageSize(100f, 200f), 500)).isEqualTo(500 to 1000)
    }

    @Test
    fun `render size is capped for a huge zoom`() {
        val (width, height) = PageRenderer.renderSize(PageSize(595f, 842f), 100_000)

        assertThat(width.toLong() * height).isAtMost(PageRenderer.MAX_PIXELS.toLong() + width)
        assertThat(height.toFloat() / width).isWithin(0.01f).of(842f / 595f)
    }

    @Test
    fun `render size is at least one pixel`() {
        assertThat(PageRenderer.renderSize(PageSize(1000f, 1f), 0)).isEqualTo(1 to 1)
    }
}
