// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.feature.reader.render.currentPage

/**
 * Where the reader is in the document, and how to jump elsewhere, whatever the scroll mode
 * (F03): the page indicator, the slider (F05) and the ViewModel only know this interface.
 */
@Stable
internal sealed interface PageNavigator {
    /** Zero-based page shown in the page indicator; snapshot state, so reading it observes it. */
    val currentPage: Int

    /**
     * Shows [page] at once, without animation, and, in continuous scroll, brings the point
     * [yOffset] of that page to the top of the reading area (a destination of the outline or of
     * a link, F06, F09).
     *
     * Page by page, a page is always shown whole at zoom 1, and the zoom goes back to 1 when
     * another page settles: the destination is then in view and [yOffset] changes nothing.
     * Within the page already shown and zoomed in, the offset is not applied either.
     *
     * @param page zero-based page, within the document.
     * @param yOffset distance from the top edge of the page, in PDF points (the unit of
     *   [PageSize]); clamped to the page height, 0 shows the top of the page.
     */
    suspend fun goTo(page: Int, yOffset: Float = 0f)

    /**
     * Shows [area] of [page] in the middle of the reading area, without animation: an
     * occurrence of a search (F07). In continuous scroll, the middle of [area] goes to the middle
     * of the part of the reading area above the floating controls; page by page, the page is shown
     * whole, as [goTo] does.
     *
     * @param page zero-based page, within the document.
     * @param area part of the page to show, in page coordinates (points, from the top-left corner).
     */
    suspend fun reveal(page: Int, area: PdfRect)
}

/**
 * Continuous vertical scroll: the current page is the one occupying the middle of the screen.
 *
 * @property listState scroll position of the list of pages.
 * @property pageSizes size of every page, in points: gives the height of a page, to turn a
 *   distance in points into pixels.
 */
internal class ContinuousNavigator(val listState: LazyListState, private val pageSizes: List<PageSize>) :
    PageNavigator {
    override val currentPage: Int by derivedStateOf { listState.currentPageIndex(pageSizes.size) }

    override suspend fun goTo(page: Int, yOffset: Float) {
        listState.scrollToItem(page)
        if (yOffset <= 0f) return
        // The page is laid out now, at the zoom and the width of the screen: its height in
        // pixels is read there, instead of repeating the layout computation here.
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == page } ?: return
        val fraction = (yOffset / pageSizes[page].height).coerceIn(0f, 1f)
        listState.scrollBy(fraction * item.size)
    }

    override suspend fun reveal(page: Int, area: PdfRect) {
        listState.scrollToItem(page)
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == page } ?: return
        val centre = ((area.top + area.bottom) / 2f / pageSizes[page].height).coerceIn(0f, 1f) * item.size
        // The end padding keeps the last page clear of the floating controls: the free part of
        // the reading area is above it.
        val visibleHeight = (layout.viewportSize.height - layout.afterContentPadding).coerceAtLeast(0)
        // The page starts at the top of the viewport once scrolled to, after the top padding.
        listState.scrollBy(centre + item.offset - layout.viewportStartOffset - visibleHeight / 2f)
    }
}

/**
 * Page by page: the current page is the one filling most of the screen during a swipe.
 *
 * @property pagerState position of the pager.
 */
internal class PagedNavigator(val pagerState: PagerState) : PageNavigator {
    override val currentPage: Int get() = pagerState.currentPage

    override suspend fun goTo(page: Int, yOffset: Float) = pagerState.scrollToPage(page)

    override suspend fun reveal(page: Int, area: PdfRect) = pagerState.scrollToPage(page)
}

/**
 * The navigator of [mode], starting on [initialPage]. Each mode keeps its own saved position;
 * a new navigator starts when the mode changes, so the caller passes the page to keep.
 *
 * @param mode scroll mode of the reader.
 * @param initialPage zero-based page shown first.
 * @param pageSizes size of every page of the document, at least one.
 */
@Composable
internal fun rememberPageNavigator(mode: ScrollMode, initialPage: Int, pageSizes: List<PageSize>): PageNavigator =
    when (mode) {
        ScrollMode.CONTINUOUS -> {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
            remember(listState, pageSizes) { ContinuousNavigator(listState, pageSizes) }
        }

        ScrollMode.PAGED -> {
            val pagerState = rememberPagerState(initialPage = initialPage) { pageSizes.size }
            remember(pagerState) { PagedNavigator(pagerState) }
        }
    }

/** Page in the middle of the screen, from the first visible page and how much of it is scrolled out. */
private fun LazyListState.currentPageIndex(pageCount: Int): Int {
    // The visible items may start with the page just above, touching the top edge: use the item
    // the scroll offset belongs to, or the indicator shows the previous page after a jump.
    val first = layoutInfo.visibleItemsInfo.firstOrNull { it.index == firstVisibleItemIndex }
        ?: return firstVisibleItemIndex
    return currentPage(
        first.index,
        firstVisibleItemScrollOffset,
        first.size + layoutInfo.mainAxisItemSpacing,
        pageCount,
    )
}
