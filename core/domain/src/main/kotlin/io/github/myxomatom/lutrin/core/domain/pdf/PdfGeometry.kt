// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

/**
 * Size of a page in PDF points (1/72 inch), after the page rotation is applied.
 *
 * @property width horizontal size, in points; always > 0.
 * @property height vertical size, in points; always > 0.
 */
data class PageSize(val width: Float, val height: Float) {
    init {
        require(width > 0f && height > 0f) { "Page size must be positive, was $width x $height" }
    }

    /** Whether the page is wider than tall. */
    val isLandscape: Boolean get() = width > height
}

/**
 * Point in page coordinates: points, origin at the top-left corner of the page, y growing
 * downwards (the coordinates of [PdfRect]).
 *
 * @property x distance from the left edge of the page.
 * @property y distance from the top edge of the page.
 */
data class PdfPoint(val x: Float, val y: Float)

/**
 * Four-cornered area in page coordinates, which follows the text it covers even when that text
 * is rotated inside the page. "Upper" and "lower" are relative to the text: for horizontal text,
 * [upperLeft] is the top-left corner.
 *
 * @property upperLeft start of the top edge, in the writing direction of the text.
 * @property upperRight end of the top edge.
 * @property lowerLeft start of the bottom edge.
 * @property lowerRight end of the bottom edge.
 */
data class PdfQuad(
    val upperLeft: PdfPoint,
    val upperRight: PdfPoint,
    val lowerLeft: PdfPoint,
    val lowerRight: PdfPoint,
) {
    /** Smallest axis-aligned rectangle around the four corners. */
    val bounds: PdfRect
        get() = PdfRect.enclosing(listOf(upperLeft, upperRight, lowerLeft, lowerRight).map { it.x to it.y })

    /** Factories of [PdfQuad]. */
    companion object {
        /** The quad covering exactly [rect]. */
        fun of(rect: PdfRect): PdfQuad = PdfQuad(
            upperLeft = PdfPoint(rect.left, rect.top),
            upperRight = PdfPoint(rect.right, rect.top),
            lowerLeft = PdfPoint(rect.left, rect.bottom),
            lowerRight = PdfPoint(rect.right, rect.bottom),
        )
    }
}

/**
 * Axis-aligned rectangle in page coordinates: points, origin at the top-left corner of the
 * page, y growing downwards.
 *
 * @property left x of the left edge.
 * @property top y of the top edge.
 * @property right x of the right edge, ≥ [left].
 * @property bottom y of the bottom edge, ≥ [top].
 * @throws IllegalArgumentException if [right] < [left] or [bottom] < [top].
 */
data class PdfRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(right >= left && bottom >= top) { "Invalid rectangle: [$left, $top, $right, $bottom]" }
    }

    /** Horizontal size, in points. */
    val width: Float get() = right - left

    /** Vertical size, in points. */
    val height: Float get() = bottom - top

    /** Whether the rectangle has no area. */
    val isEmpty: Boolean get() = width == 0f || height == 0f

    /** Whether the point ([x], [y]) lies inside the rectangle, edges included. */
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    /** Factories of [PdfRect]. */
    companion object {
        /** Smallest rectangle enclosing all the given points, as `(x, y)` pairs. */
        fun enclosing(points: List<Pair<Float, Float>>): PdfRect {
            require(points.isNotEmpty()) { "At least one point is needed" }
            return PdfRect(
                left = points.minOf { it.first },
                top = points.minOf { it.second },
                right = points.maxOf { it.first },
                bottom = points.maxOf { it.second },
            )
        }
    }
}
