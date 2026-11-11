// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Link
import com.artifex.mupdf.fitz.LinkDestination
import com.artifex.mupdf.fitz.Outline
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.StructuredText
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfMetadata
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
import io.github.myxomatom.lutrin.core.domain.pdf.parsePdfDate

/** Value MuPDF reports in the `encryption` metadata of an unencrypted document. */
private const val NOT_ENCRYPTED = "None"

/** Converts a MuPDF rectangle, normalising it in case MuPDF returns an inverted one. */
internal fun Rect.toPdfRect(): PdfRect = PdfRect(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1))

/** Smallest axis-aligned rectangle around a (possibly rotated) MuPDF quad. */
internal fun Quad.toPdfRect(): PdfRect =
    PdfRect.enclosing(listOf(ul_x to ul_y, ur_x to ur_y, ll_x to ll_y, lr_x to lr_y))

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

/** Destination of an internal [uri], or `null` if it is external or does not resolve. */
internal fun Document.destinationOf(uri: String?): PdfDestination? =
    if (uri == null || Link.isExternal(uri)) null else resolvedDestinationOrNull(uri)

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun Document.resolvedDestinationOrNull(uri: String): PdfDestination? = try {
    val destination = resolveLinkDestination(uri)
    val pageIndex = pageNumberFromLocation(destination)
    if (pageIndex < 0) null else destination.toPdfDestination(pageIndex)
} catch (error: RuntimeException) {
    // MuPDF reports a malformed or dangling destination as a plain RuntimeException
    // (see translatingMuPdfErrors). A broken link must not make the whole outline or
    // link list unreadable, so it is dropped; an Error (out of memory…) still propagates.
    null
}

/**
 * Keeps the coordinates that the kind of destination sets: MuPDF has already turned them into
 * page coordinates (top-left origin, rotation applied); a coordinate left unset is NaN.
 */
private fun LinkDestination.toPdfDestination(pageIndex: Int): PdfDestination {
    val (keepsX, keepsY) = when (type) {
        LinkDestination.LINK_DEST_XYZ, LinkDestination.LINK_DEST_FIT_R -> true to true
        LinkDestination.LINK_DEST_FIT_H, LinkDestination.LINK_DEST_FIT_BH -> false to true
        LinkDestination.LINK_DEST_FIT_V, LinkDestination.LINK_DEST_FIT_BV -> true to false
        else -> false to false // Fit the whole page.
    }
    return PdfDestination(
        pageIndex = pageIndex,
        x = x.takeIf { keepsX && it.isFinite() },
        y = y.takeIf { keepsY && it.isFinite() },
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
