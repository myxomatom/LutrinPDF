// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect

/** Number of corners of a quad, to average them. */
private const val QUAD_CORNERS = 4f

/** Quad of [glyphs] on a line going in [direction]: their extent along and across the line. */
internal fun lineQuad(direction: PdfPoint, glyphs: List<Glyph>): PdfQuad {
    val corners = glyphs.flatMap { it.quad.corners() }
    val start = corners.minOf { it.along(direction) }
    val end = corners.maxOf { it.along(direction) }
    val top = corners.minOf { it.across(direction) }
    val bottom = corners.maxOf { it.across(direction) }

    // Inverse of along / across: back to page coordinates.
    fun point(along: Float, across: Float) = PdfPoint(
        x = along * direction.x - across * direction.y,
        y = along * direction.y + across * direction.x,
    )
    return PdfQuad(point(start, top), point(end, top), point(start, bottom), point(end, bottom))
}

/** The four corners of the quad. */
internal fun PdfQuad.corners() = listOf(upperLeft, upperRight, lowerLeft, lowerRight)

/** Horizontal centre of the quad. */
internal fun PdfQuad.centerX(): Float = (upperLeft.x + upperRight.x + lowerLeft.x + lowerRight.x) / QUAD_CORNERS

/** Vertical centre of the quad. */
internal fun PdfQuad.centerY(): Float = (upperLeft.y + upperRight.y + lowerLeft.y + lowerRight.y) / QUAD_CORNERS

/** Coordinate along a line going in [direction] (a unit vector). */
internal fun PdfPoint.along(direction: PdfPoint): Float = x * direction.x + y * direction.y

/** Coordinate across a line going in [direction]: grows downwards for horizontal text. */
internal fun PdfPoint.across(direction: PdfPoint): Float = -x * direction.y + y * direction.x

/** Distance from [value] to the interval [min]..[max]; 0 inside it. */
internal fun distanceOutside(value: Float, min: Float, max: Float): Float = when {
    value < min -> min - value
    value > max -> value - max
    else -> 0f
}

/** Whether the two rectangles share at least a point, edges included. */
internal fun PdfRect.intersects(other: PdfRect): Boolean =
    left <= other.right && other.left <= right && top <= other.bottom && other.top <= bottom
