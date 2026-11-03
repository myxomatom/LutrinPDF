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
