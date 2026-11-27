// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.render

import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.settings.FitMode

// What zoom 1 means in each scroll mode, for the two fits of V6 « Ajustement » (F04, ADR 0029).
// Pure functions of the sizes of the reading area and of the pages, so that the views, the hit
// test and the tests agree.

/**
 * Height-to-width ratio of the tallest page of the document: the one that decides how wide the
 * pages of the continuous scroll can be while every page still fits whole in the screen.
 *
 * @param pageSizes size of every page, in points; empty gives 1.
 */
fun tallestAspect(pageSizes: List<PageSize>): Float =
    pageSizes.maxOfOrNull { it.height / it.width }?.coerceAtLeast(MIN_ASPECT) ?: 1f

/** Floor of the ratio, so that a degenerate page never makes the pages infinitely wide. */
private const val MIN_ASPECT = 0.01f

/**
 * Displayed width of every page at zoom 1 in the continuous scroll.
 *
 * With [FitMode.WIDTH] the page fills the width, less the [gutter] on each side. With
 * [FitMode.PAGE] the pages are as wide as the width allows *and* low enough for the tallest page
 * (see [tallestAspect]) to show whole between two gaps of [spacing]: one width for every page
 * keeps the scroll steady, and none is ever cut.
 *
 * @param viewportWidth width of the reading area, in pixels.
 * @param viewportHeight height of the reading area, in pixels.
 * @param fit what the pages are fitted to.
 * @param gutter free space on each side of a page, in pixels.
 * @param spacing gap between two pages, in pixels.
 * @param clearance height of the floating bar over the bottom of the pages, in pixels.
 * @param tallestAspect height-to-width ratio of the tallest page.
 */
@Suppress("LongParameterList") // Plain geometry: the sizes of the area and of the pages.
fun continuousFitWidth(
    viewportWidth: Int,
    viewportHeight: Int,
    fit: FitMode,
    gutter: Int,
    spacing: Int,
    clearance: Int,
    tallestAspect: Float,
): Int {
    val widthFit = (viewportWidth - 2 * gutter).coerceAtLeast(1)
    if (fit == FitMode.WIDTH) return widthFit
    val room = (viewportHeight - clearance.coerceAtMost(viewportHeight / 2) - 2 * spacing).coerceAtLeast(1)
    return minOf(widthFit, (room / tallestAspect).toInt()).coerceAtLeast(1)
}

/**
 * Displayed width of a page at zoom 1 in the page-by-page mode: the whole page in the area
 * ([FitMode.PAGE], [fitPageWidth]), or the width of the area less the [margin] on each side
 * ([FitMode.WIDTH]); a page taller than the area then scrolls vertically.
 *
 * @param page size of the page, in points.
 * @param viewportWidth width of the reading area, in pixels.
 * @param areaHeight height of the reading area above the floating bar, in pixels.
 * @param fit what the page is fitted to.
 * @param margin free space on each side of the page, in pixels.
 */
fun pagedFitWidth(page: PageSize, viewportWidth: Int, areaHeight: Int, fit: FitMode, margin: Int): Int = when (fit) {
    FitMode.PAGE -> fitPageWidth(page, viewportWidth, areaHeight, margin)
    FitMode.WIDTH -> (viewportWidth - 2 * margin).coerceAtLeast(1)
}
