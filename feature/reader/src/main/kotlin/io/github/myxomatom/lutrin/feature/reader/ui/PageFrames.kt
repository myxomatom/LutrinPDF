// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.Immutable
import io.github.myxomatom.lutrin.core.domain.pdf.PageRotation
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection

/**
 * The pages of the document as the reader shows them, turned by [rotation] (F27, ADR 0032), and
 * the one place where the reader crosses between the two spaces of [PageRotation]:
 * - the views lay out, fit, render and hit-test the pages in **display space** ([displaySizes]);
 * - the document answers in **page space**: links, search hits and selections.
 *
 * A tap is turned back into the page ([toPage]) before its link or its word is looked up; what the
 * document gives is turned into the display ([toDisplay]) before it is drawn or scrolled to. With
 * [PageRotation.NONE] every conversion returns its input.
 *
 * @property rotation the rotation of the display.
 * @property pageSizes sizes of the pages of the document, in page space.
 */
@Immutable
internal data class PageFrames(val rotation: PageRotation, val pageSizes: List<PageSize>) {
    /** Sizes of the pages as shown: their sides swapped at 90° and 270°. */
    val displaySizes: List<PageSize> =
        if (rotation == PageRotation.NONE) pageSizes else pageSizes.map(rotation::displaySize)

    /** Whether the pages are turned at all. */
    val isTurned: Boolean get() = rotation != PageRotation.NONE

    /**
     * [hit], found by a view in display space, in the page space of the document; `null` stays
     * `null`, and so does a hit on a page the document does not have. [PageHit.pointsPerPixel] is
     * unchanged: the rotation keeps distances.
     */
    fun toPage(hit: PageHit?): PageHit? {
        if (hit == null || !isTurned) return hit
        return pageSizes.getOrNull(hit.pageIndex)?.let { hit.copy(point = rotation.toPage(hit.point, it)) }
    }

    /** The point [point] shown on page [pageIndex] (display space), in the page space of the document. */
    fun toPage(pageIndex: Int, point: PdfPoint): PdfPoint =
        pageSizes.getOrNull(pageIndex)?.let { rotation.toPage(point, it) } ?: point

    /** [area] of page [pageIndex] (page space) as shown on the turned page. */
    fun toDisplay(pageIndex: Int, area: PdfRect): PdfRect =
        pageSizes.getOrNull(pageIndex)?.let { rotation.toDisplay(area, it) } ?: area

    /** [quads] of page [pageIndex] (page space) as shown on the turned page. */
    fun toDisplay(pageIndex: Int, quads: List<PdfQuad>): List<PdfQuad> {
        val page = pageSizes.getOrNull(pageIndex)
        return if (page == null || !isTurned) quads else quads.map { rotation.toDisplay(it, page) }
    }

    /**
     * [selection] as shown on the turned page: its areas and the ends of its handles. Its text and
     * its page do not change.
     */
    fun toDisplay(selection: TextSelection): TextSelection {
        val page = pageSizes.getOrNull(selection.pageIndex)
        if (page == null || !isTurned) return selection
        return selection.copy(
            quads = selection.quads.map { rotation.toDisplay(it, page) },
            start = selection.start?.let { rotation.toDisplay(it, page) },
            end = selection.end?.let { rotation.toDisplay(it, page) },
        )
    }

    /**
     * Distance from the top of the shown page to bring to the top of the reading area for a
     * destination [yOffset] points below the top of the page in the document (outline, links).
     * Straight, the destination itself. Turned, the top of the shown page: the top of the document
     * page is then on a side or at the bottom, and the line of a destination no longer runs across
     * the screen (ADR 0032).
     */
    fun displayOffset(yOffset: Float): Float = if (isTurned) 0f else yOffset
}
