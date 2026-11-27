// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.AffineTransform
import io.github.myxomatom.lutrin.core.domain.pdf.PageRotation
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.RenderOptions
import org.junit.Test

/** The matrix given to MuPDF (ADR 0032): pure arithmetic, run on every system. */
class RenderTransformTest {
    private val page = PageSize(200f, 300f)

    private fun AffineTransform.apply(point: PdfPoint) = PdfPoint(
        a * point.x + c * point.y + e,
        b * point.x + d * point.y + f,
    )

    private fun spec(rotation: PageRotation, region: PdfRect, scale: Float) =
        RenderSpec(region, scale, width = 10, height = 10, options = RenderOptions(rotation = rotation))

    @Test
    fun `a straight page is shifted to its region and scaled`() {
        val transform = spec(PageRotation.NONE, PdfRect(50f, 100f, 150f, 200f), scale = 2f).transform(page)

        assertThat(transform).isEqualTo(AffineTransform(2f, 0f, 0f, 2f, -100f, -200f))
    }

    @Test
    fun `the top-left pixel shows the top-left corner of the region at every angle`() {
        PageRotation.entries.forEach { rotation ->
            val display = rotation.displaySize(page)
            val region = PdfRect(10f, 20f, display.width, display.height)
            val transform = spec(rotation, region, scale = 3f).transform(page)

            // The page point shown at the region's corner lands on pixel (0, 0).
            val corner = rotation.toPage(PdfPoint(region.left, region.top), page)
            assertThat(transform.apply(corner)).isEqualTo(PdfPoint(0f, 0f))
        }
    }

    @Test
    fun `a quarter turn draws the top of the page along the right edge`() {
        val display = PageRotation.QUARTER.displaySize(page)
        val transform = spec(PageRotation.QUARTER, PdfRect(0f, 0f, display.width, display.height), 1f).transform(page)

        assertThat(transform.apply(PdfPoint(0f, 0f))).isEqualTo(PdfPoint(300f, 0f))
        assertThat(transform.apply(PdfPoint(200f, 300f))).isEqualTo(PdfPoint(0f, 200f))
    }
}
