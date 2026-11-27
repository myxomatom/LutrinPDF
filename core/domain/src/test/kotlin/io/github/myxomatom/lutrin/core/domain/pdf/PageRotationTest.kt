// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageRotationTest {
    // A portrait page, 200 wide and 300 high, and a point near its top-left corner.
    private val page = PageSize(200f, 300f)
    private val point = PdfPoint(20f, 30f)

    @Test
    fun `each tap turns a quarter clockwise and the fourth comes back`() {
        assertThat(PageRotation.NONE.next).isEqualTo(PageRotation.QUARTER)
        assertThat(PageRotation.QUARTER.next).isEqualTo(PageRotation.HALF)
        assertThat(PageRotation.HALF.next).isEqualTo(PageRotation.THREE_QUARTERS)
        assertThat(PageRotation.THREE_QUARTERS.next).isEqualTo(PageRotation.NONE)
        assertThat(PageRotation.entries.map { it.degrees }).containsExactly(0, 90, 180, 270).inOrder()
    }

    @Test
    fun `a quarter turn swaps width and height, a half turn does not`() {
        assertThat(PageRotation.NONE.displaySize(page)).isEqualTo(page)
        assertThat(PageRotation.QUARTER.displaySize(page)).isEqualTo(PageSize(300f, 200f))
        assertThat(PageRotation.HALF.displaySize(page)).isEqualTo(page)
        assertThat(PageRotation.THREE_QUARTERS.displaySize(page)).isEqualTo(PageSize(300f, 200f))
    }

    @Test
    fun `no rotation leaves every point where it is`() {
        assertThat(PageRotation.NONE.toDisplay(point, page)).isEqualTo(point)
        assertThat(PageRotation.NONE.toPage(point, page)).isEqualTo(point)
    }

    @Test
    fun `at 90 degrees the top-left corner goes to the top-right of the display`() {
        val rotation = PageRotation.QUARTER

        // Near the top of the page: near the right edge of the 300-wide turned page.
        assertThat(rotation.toDisplay(point, page)).isEqualTo(PdfPoint(270f, 20f))
        assertThat(rotation.toDisplay(PdfPoint(0f, 0f), page)).isEqualTo(PdfPoint(300f, 0f))
        assertThat(rotation.toDisplay(PdfPoint(200f, 300f), page)).isEqualTo(PdfPoint(0f, 200f))
    }

    @Test
    fun `at 180 degrees the page is upside down`() {
        assertThat(PageRotation.HALF.toDisplay(point, page)).isEqualTo(PdfPoint(180f, 270f))
    }

    @Test
    fun `at 270 degrees the top-left corner goes to the bottom-left of the display`() {
        val rotation = PageRotation.THREE_QUARTERS

        assertThat(rotation.toDisplay(point, page)).isEqualTo(PdfPoint(30f, 180f))
        assertThat(rotation.toDisplay(PdfPoint(0f, 0f), page)).isEqualTo(PdfPoint(0f, 200f))
    }

    @Test
    fun `toPage undoes toDisplay at every angle`() {
        val points = listOf(PdfPoint(0f, 0f), point, PdfPoint(200f, 300f), PdfPoint(123.5f, 7.25f))
        PageRotation.entries.forEach { rotation ->
            points.forEach { original ->
                assertThat(rotation.toPage(rotation.toDisplay(original, page), page)).isEqualTo(original)
            }
        }
    }

    @Test
    fun `every turned point stays inside the turned page`() {
        val corners = listOf(PdfPoint(0f, 0f), PdfPoint(200f, 0f), PdfPoint(0f, 300f), PdfPoint(200f, 300f))
        PageRotation.entries.forEach { rotation ->
            val size = rotation.displaySize(page)
            val turned = corners.map { rotation.toDisplay(it, page) }

            assertThat(turned.map { it.x }.toSet()).containsExactly(0f, size.width)
            assertThat(turned.map { it.y }.toSet()).containsExactly(0f, size.height)
        }
    }

    @Test
    fun `a turned rectangle keeps its sides along the axes`() {
        val link = PdfRect(10f, 20f, 50f, 40f)

        assertThat(PageRotation.NONE.toDisplay(link, page)).isEqualTo(link)
        assertThat(PageRotation.QUARTER.toDisplay(link, page)).isEqualTo(PdfRect(260f, 10f, 280f, 50f))
        assertThat(PageRotation.HALF.toDisplay(link, page)).isEqualTo(PdfRect(150f, 260f, 190f, 280f))
        assertThat(PageRotation.THREE_QUARTERS.toDisplay(link, page)).isEqualTo(PdfRect(20f, 150f, 40f, 190f))
    }

    @Test
    fun `a turned quad keeps the role of each corner`() {
        val word = PdfQuad.of(PdfRect(10f, 20f, 50f, 40f))

        val turned = PageRotation.QUARTER.toDisplay(word, page)

        // The upper edge of the word is now on the right: its upper-left corner is at the top right.
        assertThat(turned.upperLeft).isEqualTo(PdfPoint(280f, 10f))
        assertThat(turned.upperRight).isEqualTo(PdfPoint(280f, 50f))
        assertThat(turned.lowerLeft).isEqualTo(PdfPoint(260f, 10f))
        assertThat(turned.bounds).isEqualTo(PageRotation.QUARTER.toDisplay(word.bounds, page))
    }

    @Test
    fun `the transform matches toDisplay`() {
        PageRotation.entries.forEach { rotation ->
            val m = rotation.transform(page)
            val expected = rotation.toDisplay(point, page)

            assertThat(m.a * point.x + m.c * point.y + m.e).isEqualTo(expected.x)
            assertThat(m.b * point.x + m.d * point.y + m.f).isEqualTo(expected.y)
        }
    }

    @Test
    fun `stored angles are read back, unknown ones as no rotation`() {
        assertThat(PageRotation.fromDegrees(0)).isEqualTo(PageRotation.NONE)
        assertThat(PageRotation.fromDegrees(90)).isEqualTo(PageRotation.QUARTER)
        assertThat(PageRotation.fromDegrees(180)).isEqualTo(PageRotation.HALF)
        assertThat(PageRotation.fromDegrees(270)).isEqualTo(PageRotation.THREE_QUARTERS)
        assertThat(PageRotation.fromDegrees(-90)).isEqualTo(PageRotation.THREE_QUARTERS)
        assertThat(PageRotation.fromDegrees(450)).isEqualTo(PageRotation.QUARTER)
        assertThat(PageRotation.fromDegrees(45)).isEqualTo(PageRotation.NONE)
    }
}
