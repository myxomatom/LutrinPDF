// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Link
import com.artifex.mupdf.fitz.Outline
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.StructuredText
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfMetadata
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
import io.github.myxomatom.lutrin.core.domain.pdf.parsePdfDate

/** Value MuPDF reports in the `encryption` metadata of an unencrypted document. */
private const val NOT_ENCRYPTED = "None"

/** Converts a MuPDF rectangle, normalising it in case MuPDF returns an inverted one. */
internal fun Rect.toPdfRect(): PdfRect = PdfRect(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1))

/** MuPDF search flags for the given options. */
internal fun SearchOptions.toMuPdfFlags(): Int {
    var flags = StructuredText.SEARCH_EXACT
    if (!matchCase) flags = flags or StructuredText.SEARCH_IGNORE_CASE
    if (!matchAccents) flags = flags or StructuredText.SEARCH_IGNORE_DIACRITICS
    return flags
}

/** Reads the document information; MuPDF returns blank strings for missing fields. */
internal fun Document.readMetadata(pageCount: Int): PdfMetadata {
    fun field(key: String) = getMetaData(key)?.takeIf { it.isNotBlank() }
    return PdfMetadata(
        pageCount = pageCount,
        title = field(Document.META_INFO_TITLE),
        author = field(Document.META_INFO_AUTHOR),
        subject = field(Document.META_INFO_SUBJECT),
        keywords = field(Document.META_INFO_KEYWORDS),
        creator = field(Document.META_INFO_CREATOR),
        producer = field(Document.META_INFO_PRODUCER),
        creationDate = parsePdfDate(field(Document.META_INFO_CREATIONDATE)),
        modificationDate = parsePdfDate(field(Document.META_INFO_MODIFICATIONDATE)),
        format = field(Document.META_FORMAT),
        isEncrypted = field(Document.META_ENCRYPTION).let { it != null && it != NOT_ENCRYPTED },
    )
}

/** Converts MuPDF outline entries, recursively. */
internal fun Document.toOutlineItems(entries: Array<Outline>?): List<OutlineItem> = entries.orEmpty().map { entry ->
    OutlineItem(
        title = entry.title.orEmpty(),
        destination = destinationOf(entry.uri),
        uri = entry.uri?.takeIf(Link::isExternal),
        children = toOutlineItems(entry.down),
    )
}

/**
 * Converts a MuPDF link; `null` for an internal link whose destination does not resolve. An
 * external link keeps its URI as MuPDF reads it, whatever the scheme: MuPDF turns a relative URI
 * into a `file://` one (ADR 0020).
 */
internal fun Document.toPdfLink(link: Link): PdfLink? {
    val uri = link.uri ?: return null
    val bounds = link.bounds.toPdfRect()
    return if (link.isExternal) {
        PdfLink.External(bounds, uri)
    } else {
        destinationOf(uri)?.let { PdfLink.Internal(bounds, it) }
    }
}
