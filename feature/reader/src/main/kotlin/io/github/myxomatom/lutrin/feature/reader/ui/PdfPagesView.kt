// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.feature.reader.render.ListPosition
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import io.github.myxomatom.lutrin.feature.reader.render.PinchTransform
import io.github.myxomatom.lutrin.feature.reader.render.clampZoom
import io.github.myxomatom.lutrin.feature.reader.render.listPositionAfterGesture
import io.github.myxomatom.lutrin.feature.reader.render.pageHeightPx
import io.github.myxomatom.lutrin.feature.reader.render.pageWidthPx
import io.github.myxomatom.lutrin.feature.reader.render.scrollAfterGesture

/** Gap between two pages at zoom 1; scaled with the zoom so that zooming keeps its focal point. */
internal val PAGE_SPACING = 8.dp

/** Margin on each side of a page at "fit width" (mockup E2), scaled with the zoom too. */
internal val PAGE_GUTTER = 16.dp

/**
 * Pages of a document in a continuous vertical scroll (F03), with pinch and double-tap zoom
 * (F04). Single-finger gestures scroll; two-finger gestures zoom around their centroid.
 *
 * During a pinch the pages are only scaled visually ([PinchTransform]); the zoom is applied
 * once, when the fingers lift, with the list scrolled so that the pages stay where the gesture
 * left them. Relaying out the list at every move made the zoom stutter and the page jump.
 *
 * @param zoom current zoom, 1 = fit width; hoisted so that the screen can reset it.
 * @param onZoomChange called with the new, clamped zoom.
 * @param onTap called on a single tap, to show or hide the controls (F14).
 * @param contentDescription what TalkBack announces for the document area (F18).
 * @param bottomClearance extra free space after the last page, so that it can scroll above the
 *   floating bar laid over the bottom of the list (ADR 0018); not scaled by the zoom.
 * @param sharedPage the page that is the shared element of the thumbnail transition, if any.
 */
@Composable
internal fun PdfPagesView(
    pageSizes: List<PageSize>,
    renderer: PageRenderer,
    listState: LazyListState,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onTap: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    nightMode: Boolean = false,
    bottomClearance: Dp = 0.dp,
    sharedPage: SharedPage? = null,
) {
    val horizontal = rememberScrollState()
    var pinch by remember { mutableStateOf<PinchTransform?>(null) }
    var pendingHorizontal by remember { mutableStateOf<Int?>(null) }
    PendingHorizontalScroll(zoom, pendingHorizontal, horizontal, onDone = { pendingHorizontal = null })
    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = pagesLayout(constraints.maxWidth, zoom, bottomClearance)
        val commit = rememberCommit(zoom, onZoomChange, listState, horizontal) { newZoom ->
            { index: Int -> layout.itemExtent(pageSizes[index], newZoom) }
        }
        val onCommit: (PinchTransform) -> Unit = { pendingHorizontal = commit(it, pageSizes.size) }
        PagesList(
            pageSizes = pageSizes,
            renderer = renderer,
            listState = listState,
            horizontal = horizontal,
            zoom = zoom,
            layout = layout,
            pinch = pinch,
            nightMode = nightMode,
            sharedPage = sharedPage,
            modifier =
                Modifier
                    .readerGestures(
                        zoom = zoom,
                        onPinch = { pinch = it },
                        onPinchEnd = { transform ->
                            onCommit(transform)
                            pinch = null
                        },
                        onDoubleTap = { focus -> onCommit(doubleTapTransform(zoom, focus)) },
                        onTap = onTap,
                    )
                    .semantics { this.contentDescription = contentDescription },
        )
    }
}

/**
 * Scrolls [horizontal] to [pending] after a gesture, then calls [onDone]: the horizontal range
 * only grows once the new [zoom] is laid out, so the scroll waits for it.
 */
@Composable
private fun PendingHorizontalScroll(zoom: Float, pending: Int?, horizontal: ScrollState, onDone: () -> Unit) {
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(zoom, pending) {
        pending?.let { horizontal.scrollTo(it) }
        currentOnDone()
    }
}

/**
 * Sizes of the pages for a viewport width and a zoom.
 *
 * @property fitWidth page width at zoom 1: the viewport minus a margin on both sides.
 * @property contentWidth width of the scrollable content, at least the viewport.
 * @property spacingPx gap between pages at zoom 1, in pixels.
 * @property bottomClearance free space after the last page, above the floating bar.
 */
private data class PagesLayout(
    val fitWidth: Int,
    val contentWidth: Int,
    val spacingPx: Float,
    val bottomClearance: Dp,
) {
    /** Height of [page] plus the gap below it, at [zoom], in pixels. */
    fun itemExtent(page: PageSize, zoom: Float): Int =
        pageHeightPx(page, pageWidthPx(fitWidth, zoom)) + (spacingPx * zoom).toInt()
}

@Composable
private fun pagesLayout(viewportWidth: Int, zoom: Float, bottomClearance: Dp): PagesLayout {
    val density = LocalDensity.current
    val gutterPx = with(density) { PAGE_GUTTER.roundToPx() }
    // "Fit width" leaves a margin on both sides: pages are sized against the remaining width.
    val fitWidth = (viewportWidth - 2 * gutterPx).coerceAtLeast(1)
    val contentWidth = maxOf(viewportWidth, pageWidthPx(fitWidth, zoom) + (2 * gutterPx * zoom).toInt())
    return PagesLayout(fitWidth, contentWidth, with(density) { PAGE_SPACING.toPx() }, bottomClearance)
}

/** The list of pages, drawn with the [pinch] transform while a pinch is in progress. */
@Suppress("LongParameterList") // Plain wiring of the state hoisted in PdfPagesView.
@Composable
private fun PagesList(
    pageSizes: List<PageSize>,
    renderer: PageRenderer,
    listState: LazyListState,
    horizontal: ScrollState,
    zoom: Float,
    layout: PagesLayout,
    pinch: PinchTransform?,
    nightMode: Boolean,
    sharedPage: SharedPage?,
    modifier: Modifier = Modifier,
) {
    var viewport by remember { mutableStateOf<Viewport?>(null) }
    val density = LocalDensity.current
    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned { viewport = Viewport(it, it.size) }
                .pinchLayer { pinch }
                .horizontalScroll(horizontal, enabled = zoom > 1f),
        contentPadding = PaddingValues(
            top = PAGE_SPACING * zoom,
            bottom = PAGE_SPACING * zoom + layout.bottomClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(PAGE_SPACING * zoom),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(pageSizes, key = { index, _ -> index }) { index, page ->
            PdfPage(
                pageIndex = index,
                page = page,
                renderer = renderer,
                fitWidth = layout.fitWidth,
                viewport = viewport,
                zoom = zoom,
                settled = pinch == null && !listState.isScrollInProgress && !horizontal.isScrollInProgress,
                nightMode = nightMode,
                modifier = Modifier.width(with(density) { layout.contentWidth.toDp() }),
                pageModifier = Modifier.sharedPage(sharedPage, index),
            )
        }
    }
}

/**
 * The reading area, used to find which part of a page is visible.
 *
 * @property coordinates layout coordinates of the reading area.
 * @property size its size, in pixels.
 */
internal data class Viewport(val coordinates: LayoutCoordinates, val size: IntSize)

/**
 * Returns a function applying a finished gesture: sets the new zoom and, in the same frame,
 * the list position that keeps the pages where the gesture showed them. It returns the
 * horizontal scroll to apply once the wider content is laid out.
 *
 * @param itemExtentAt height of page `index` plus its gap, at a given zoom, in pixels.
 */
@Composable
private fun rememberCommit(
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    listState: LazyListState,
    horizontal: ScrollState,
    itemExtentAt: (Float) -> (Int) -> Int,
): (PinchTransform, Int) -> Int? {
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val currentExtent by rememberUpdatedState(itemExtentAt)
    return remember(listState, horizontal) {
        { transform, itemCount ->
            val newZoom = clampZoom(currentZoom * transform.scale)
            val scale = newZoom / currentZoom
            if (scale == 1f && transform.isIdentity) {
                null
            } else {
                val from = ListPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                val to =
                    listPositionAfterGesture(from, scale, transform.translationY, itemCount, currentExtent(newZoom))
                currentOnZoomChange(newZoom)
                // Applied at the next layout, which already uses the new zoom: no intermediate frame.
                listState.requestScrollToItem(to.index, to.offset)
                scrollAfterGesture(horizontal.value.toFloat(), scale, transform.translationX).toInt().coerceAtLeast(0)
            }
        }
    }
}
