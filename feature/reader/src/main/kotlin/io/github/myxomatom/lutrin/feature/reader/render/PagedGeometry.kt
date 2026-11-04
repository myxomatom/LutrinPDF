// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.render

import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import kotlin.math.roundToInt

// Geometry of the page-by-page mode (F03, ADR 0011); the rest is shared with the continuous
// mode, in ReaderGeometry.kt.

/**
 * Displayed width at which [page] fits whole in the viewport, with [margin] pixels left free on
 * every side: zoom 1 of the page-by-page mode (F03, ADR 0011). Its height never exceeds the room
 * left, so a page wider or taller than the screen is never cut.
 *
 * @param viewportWidth width of the reading area, in pixels.
 * @param viewportHeight height of the reading area, in pixels.
 * @param margin free space on each side of the page, in pixels.
 */
fun fitPageWidth(page: PageSize, viewportWidth: Int, viewportHeight: Int, margin: Int): Int {
    val availableWidth = (viewportWidth - 2 * margin).coerceAtLeast(1)
    val availableHeight = (viewportHeight - 2 * margin).coerceAtLeast(1)
    val widthForHeight = (availableHeight * page.width / page.height).toInt()
    return minOf(availableWidth, widthForHeight).coerceAtLeast(1)
}

/**
 * Size, along one axis, of the zoomable area of a page in page-by-page mode: the viewport
 * scaled by [zoom], never smaller than the viewport. Everything in it, page and margins, scales
 * with the zoom, so that a gesture keeps its focal point once applied ([scrollAfterGesture]).
 *
 * @param viewportExtent width or height of the reading area, in pixels.
 */
fun zoomedExtent(viewportExtent: Int, zoom: Float): Int = maxOf(viewportExtent, (viewportExtent * zoom).roundToInt())
