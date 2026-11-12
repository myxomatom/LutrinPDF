// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Link
import com.artifex.mupdf.fitz.LinkDestination
import com.artifex.mupdf.fitz.PDFPage
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import kotlin.math.abs

/** Destination of an internal [uri], or `null` if it is external or does not resolve. */
internal fun Document.destinationOf(uri: String?): PdfDestination? =
    if (uri == null || Link.isExternal(uri)) null else resolvedDestinationOrNull(uri)

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun Document.resolvedDestinationOrNull(uri: String): PdfDestination? = try {
    val destination = resolveLinkDestination(uri)
    val pageIndex = pageNumberFromLocation(destination)
    when {
        pageIndex < 0 -> null
        destination.isSingleAxis() && isQuarterTurned(pageIndex) -> PdfDestination(pageIndex)
        else -> destination.toPdfDestination(pageIndex, uri)
    }
} catch (error: RuntimeException) {
    // MuPDF reports a malformed or dangling destination as a plain RuntimeException
    // (see translatingMuPdfErrors). A broken link must not make the whole outline or
    // link list unreadable, so it is dropped; an Error (out of memory…) still propagates.
    null
}

/**
 * Whether only one coordinate of this destination is set: `/FitH`, `/FitV` and their `B`
 * variants, or `/XYZ` with a `null` coordinate. MuPDF computes that coordinate by turning the
 * point (0, y) or (x, 0) with the page; on a page turned by a quarter, the value lands on the
 * other axis and is lost (MuPDF 1.28.6, `populate_destination`), so it cannot be trusted there.
 */
private fun LinkDestination.isSingleAxis(): Boolean = when (type) {
    LinkDestination.LINK_DEST_FIT_H, LinkDestination.LINK_DEST_FIT_BH,
    LinkDestination.LINK_DEST_FIT_V, LinkDestination.LINK_DEST_FIT_BV,
    -> true

    LinkDestination.LINK_DEST_XYZ -> x.isFinite() != y.isFinite()

    else -> false
}

/**
 * Whether the page at [pageIndex] is shown turned by 90° or 270° (`/Rotate`). The page is loaded
 * for this check only and released at once, outside the page cache of the reader.
 */
private fun Document.isQuarterTurned(pageIndex: Int): Boolean {
    val page = loadPage(pageIndex)
    try {
        // The transform of a quarter turn swaps the axes: a = d = 0, b and c = ±1.
        val transform = (page as? PDFPage)?.transform ?: return false
        return abs(transform.b) > abs(transform.a)
    } finally {
        page.destroy()
    }
}

/**
 * Keeps the coordinates that the kind of destination sets: MuPDF has already turned them into
 * page coordinates (top-left origin, rotation applied); a coordinate left unset is NaN. A
 * `/FitR` destination keeps its top-left corner and, when [uri] spells it, its size.
 */
private fun LinkDestination.toPdfDestination(pageIndex: Int, uri: String): PdfDestination {
    val (keepsX, keepsY) = when (type) {
        LinkDestination.LINK_DEST_XYZ, LinkDestination.LINK_DEST_FIT_R -> true to true
        LinkDestination.LINK_DEST_FIT_H, LinkDestination.LINK_DEST_FIT_BH -> false to true
        LinkDestination.LINK_DEST_FIT_V, LinkDestination.LINK_DEST_FIT_BV -> true to false
        else -> false to false // Fit the whole page.
    }
    val size = if (type == LinkDestination.LINK_DEST_FIT_R) viewRectSize(uri) else null
    return PdfDestination(
        pageIndex = pageIndex,
        x = x.takeIf { keepsX && it.isFinite() },
        y = y.takeIf { keepsY && it.isFinite() },
        width = size?.first,
        height = size?.second,
    )
}

/** `viewrect=x,y,width,height` of a MuPDF link URI. */
private val VIEW_RECT = Regex("""[#&]viewrect=([^,&]+),([^,&]+),([^,&]+),([^,&]+)""")

/**
 * Size of the `/FitR` area written in a link [uri] (`#page=2&viewrect=x,y,w,h`), or `null`.
 * The size cannot be read from [LinkDestination]: in MuPDF 1.28.6 its Java constructor assigns
 * `width` and `height` to themselves, so both are always 0. A named destination
 * (`#nameddest=…`) does not spell the area, so its size stays unknown.
 */
internal fun viewRectSize(uri: String): Pair<Float, Float>? {
    val values = VIEW_RECT.find(uri)?.groupValues ?: return null
    val width = values[WIDTH_GROUP].toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
    val height = values[HEIGHT_GROUP].toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
    return if (width != null && height != null) width to height else null
}

private const val WIDTH_GROUP = 3
private const val HEIGHT_GROUP = 4
