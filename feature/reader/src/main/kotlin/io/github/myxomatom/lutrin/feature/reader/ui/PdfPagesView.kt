// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import io.github.myxomatom.lutrin.feature.reader.render.clampZoom
import io.github.myxomatom.lutrin.feature.reader.render.doubleTapZoom
import io.github.myxomatom.lutrin.feature.reader.render.pageWidthPx
import io.github.myxomatom.lutrin.feature.reader.render.scrollAfterZoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Gap between two pages at zoom 1; scaled with the zoom so that zooming keeps its focal point. */
private val PAGE_SPACING = 8.dp

/** Margin on each side of a page at "fit width" (mockup E2), scaled with the zoom too. */
private val PAGE_GUTTER = 16.dp

/**
 * Pages of a document in a continuous vertical scroll (F03), with pinch and double-tap zoom
 * (F04). Single-finger gestures scroll; two-finger gestures zoom around their centroid.
 *
 * @param zoom current zoom, 1 = fit width; hoisted so that the screen can reset it.
 * @param onZoomChange called with the new, clamped zoom.
 * @param onTap called on a single tap, to show or hide the controls (F14).
 * @param contentDescription what TalkBack announces for the document area (F18).
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
) {
    val horizontal = rememberScrollState()
    var pinching by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf<Viewport?>(null) }
    val zoomTo = rememberZoomTo(zoom, onZoomChange, listState, horizontal)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gutterPx = with(LocalDensity.current) { PAGE_GUTTER.roundToPx() }
        // "Fit width" leaves a margin on both sides: pages are sized against the remaining width.
        val fitWidth = (constraints.maxWidth - 2 * gutterPx).coerceAtLeast(1)
        val contentWidth = maxOf(constraints.maxWidth, pageWidthPx(fitWidth, zoom) + (2 * gutterPx * zoom).toInt())
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { viewport = Viewport(it, it.size) }
                    .readerGestures(zoom, zoomTo, onPinching = { pinching = it }, onTap = onTap)
                    .horizontalScroll(horizontal, enabled = zoom > 1f)
                    .semantics { this.contentDescription = contentDescription },
            contentPadding = PaddingValues(vertical = PAGE_SPACING * zoom),
            verticalArrangement = Arrangement.spacedBy(PAGE_SPACING * zoom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(pageSizes, key = { index, _ -> index }) { index, page ->
                PdfPage(
                    pageIndex = index,
                    page = page,
                    renderer = renderer,
                    fitWidth = fitWidth,
                    viewport = viewport,
                    zoom = zoom,
                    settled = !pinching && !listState.isScrollInProgress && !horizontal.isScrollInProgress,
                    nightMode = nightMode,
                    modifier = Modifier.width(with(LocalDensity.current) { contentWidth.toDp() }),
                )
            }
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

/** Returns a function setting a new zoom (clamped) while keeping the given focal point still. */
@Composable
private fun rememberZoomTo(
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    listState: LazyListState,
    horizontal: ScrollState,
): (Float, Offset) -> Unit {
    val scope = rememberCoroutineScope()
    return { target, focus ->
        val factor = clampZoom(target) / zoom
        if (factor != 1f) {
            onZoomChange(clampZoom(target))
            scope.keepFocus(listState, horizontal, focus, factor)
        }
    }
}

/** Pinch and double tap zoom to [zoomTo] (new zoom, focal point); a single tap calls [onTap]. */
private fun Modifier.readerGestures(
    zoom: Float,
    zoomTo: (Float, Offset) -> Unit,
    onPinching: (Boolean) -> Unit,
    onTap: () -> Unit,
): Modifier = pinchToZoom(onPinch = { factor, centroid -> zoomTo(zoom * factor, centroid) }, onActive = onPinching)
    .pointerInput(zoom) {
        detectTapGestures(onTap = { onTap() }, onDoubleTap = { zoomTo(doubleTapZoom(zoom), it) })
    }

/** Moves both scroll positions so that the content under [focus] stays under it after zooming by [factor]. */
private fun CoroutineScope.keepFocus(listState: LazyListState, horizontal: ScrollState, focus: Offset, factor: Float) {
    launch {
        val offset = scrollAfterZoom(listState.firstVisibleItemScrollOffset.toFloat(), focus.y, factor)
        listState.scrollToItem(listState.firstVisibleItemIndex, offset.toInt().coerceAtLeast(0))
    }
    launch {
        horizontal.scrollTo(scrollAfterZoom(horizontal.value.toFloat(), focus.x, factor).toInt().coerceAtLeast(0))
    }
}

/**
 * Detects two-finger pinches without stealing single-finger scrolling from the list.
 *
 * @param onPinch zoom factor since the previous event, and the centroid of the fingers.
 * @param onActive whether a pinch is in progress (renderings wait for its end).
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.pinchToZoom(onPinch: (Float, Offset) -> Unit, onActive: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    onActive(true)
                    val factor = event.calculateZoom()
                    if (factor != 1f) onPinch(factor, event.calculateCentroid())
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            onActive(false)
        }
    }
