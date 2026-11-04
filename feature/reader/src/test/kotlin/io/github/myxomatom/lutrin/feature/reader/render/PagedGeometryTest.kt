// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.render

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import org.junit.Test

class PagedGeometryTest {
    @Test
    fun `a portrait page in a tall screen fits its width`() {
        // A4 in a 1000 x 2000 px phone screen, 20 px margins: the width is the limit.
        val width = fitPageWidth(PageSize(595f, 842f), viewportWidth = 1000, viewportHeight = 2000, margin = 20)

        assertThat(width).isEqualTo(960)
        assertThat(pageHeightPx(PageSize(595f, 842f), width)).isAtMost(2000 - 40)
    }

    @Test
    fun `a page taller than the screen fits its height`() {
        // A4 in a landscape screen: the height is the limit, the page is narrower than the screen.
        val page = PageSize(595f, 842f)
        val width = fitPageWidth(page, viewportWidth = 2000, viewportHeight = 1000, margin = 20)

        assertThat(width).isEqualTo((960 * 595f / 842f).toInt())
        assertThat(pageHeightPx(page, width)).isAtMost(960)
    }

    @Test
    fun `a landscape page in a portrait screen fits its width`() {
        val width = fitPageWidth(PageSize(842f, 595f), viewportWidth = 1000, viewportHeight = 2000, margin = 0)

        assertThat(width).isEqualTo(1000)
    }

    @Test
    fun `a fitted page is never less than one pixel wide`() {
        assertThat(
            fitPageWidth(PageSize(595f, 842f), viewportWidth = 10, viewportHeight = 10, margin = 20),
        ).isEqualTo(1)
        assertThat(fitPageWidth(PageSize(1f, 100_000f), viewportWidth = 1000, viewportHeight = 100, margin = 0))
            .isEqualTo(1)
    }

    @Test
    fun `zoomed area grows with the zoom and never shrinks below the viewport`() {
        assertThat(zoomedExtent(1000, 1f)).isEqualTo(1000)
        assertThat(zoomedExtent(1000, 2.5f)).isEqualTo(2500)
        assertThat(zoomedExtent(1000, 0.25f)).isEqualTo(1000)
    }
}
