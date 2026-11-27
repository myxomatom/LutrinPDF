// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.render

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.settings.FitMode
import org.junit.Test

class FitGeometryTest {
    private val a4 = PageSize(595f, 842f)

    @Test
    fun `the tallest aspect is the one of the tallest page`() {
        val sizes = listOf(PageSize(600f, 400f), a4, PageSize(300f, 600f))

        assertThat(tallestAspect(sizes)).isWithin(EPSILON).of(2f)
    }

    @Test
    fun `the tallest aspect of no page is a square`() {
        assertThat(tallestAspect(emptyList())).isEqualTo(1f)
    }

    @Test
    fun `fit width fills the width less the gutters in the continuous scroll`() {
        val width = continuousFitWidth(1000, 2000, FitMode.WIDTH, gutter = 20, spacing = 10, clearance = 0, 1.414f)

        assertThat(width).isEqualTo(960)
    }

    @Test
    fun `fit page keeps the width when a whole page fits the screen`() {
        // A4 on a tall phone: 960 px wide is 1357 px high, in 2000 px.
        val width = continuousFitWidth(1000, 2000, FitMode.PAGE, gutter = 20, spacing = 10, clearance = 0, 1.414f)

        assertThat(width).isEqualTo(960)
    }

    @Test
    fun `fit page narrows the pages so that the tallest one is whole in the screen`() {
        // A4 on a landscape screen: the height decides, less the two gaps and the floating bar.
        val aspect = 1.414f
        val width = continuousFitWidth(2000, 1000, FitMode.PAGE, gutter = 20, spacing = 10, clearance = 100, aspect)

        assertThat(width).isLessThan(1960)
        assertThat(width * aspect + 100 + 2 * 10).isAtMost(1000f)
        assertThat(width).isAtLeast(((1000 - 100 - 2 * 10) / aspect).toInt() - 1)
    }

    @Test
    fun `fit page uses the tallest page of a mixed document for every page`() {
        val sizes = listOf(a4, PageSize(300f, 900f))
        val aspect = tallestAspect(sizes)

        val width = continuousFitWidth(2000, 1000, FitMode.PAGE, gutter = 0, spacing = 0, clearance = 0, aspect)

        assertThat(width).isEqualTo(333)
    }

    @Test
    fun `fit page never gives an empty width`() {
        val tall = continuousFitWidth(10, 10, FitMode.PAGE, gutter = 20, spacing = 20, clearance = 5, 1000f)
        val wide = continuousFitWidth(10, 10, FitMode.WIDTH, gutter = 20, spacing = 20, clearance = 5, 1f)

        assertThat(tall).isEqualTo(1)
        assertThat(wide).isEqualTo(1)
    }

    @Test
    fun `a clearance larger than the screen leaves half of it for the page`() {
        val width = continuousFitWidth(2000, 1000, FitMode.PAGE, gutter = 0, spacing = 0, clearance = 5000, 1f)

        assertThat(width).isEqualTo(500)
    }

    @Test
    fun `page by page fit page is the whole page and fit width the width`() {
        // A4 in a landscape area: the whole page is narrower than the area, the width fit is not.
        assertThat(pagedFitWidth(a4, 2000, 1000, FitMode.PAGE, margin = 20))
            .isEqualTo(fitPageWidth(a4, 2000, 1000, 20))
        assertThat(pagedFitWidth(a4, 2000, 1000, FitMode.WIDTH, margin = 20)).isEqualTo(1960)
    }

    @Test
    fun `page by page fit width never gives an empty width`() {
        assertThat(pagedFitWidth(a4, 10, 10, FitMode.WIDTH, margin = 20)).isEqualTo(1)
    }

    private companion object {
        const val EPSILON = 0.001f
    }
}
