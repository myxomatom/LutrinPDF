// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.Font
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.StructuredText
import com.artifex.mupdf.fitz.StructuredTextWalker
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import kotlin.math.hypot

/**
 * Options of the structured text MuPDF extracts for search and selection. `dehyphenate` marks
 * the lines ending with a hyphen, so that the search finds a word cut at the end of a line; it
 * is what MuPDF's own page search uses.
 */
internal const val STRUCTURED_TEXT_OPTIONS = "dehyphenate"

/** Below this length, the direction MuPDF gives for a line is meaningless. */
private const val MIN_DIRECTION_LENGTH = 1e-3f

/**
 * Extracts the structured text of this page and runs [block] on it, then releases it. Must run
 * on the thread of the page's document.
 */
internal inline fun <T> Page.withStructuredText(block: (StructuredText) -> T): T {
    val text = toStructuredText(STRUCTURED_TEXT_OPTIONS)
    try {
        return block(text)
    } finally {
        text.destroy()
    }
}

/**
 * Reads every character of this structured text into a [PageText]. Must run on the thread of
 * its document: the fonts and images MuPDF hands to the walker are released right away, on that
 * thread, rather than left to the garbage collector.
 */
internal fun StructuredText.toPageText(): PageText {
    val collector = GlyphCollector()
    walk(collector)
    return PageText(collector.lines)
}

/**
 * Occurrences of [query] on this page, in reading order, each with its excerpt. Must run on the
 * thread of the page's document.
 *
 * @param pageIndex page reported in the results.
 * @param flags MuPDF search flags (see [toMuPdfFlags]).
 */
internal fun Page.searchHits(pageIndex: Int, query: String, flags: Int): List<SearchHit> = withStructuredText { text ->
    val matches = text.search(query, flags).orEmpty()
    // The characters are only read when the page has a result: most pages have none.
    val pageText by lazy(LazyThreadSafetyMode.NONE) { text.toPageText() }
    matches.map { quads ->
        val areas = quads.map { it.toPdfRect() }
        SearchHit(pageIndex, areas, pageText.excerpt(areas))
    }
}

/** Converts a MuPDF quad. */
internal fun Quad.toPdfQuad(): PdfQuad = PdfQuad(
    upperLeft = PdfPoint(ul_x, ul_y),
    upperRight = PdfPoint(ur_x, ur_y),
    lowerLeft = PdfPoint(ll_x, ll_y),
    lowerRight = PdfPoint(lr_x, lr_y),
)

/** Collects the lines of text, in reading order, and nothing else. */
private class GlyphCollector : StructuredTextWalker {
    val lines = mutableListOf<GlyphLine>()
    private var glyphs = mutableListOf<Glyph>()
    private var direction = HORIZONTAL_TEXT

    override fun onImageBlock(bbox: Rect?, transform: Matrix?, image: Image?) {
        image?.destroy()
    }

    override fun beginTextBlock(bbox: Rect?, flags: Int) = Unit

    override fun endTextBlock() = Unit

    override fun beginLine(bbox: Rect?, wmode: Int, dir: Point?) {
        glyphs = mutableListOf()
        direction = dir?.takeIf { hypot(it.x, it.y) > MIN_DIRECTION_LENGTH }?.let { PdfPoint(it.x, it.y) }
            ?: HORIZONTAL_TEXT
    }

    override fun endLine() {
        if (glyphs.isNotEmpty()) lines += GlyphLine(glyphs, direction)
    }

    @Suppress("LongParameterList") // Imposed by MuPDF's interface.
    override fun onChar(
        c: Int,
        origin: Point?,
        font: Font?,
        size: Float,
        q: Quad?,
        argb: Int,
        flags: Int,
        bidi: Int,
    ) {
        font?.destroy()
        // An odd embedding level is right-to-left (Unicode bidirectional algorithm).
        if (q != null) glyphs += Glyph(c, q.toPdfQuad(), isRightToLeft = bidi and 1 == 1)
    }

    override fun beginStruct(standard: String?, raw: String?, index: Int) = Unit

    override fun endStruct() = Unit

    override fun onVector(bbox: Rect?, info: StructuredTextWalker.VectorInfo?, argb: Int) = Unit
}
