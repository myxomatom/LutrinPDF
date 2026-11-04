// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.render

import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Zoom limits of F04, relative to "fit width" (zoom 1). */
const val MIN_ZOOM = 0.25f

/** Upper zoom limit of F04 (800 %). */
const val MAX_ZOOM = 8f

/** Zoom reached by a double tap from "fit width". */
const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Base renderings are made at a few discrete widths so that a pinch does not trigger a new
 * rendering at every frame; the image is stretched in between.
 */
private const val BASE_WIDTH_STEPS = 8

/** Clamps a zoom factor to [MIN_ZOOM]..[MAX_ZOOM]. */
fun clampZoom(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

/** Zoom reached by a double tap: back to "fit width" when zoomed in, otherwise [DOUBLE_TAP_ZOOM]. */
fun doubleTapZoom(current: Float): Float = if (current > 1f) 1f else DOUBLE_TAP_ZOOM

/**
 * Displayed width of a page, in pixels.
 *
 * @param viewportWidth width of the reading area, in pixels.
 * @param zoom current zoom; 1 means the page fills the width.
 */
fun pageWidthPx(viewportWidth: Int, zoom: Float): Int = (viewportWidth * zoom).roundToInt().coerceAtLeast(1)

/** Displayed height of [page] for a displayed width of [widthPx], keeping its aspect ratio. */
fun pageHeightPx(page: PageSize, widthPx: Int): Int = (widthPx * page.height / page.width).roundToInt().coerceAtLeast(1)

/**
 * Width at which the whole page is rendered once ("base" rendering). Never more than the
 * viewport width: beyond "fit width", sharpness comes from the detail rendering of the visible
 * region. Below it, the width is rounded up to a fraction of the viewport, to limit renderings.
 */
fun baseRenderWidth(viewportWidth: Int, zoom: Float): Int {
    val fraction = ceil(zoom.coerceAtMost(1f) * BASE_WIDTH_STEPS) / BASE_WIDTH_STEPS
    return (viewportWidth * fraction).roundToInt().coerceAtLeast(1)
}

/**
 * New scroll offset keeping the content point under the focal point still while zooming.
 *
 * @param scroll current scroll offset along one axis, in pixels.
 * @param focus position of the focal point in the viewport along the same axis, in pixels.
 * @param factor ratio new zoom / old zoom.
 */
fun scrollAfterZoom(scroll: Float, focus: Float, factor: Float): Float = (scroll + focus) * factor - focus

/**
 * A rectangle in pixels, relative to the top-left corner of the displayed page.
 *
 * @property left x of the left edge.
 * @property top y of the top edge.
 * @property right x of the right edge.
 * @property bottom y of the bottom edge.
 */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    /** Horizontal size, in pixels. */
    val width: Int get() = right - left

    /** Vertical size, in pixels. */
    val height: Int get() = bottom - top
}

/**
 * Part of a displayed page that is visible in the viewport.
 *
 * @param pageLeft position of the page's left edge in the viewport, in pixels (may be < 0).
 * @param pageTop position of the page's top edge in the viewport, in pixels (may be < 0).
 * @param pageWidth displayed page width, in pixels.
 * @param pageHeight displayed page height, in pixels.
 * @return the visible area relative to the page, or `null` if the page is off-screen.
 */
@Suppress("LongParameterList") // Plain geometry: two rectangles given by their coordinates.
fun visiblePart(
    pageLeft: Float,
    pageTop: Float,
    pageWidth: Int,
    pageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
): PixelRect? {
    val left = (-pageLeft).coerceAtLeast(0f).toInt()
    val top = (-pageTop).coerceAtLeast(0f).toInt()
    val right = (viewportWidth - pageLeft).coerceAtMost(pageWidth.toFloat()).toInt()
    val bottom = (viewportHeight - pageTop).coerceAtMost(pageHeight.toFloat()).toInt()
    return if (right > left && bottom > top) PixelRect(left, top, right, bottom) else null
}

/** Converts this part of a page displayed [pageWidthPx] wide into page coordinates (points). */
fun PixelRect.toPageRect(page: PageSize, pageWidthPx: Int): PdfRect {
    val pointsPerPixel = page.width / pageWidthPx
    return PdfRect(
        left = left * pointsPerPixel,
        top = top * pointsPerPixel,
        right = (right * pointsPerPixel).coerceAtMost(page.width),
        bottom = (bottom * pointsPerPixel).coerceAtMost(page.height),
    )
}

/**
 * Page shown in the page indicator: the page occupying the middle of the viewport.
 *
 * @param firstVisible index of the first visible page.
 * @param firstVisibleOffset how much of the first visible page is scrolled out, in pixels.
 * @param firstVisibleHeight height of the first visible page, spacing included, in pixels.
 * @param pageCount number of pages.
 */
fun currentPage(firstVisible: Int, firstVisibleOffset: Int, firstVisibleHeight: Int, pageCount: Int): Int {
    val pastHalf = firstVisibleHeight > 0 && firstVisibleOffset * 2 > firstVisibleHeight
    return (if (pastHalf) firstVisible + 1 else firstVisible).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
}
