// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
     * Shows [page] at once, without animation.
     *
     * @param page zero-based page, within the document.
     */
    suspend fun goTo(page: Int)
}

/**
 * Continuous vertical scroll: the current page is the one occupying the middle of the screen.
 *
 * @property listState scroll position of the list of pages.
 * @param pageCount number of pages of the document.
 */
internal class ContinuousNavigator(val listState: LazyListState, pageCount: Int) : PageNavigator {
    override val currentPage: Int by derivedStateOf { listState.currentPageIndex(pageCount) }

    override suspend fun goTo(page: Int) = listState.scrollToItem(page)
}

/**
 * Page by page: the current page is the one filling most of the screen during a swipe.
 *
 * @property pagerState position of the pager.
 */
internal class PagedNavigator(val pagerState: PagerState) : PageNavigator {
    override val currentPage: Int get() = pagerState.currentPage

    override suspend fun goTo(page: Int) = pagerState.scrollToPage(page)
}

/**
 * The navigator of [mode], starting on [initialPage]. Each mode keeps its own saved position;
 * a new navigator starts when the mode changes, so the caller passes the page to keep.
 *
 * @param mode scroll mode of the reader.
 * @param initialPage zero-based page shown first.
 * @param pageCount number of pages of the document, at least 1.
 */
@Composable
internal fun rememberPageNavigator(mode: ScrollMode, initialPage: Int, pageCount: Int): PageNavigator = when (mode) {
    ScrollMode.CONTINUOUS -> {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
        remember(listState, pageCount) { ContinuousNavigator(listState, pageCount) }
    }

    ScrollMode.PAGED -> {
        val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }
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
