// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.print

import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/** Object number of the catalog, written last. */
private const val CATALOG_OBJECT = 1

/** Object number of the page tree, written last, once every page is known. */
private const val PAGES_OBJECT = 2

/** First object number of the pages. */
private const val FIRST_PAGE_OBJECT = 3

/** Bytes per pixel in the image streams: red, green, blue. */
private const val RGB_BYTES = 3

/** Bit shifts of the colour channels in an ARGB pixel. */
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

/** Mask of one colour channel. */
private const val CHANNEL_MASK = 0xFF

/**
 * The PDF header, in Latin-1. The comment of characters above 127 tells transfer tools that the
 * file is binary.
 */
private const val HEADER = "%PDF-1.4\n%âãÏÓ\n"

/**
 * One page drawn as an image: [width] × [height] opaque pixels, read one row at a time.
 *
 * @property width width in pixels.
 * @property height height in pixels.
 * @property readRow copies the ARGB pixels of a row (from the top) into its array of [width] pixels.
 */
internal class PageImage(val width: Int, val height: Int, val readRow: (row: Int, pixels: IntArray) -> Unit) {
    init {
        require(width > 0 && height > 0) { "Image size must be positive, was $width x $height" }
    }
}

/**
 * Writes a PDF made of one image per page into [output], page by page (ADR 0026): what prints a
 * document opened with its password, whose decrypted PDF is never written.
 *
 * Each page is compressed (Flate, lossless) and written as soon as it is added, so that only one
 * page is ever held in memory, whatever the number of pages. `android.graphics.pdf.PdfDocument`
 * keeps every page it records until it writes the file, so its memory grows with the document.
 *
 * Call [addPage] for each page, in order, then [finish] once; [output] is not closed.
 *
 * @param output where the PDF goes, from its first byte.
 */
internal class ImagePdfWriter(output: OutputStream) {
    private val out = CountingOutputStream(output)
    private val offsets = mutableMapOf<Int, Long>()
    private val pageObjects = mutableListOf<Int>()
    private var nextObject = FIRST_PAGE_OBJECT
    private var finished = false

    init {
        out.write(HEADER.toByteArray(Charsets.ISO_8859_1))
    }

    /** Number of pages added so far. */
    val pageCount: Int get() = pageObjects.size

    /**
     * Adds a page of [width] × [height] points covered by [image].
     *
     * @throws IllegalStateException after [finish].
     * @throws java.io.IOException if the output cannot be written.
     */
    fun addPage(width: Float, height: Float, image: PageImage) {
        check(!finished) { "The PDF is finished" }
        val imageObject = writeStream(
            "/Type /XObject /Subtype /Image /Width ${image.width} /Height ${image.height} " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode",
            compressed(image),
        )
        val content = "q ${number(width)} 0 0 ${number(height)} 0 0 cm /Im0 Do Q".toByteArray(Charsets.US_ASCII)
        val contentObject = writeStream("", content)
        val page = writeObject(
            "<< /Type /Page /Parent $PAGES_OBJECT 0 R /MediaBox [0 0 ${number(width)} ${number(height)}] " +
                "/Resources << /XObject << /Im0 $imageObject 0 R >> >> /Contents $contentObject 0 R >>",
        )
        pageObjects += page
    }

    /**
     * Writes the page tree, the catalog and the cross-reference table: the PDF is complete.
     *
     * @throws IllegalStateException without any page, or when called twice.
     * @throws java.io.IOException if the output cannot be written.
     */
    fun finish() {
        check(!finished) { "The PDF is finished" }
        check(pageObjects.isNotEmpty()) { "A PDF needs at least one page" }
        finished = true
        val kids = pageObjects.joinToString(" ") { "$it 0 R" }
        writeObject("<< /Type /Pages /Kids [$kids] /Count ${pageObjects.size} >>", PAGES_OBJECT)
        writeObject("<< /Type /Catalog /Pages $PAGES_OBJECT 0 R >>", CATALOG_OBJECT)
        val xref = out.count
        val size = nextObject
        val table = StringBuilder("xref\n0 $size\n0000000000 65535 f \n")
        for (number in 1 until size) {
            table.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.getValue(number)))
        }
        table.append("trailer\n<< /Size $size /Root $CATALOG_OBJECT 0 R >>\nstartxref\n$xref\n%%EOF\n")
        ascii(table.toString())
        out.flush()
    }

    private fun writeObject(body: String, number: Int = nextObject++): Int {
        offsets[number] = out.count
        ascii("$number 0 obj\n$body\nendobj\n")
        return number
    }

    private fun writeStream(dictionary: String, data: ByteArray): Int {
        val number = nextObject++
        offsets[number] = out.count
        ascii("$number 0 obj\n<< $dictionary /Length ${data.size} >>\nstream\n")
        out.write(data)
        ascii("\nendstream\nendobj\n")
        return number
    }

    private fun ascii(text: String) = out.write(text.toByteArray(Charsets.US_ASCII))
}

/** The pixels of [image], row by row, as Flate-compressed RGB bytes. */
private fun compressed(image: PageImage): ByteArray {
    val pixels = IntArray(image.width)
    val row = ByteArray(image.width * RGB_BYTES)
    val bytes = ByteArrayOutputStream()
    val deflater = Deflater()
    try {
        DeflaterOutputStream(bytes, deflater).use { stream ->
            for (y in 0 until image.height) {
                image.readRow(y, pixels)
                pixels.forEachIndexed { x, pixel ->
                    row[x * RGB_BYTES] = (pixel shr RED_SHIFT and CHANNEL_MASK).toByte()
                    row[x * RGB_BYTES + 1] = (pixel shr GREEN_SHIFT and CHANNEL_MASK).toByte()
                    row[x * RGB_BYTES + 2] = (pixel and CHANNEL_MASK).toByte()
                }
                stream.write(row)
            }
        }
    } finally {
        // A deflater given to the stream is not ended by it: its native memory is freed here.
        deflater.end()
    }
    return bytes.toByteArray()
}

/** A length in points, with two decimals at most, in the syntax of PDF (no exponent, a dot). */
private fun number(value: Float): String = String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')

/** Counts the bytes written, for the offsets of the cross-reference table. */
private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
    var count: Long = 0
        private set

    override fun write(b: Int) {
        out.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }
}
