// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import java.io.InputStream

/** The PDF specification allows bytes before the header, within the first kilobyte. */
internal const val PDF_HEADER_SEARCH_LENGTH = 1024

private val PDF_HEADER = "%PDF-".toByteArray(Charsets.US_ASCII)

/**
 * Whether [start], the first bytes of a file, contains a PDF header. Used to tell "not a PDF"
 * (another format renamed) apart from "damaged PDF", which MuPDF reports the same way.
 */
internal fun hasPdfHeader(start: ByteArray): Boolean {
    val limit = minOf(start.size, PDF_HEADER_SEARCH_LENGTH) - PDF_HEADER.size
    return (0..limit).any { offset -> PDF_HEADER.indices.all { start[offset + it] == PDF_HEADER[it] } }
}

/** Reads at most [count] bytes, fewer only at the end of the stream (`readNBytes` needs API 33). */
internal fun InputStream.readUpTo(count: Int): ByteArray {
    val buffer = ByteArray(count)
    var filled = 0
    while (filled < count) {
        val read = read(buffer, filled, count - filled)
        if (read < 0) break
        filled += read
    }
    return buffer.copyOf(filled)
}
