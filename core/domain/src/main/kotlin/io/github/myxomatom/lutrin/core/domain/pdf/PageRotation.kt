// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

/**
 * Rotation of the display of the pages, by quarter turns clockwise (F27, ADR 0032): display only,
 * the file is never changed, and the whole document turns, never a single page.
 *
 * Two spaces meet here. The **page space** is the one of the engine: points of the page as MuPDF
 * gives them (its own `/Rotate` already applied), origin at the top-left corner, y downwards;
 * text, links, search hits and selections are always in it. The **display space** is the page
 * as the reader shows it once turned: same unit, origin at the top-left corner of the turned page,
 * y downwards. Every conversion between the two goes through the functions of this class, so that
 * the rendering, the hit tests and the overlays never disagree.
 *
 * @property degrees the angle, clockwise: 0, 90, 180 or 270.
 */
enum class PageRotation(val degrees: Int) {
    /** The page as the document shows it. */
    NONE(0),

    /** A quarter turn clockwise: the top of the page goes to the right. */
    QUARTER(90),

    /** Upside down. */
    HALF(180),

    /** A quarter turn anticlockwise: the top of the page goes to the left. */
    THREE_QUARTERS(270),
    ;

    /** The next quarter turn clockwise, in the cycle 0 → 90 → 180 → 270 → 0 (one tap of « Tourner »). */
    val next: PageRotation get() = entries[(ordinal + 1) % entries.size]

    /** Whether the width and the height of a page are swapped on screen (90° and 270°). */
    val swapsSides: Boolean get() = this == QUARTER || this == THREE_QUARTERS

    /** Size of [page] once turned: its sides swapped at 90° and 270°. */
    fun displaySize(page: PageSize): PageSize = if (swapsSides) PageSize(page.height, page.width) else page

    /**
     * Where the point [point] of [page] (page space) is shown on the turned page (display space).
     *
     * @param page size of the page in page space, as the engine gives it.
     */
    fun toDisplay(point: PdfPoint, page: PageSize): PdfPoint = with(transform(page)) {
        PdfPoint(a * point.x + c * point.y + e, b * point.x + d * point.y + f)
    }

    /**
     * Which point of [page] (page space) is shown at [point] of the turned page (display space):
     * the inverse of [toDisplay].
     */
    fun toPage(point: PdfPoint, page: PageSize): PdfPoint = when (this) {
        NONE -> point
        QUARTER -> PdfPoint(point.y, page.height - point.x)
        HALF -> PdfPoint(page.width - point.x, page.height - point.y)
        THREE_QUARTERS -> PdfPoint(page.width - point.y, point.x)
    }

    /** [rect] of [page] once turned: the turned rectangle, still with its sides along the axes. */
    fun toDisplay(rect: PdfRect, page: PageSize): PdfRect {
        val corners = listOf(PdfPoint(rect.left, rect.top), PdfPoint(rect.right, rect.bottom)).map { toDisplay(it, page) }
        return PdfRect.enclosing(corners.map { it.x to it.y })
    }

    /**
     * [quad] of [page] once turned. Each corner keeps its role in the text (the upper-left corner
     * of a word stays its upper-left corner, wherever it is drawn), so that a path through the four
     * corners in order still outlines the same area.
     */
    fun toDisplay(quad: PdfQuad, page: PageSize): PdfQuad = PdfQuad(
        upperLeft = toDisplay(quad.upperLeft, page),
        upperRight = toDisplay(quad.upperRight, page),
        lowerLeft = toDisplay(quad.lowerLeft, page),
        lowerRight = toDisplay(quad.lowerRight, page),
    )

    /**
     * The affine transform from the page space of [page] to the display space: the matrix the
     * engine prepends to its own to draw the page turned, `x' = a·x + c·y + e`, `y' = b·x + d·y + f`.
     */
    fun transform(page: PageSize): AffineTransform = when (this) {
        NONE -> AffineTransform(a = 1f, b = 0f, c = 0f, d = 1f, e = 0f, f = 0f)
        QUARTER -> AffineTransform(a = 0f, b = 1f, c = -1f, d = 0f, e = page.height, f = 0f)
        HALF -> AffineTransform(a = -1f, b = 0f, c = 0f, d = -1f, e = page.width, f = page.height)
        THREE_QUARTERS -> AffineTransform(a = 0f, b = -1f, c = 1f, d = 0f, e = 0f, f = page.width)
    }

    /** Lookup by angle. */
    companion object {
        /**
         * The rotation of [degrees], as stored with the reading position: any multiple of 90°,
         * negative or beyond a full turn, is brought back into 0..270; any other value, unknown,
         * reads as [NONE] rather than failing.
         */
        fun fromDegrees(degrees: Int): PageRotation {
            val normalized = Math.floorMod(degrees, FULL_TURN)
            return entries.firstOrNull { it.degrees == normalized } ?: NONE
        }

        /** Degrees of a full turn. */
        private const val FULL_TURN = 360
    }
}

/**
 * A 2D affine transform in the convention of PDF and MuPDF matrices: a point (x, y) goes to
 * (`a·x + c·y + e`, `b·x + d·y + f`).
 */
data class AffineTransform(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float)
