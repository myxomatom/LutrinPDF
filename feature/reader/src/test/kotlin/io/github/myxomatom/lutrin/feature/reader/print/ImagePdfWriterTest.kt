// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.print

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertThrows
import org.junit.Test

/** The PDF of images that prints a protected document (ADR 0026), read back byte by byte. */
class ImagePdfWriterTest {
    private val output = ByteArrayOutputStream()
    private val writer = ImagePdfWriter(output)

    private fun solid(width: Int, height: Int, argb: Int) = PageImage(width, height) { _, pixels -> pixels.fill(argb) }

    private fun printed() = PrintedPdf(output.toByteArray())

    @Test
    fun `writes a complete PDF with one page`() {
        writer.addPage(595f, 842f, solid(4, 6, 0xFFFFFFFF.toInt()))
        writer.finish()

        val pdf = printed()
        assertThat(pdf.hasHeaderAndTrailer).isTrue()
        assertThat(pdf.hasConsistentCrossReferences).isTrue()
        assertThat(pdf.pageObjectCount).isEqualTo(1)
        assertThat(pdf.declaredPageCount).isEqualTo(1)
        assertThat(pdf.mediaBoxes).containsExactly("0 0 595 842")
    }

    @Test
    fun `keeps the size of each page, in points, and their order`() {
        writer.addPage(595.276f, 841.89f, solid(2, 3, 0xFFFF0000.toInt()))
        writer.addPage(842f, 595.5f, solid(3, 2, 0xFF0000FF.toInt()))
        writer.addPage(100f, 100f, solid(1, 1, 0xFF00FF00.toInt()))
        writer.finish()

        val pdf = printed()
        assertThat(writer.pageCount).isEqualTo(3)
        assertThat(pdf.declaredPageCount).isEqualTo(3)
        assertThat(pdf.hasConsistentCrossReferences).isTrue()
        assertThat(pdf.mediaBoxes).containsExactly("0 0 595.28 841.89", "0 0 842 595.5", "0 0 100 100").inOrder()
        assertThat(pdf.images.map { it.pixel(0, 0) }).containsExactly(0xFF0000, 0x0000FF, 0x00FF00).inOrder()
        assertThat(pdf.images.map { it.width to it.height }).containsExactly(2 to 3, 3 to 2, 1 to 1).inOrder()
    }

    @Test
    fun `keeps every pixel, row by row from the top, without loss`() {
        val rows =
            listOf(
                intArrayOf(0xFF102030.toInt(), 0xFF405060.toInt()),
                intArrayOf(0xFF708090.toInt(), 0xFFA0B0C0.toInt()),
            )
        writer.addPage(10f, 10f, PageImage(2, 2) { row, pixels -> rows[row].copyInto(pixels) })
        writer.finish()

        val image = printed().images.single()
        assertThat(image.pixel(0, 0)).isEqualTo(0x102030)
        assertThat(image.pixel(1, 0)).isEqualTo(0x405060)
        assertThat(image.pixel(0, 1)).isEqualTo(0x708090)
        assertThat(image.pixel(1, 1)).isEqualTo(0xA0B0C0)
    }

    @Test
    fun `reads each row of a page once, in order, as soon as the page is added`() {
        val rowsRead = mutableListOf<Int>()
        writer.addPage(
            10f,
            10f,
            PageImage(3, 4) { row, pixels ->
                rowsRead += row
                pixels.fill(0)
            },
        )

        // Written already: nothing of the page waits in memory for the next ones.
        assertThat(rowsRead).containsExactly(0, 1, 2, 3).inOrder()
        assertThat(output.size()).isGreaterThan(100)
    }

    @Test
    fun `refuses a PDF without pages`() {
        assertThrows(IllegalStateException::class.java) { writer.finish() }
    }

    @Test
    fun `refuses pages after the end, and a second end`() {
        writer.addPage(10f, 10f, solid(1, 1, 0))
        writer.finish()

        assertThrows(IllegalStateException::class.java) { writer.addPage(10f, 10f, solid(1, 1, 0)) }
        assertThrows(IllegalStateException::class.java) { writer.finish() }
    }

    @Test
    fun `refuses an empty image`() {
        assertThrows(IllegalArgumentException::class.java) { PageImage(0, 10) { _, _ -> } }
        assertThrows(IllegalArgumentException::class.java) { PageImage(10, 0) { _, _ -> } }
    }
}
