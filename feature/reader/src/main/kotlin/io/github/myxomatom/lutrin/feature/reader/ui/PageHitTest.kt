// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.unit.IntSize
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.feature.reader.render.fitPageWidth
import io.github.myxomatom.lutrin.feature.reader.render.pageHeightPx
import io.github.myxomatom.lutrin.feature.reader.render.pageWidthPx
import io.github.myxomatom.lutrin.feature.reader.render.zoomedExtent
import kotlin.math.hypot
import kotlin.math.roundToInt

// From a tap on the reading area to a point of a page, in both scroll modes (F03), whatever the
// zoom and the scroll, then to the link under the finger (F09, ADR 0022). Pure functions: the
// views give them the layout they use, so that the hit test and the drawing never disagree.

/**
 * Where a page is drawn in the reading area, in pixels.
 *
 * @property pageIndex zero-based page.
 * @property left x of the left edge of the page in the reading area (negative when scrolled past).
 * @property top y of the top edge of the page in the reading area (negative when scrolled past).
 * @property width displayed width of the page.
 * @property height displayed height of the page.
 */
internal data class PagePlacement(val pageIndex: Int, val left: Float, val top: Float, val width: Int, val height: Int)

/**
 * A tap on a page.
 *
 * @property pageIndex zero-based page tapped.
 * @property point the tapped point, in page coordinates (points, origin at the top-left corner).
 * @property pointsPerPixel page points per screen pixel at the current zoom: turns a distance
 *   on screen, such as the tolerance of a finger, into page units.
 */
internal data class PageHit(val pageIndex: Int, val point: PdfPoint, val pointsPerPixel: Float)

/**
 * The page under ([x], [y]), and the point of that page, in page coordinates.
 *
 * @param x horizontal position of the tap in the reading area, in pixels.
 * @param y vertical position of the tap in the reading area, in pixels.
 * @param placements where each page on screen is drawn.
 * @param pageSizes size of every page of the document, in points.
 * @return the hit, or `null` when the tap is outside every page (margins, gaps between pages).
 */
internal fun hitTest(x: Float, y: Float, placements: List<PagePlacement>, pageSizes: List<PageSize>): PageHit? {
    val placement =
        placements.firstOrNull { x >= it.left && x < it.left + it.width && y >= it.top && y < it.top + it.height }
    val page = placement?.let { pageSizes.getOrNull(it.pageIndex) }
    if (placement == null || page == null) return null
    val pointsPerPixel = page.width / placement.width
    return PageHit(
        pageIndex = placement.pageIndex,
        point = PdfPoint((x - placement.left) * pointsPerPixel, (y - placement.top) * page.height / placement.height),
        pointsPerPixel = pointsPerPixel,
    )
}

/**
 * A page shown by the continuous list ([PdfPagesView]).
 *
 * @property pageIndex zero-based page.
 * @property top y of the top edge of its item in the reading area, in pixels: the offset of
 *   the item minus the start of the viewport, which is negative with a top content padding.
 */
internal data class VisibleItem(val pageIndex: Int, val top: Int)

/**
 * Where the pages of the continuous list are drawn: each item is as wide as the content and
 * holds its page centred at the top; the content scrolls horizontally when zoomed in.
 *
 * @param items the pages laid out by the list.
 * @param pageSizes size of every page, in points.
 * @param width displayed width of the pages at the current zoom, in pixels ([pageWidthPx]).
 * @param contentWidth width of the scrollable content, in pixels, at least the viewport.
 * @param horizontalScroll horizontal scroll of the content, in pixels.
 */
internal fun continuousPlacements(
    items: List<VisibleItem>,
    pageSizes: List<PageSize>,
    width: Int,
    contentWidth: Int,
    horizontalScroll: Int,
): List<PagePlacement> {
    val left = centred(contentWidth, width) - horizontalScroll
    return items.mapNotNull { item ->
        pageSizes.getOrNull(item.pageIndex)?.let { page ->
            PagePlacement(item.pageIndex, left.toFloat(), item.top.toFloat(), width, pageHeightPx(page, width))
        }
    }
}

/**
 * Layout of one page in page-by-page mode ([PdfPagerView]): a zoomable area, the viewport above
 * the floating bar scaled by the zoom, holding the page centred; then the clearance of the bar,
 * which does not scale.
 *
 * @property areaWidth width of the zoomable area, in pixels.
 * @property areaHeight height of the zoomable area, in pixels, without the clearance.
 * @property clearance free space under the area, in pixels.
 * @property fitWidth width of the page at zoom 1, in pixels.
 */
internal data class PagedPageLayout(val areaWidth: Int, val areaHeight: Int, val clearance: Int, val fitWidth: Int) {
    /**
     * Where the page is drawn in the reading area at [zoom], with the area scrolled by
     * ([scrollX], [scrollY]) pixels.
     */
    fun placement(pageIndex: Int, page: PageSize, zoom: Float, scrollX: Int, scrollY: Int): PagePlacement {
        val width = pageWidthPx(fitWidth, zoom)
        val height = pageHeightPx(page, width)
        return PagePlacement(
            pageIndex = pageIndex,
            left = (centred(areaWidth, width) - scrollX).toFloat(),
            top = (centred(areaHeight, height) - scrollY).toFloat(),
            width = width,
            height = height,
        )
    }
}

/**
 * Layout of [page] in page-by-page mode, at [zoom].
 *
 * @param viewport size of the reading area, in pixels.
 * @param bottomClearance height of the floating bar over the bottom of the page, in pixels; never
 *   more than half the viewport, so that a window too low for the bar still shows the page.
 * @param margin free space around a page fitted to the screen, in pixels.
 */
internal fun pagedPageLayout(
    page: PageSize,
    viewport: IntSize,
    bottomClearance: Int,
    margin: Int,
    zoom: Float,
): PagedPageLayout {
    val clearance = minOf(bottomClearance, viewport.height / 2).coerceAtLeast(0)
    val pageAreaHeight = viewport.height - clearance
    return PagedPageLayout(
        areaWidth = zoomedExtent(viewport.width, zoom),
        areaHeight = zoomedExtent(pageAreaHeight, zoom),
        clearance = clearance,
        fitWidth = fitPageWidth(page, viewport.width, pageAreaHeight, margin),
    )
}

/**
 * The link under [point]: the first one, in document order, whose area holds it; otherwise the
 * nearest one within [tolerance], since a finger covers more than a small link.
 *
 * @param links links of the page.
 * @param point tapped point, in page coordinates.
 * @param tolerance largest distance from the area of a link, in page points; 0 for none.
 * @return the link, or `null` when none is close enough.
 */
internal fun linkAt(links: List<PdfLink>, point: PdfPoint, tolerance: Float): PdfLink? =
    links.firstOrNull { it.bounds.contains(point.x, point.y) }
        ?: links
            .map { it to it.bounds.distanceTo(point) }
            .filter { (_, distance) -> distance <= tolerance }
            .minByOrNull { (_, distance) -> distance }
            ?.first

/** Distance from [point] to the nearest edge of the rectangle; 0 inside it. */
private fun PdfRect.distanceTo(point: PdfPoint): Float {
    val dx = maxOf(left - point.x, 0f, point.x - right)
    val dy = maxOf(top - point.y, 0f, point.y - bottom)
    return hypot(dx, dy)
}

/** Offset of an item of [size] centred in [space], rounded as Compose's `Alignment.Center` does. */
private fun centred(space: Int, size: Int): Int = ((space - size) / 2f).roundToInt()
