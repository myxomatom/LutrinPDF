// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.print

import java.util.zip.Inflater

/**
 * Reads back, for the tests, a PDF written by [ImagePdfWriter]: its pages, their images and its
 * cross-reference table. Not a PDF parser: it knows the exact syntax the writer produces.
 */
internal class PrintedPdf(private val bytes: ByteArray) {
    private val text = String(bytes, Charsets.ISO_8859_1)

    /**
     * One image of a page.
     *
     * @property width width in pixels.
     * @property height height in pixels.
     * @property rgb its pixels, three bytes each, row by row.
     */
    class Image(val width: Int, val height: Int, val rgb: ByteArray) {
        /** The pixel at ([x], [y]) as 0xRRGGBB. */
        fun pixel(x: Int, y: Int): Int {
            val at = (y * width + x) * 3
            return (rgb[at].toInt() and 0xFF shl 16) or (rgb[at + 1].toInt() and 0xFF shl 8) or
                (rgb[at + 2].toInt() and 0xFF)
        }
    }

    /** Whether the file starts with the PDF header and ends with the end-of-file marker. */
    val hasHeaderAndTrailer: Boolean get() = text.startsWith("%PDF-1.4\n") && text.endsWith("%%EOF\n")

    /** Number of page objects. */
    val pageObjectCount: Int get() = Regex("/Type /Page /Parent").findAll(text).count()

    /** The /Count of the page tree. */
    val declaredPageCount: Int? get() = Regex(
        "/Type /Pages /Kids \\[[^]]*] /Count (\\d+)",
    ).find(text)?.groupValues?.get(1)?.toInt()

    /** The media box of each page, in order, as written. */
    val mediaBoxes: List<String> get() = Regex("/MediaBox \\[([^]]*)]").findAll(text).map { it.groupValues[1] }.toList()

    /** The images, in the order of the pages. */
    val images: List<Image>
        get() = Regex("/Subtype /Image /Width (\\d+) /Height (\\d+) [^>]*/Length (\\d+) >>\nstream\n")
            .findAll(text)
            .map { match ->
                val (width, height, length) = match.destructured
                val start = match.range.last + 1
                Image(width.toInt(), height.toInt(), inflate(bytes.copyOfRange(start, start + length.toInt())))
            }.toList()

    /**
     * Whether every entry of the cross-reference table points to the start of its object, and the
     * table is where `startxref` says.
     */
    val hasConsistentCrossReferences: Boolean
        get() {
            val start = Regex("startxref\n(\\d+)\n%%EOF\n$").find(text)?.groupValues?.get(1)?.toInt() ?: return false
            if (!text.startsWith("xref\n", start)) return false
            val lines = text.substring(start).lines()
            val size = lines[1].substringAfter(' ').toInt()
            if (lines[2] != "0000000000 65535 f ") return false
            return (1 until size).all { number ->
                val offset = lines[2 + number].substring(0, 10).toInt()
                text.startsWith("$number 0 obj\n", offset)
            }
        }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            check(read > 0 || !inflater.needsInput()) { "Truncated image stream" }
            out.write(buffer, 0, read)
        }
        inflater.end()
        return out.toByteArray()
    }
}
