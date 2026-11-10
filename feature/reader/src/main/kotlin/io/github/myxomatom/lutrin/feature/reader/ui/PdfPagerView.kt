// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import io.github.myxomatom.lutrin.feature.reader.render.PinchTransform
import io.github.myxomatom.lutrin.feature.reader.render.clampZoom
import io.github.myxomatom.lutrin.feature.reader.render.fitPageWidth
import io.github.myxomatom.lutrin.feature.reader.render.scrollAfterGesture
import io.github.myxomatom.lutrin.feature.reader.render.zoomedExtent
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop

/** Free space around a page fitted to the screen; scaled with the zoom, like the whole page area. */
private val PAGE_MARGIN = 16.dp

/**
 * Pages of a document one per screen, changed with a horizontal swipe (F03, ADR 0011), with
 * pinch and double-tap zoom (F04) within the page. Zoom 1 fits the whole page in the screen.
 *
 * While zoomed in beyond 1, drags pan the page and swiping to another page is off: a double
 * tap or the "fit" button brings the page back, and swiping with it. The zoom goes back to 1
 * whenever another page settles. Only the pages on screen are composed: a page that leaves the
 * screen cancels its renderings (ADR 0008). Pages always run left to right, even when the
 * interface is right to left.
 *
 * @param pagerState current page and swipe state; hoisted so that the slider can move it.
 * @param zoom current zoom, 1 = whole page; hoisted so that the screen can reset it.
 * @param onZoomChange called with the new, clamped zoom.
 * @param onTap called on a single tap, to show or hide the controls (F14).
 * @param contentDescription what TalkBack announces for the document area (F18).
 * @param bottomClearance height of the floating bar laid over the bottom of the pages (ADR 0018):
 *   a page at zoom 1 fits in the space above it, and a zoomed page can scroll above it.
 * @param sharedPage the page that is the shared element of the thumbnail transition, if any.
 */
@Composable
internal fun PdfPagerView(
    pageSizes: List<PageSize>,
    renderer: PageRenderer,
    pagerState: PagerState,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onTap: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    nightMode: Boolean = false,
    bottomClearance: Dp = 0.dp,
    sharedPage: SharedPage? = null,
) {
    ResetZoomOnPageChange(pagerState, zoom, onZoomChange)
    // Pages follow each other left to right whatever the language of the interface: the
    // reading direction belongs to the document, not to the app (ADR 0011).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        HorizontalPager(
            state = pagerState,
            modifier = modifier.fillMaxSize().semantics { this.contentDescription = contentDescription },
            userScrollEnabled = zoom <= 1f,
            key = { it },
        ) { index ->
            ZoomablePage(
                pageIndex = index,
                page = pageSizes[index],
                renderer = renderer,
                zoom = zoom,
                onZoomChange = onZoomChange,
                onTap = onTap,
                pagerMoving = pagerState.isScrollInProgress,
                nightMode = nightMode,
                bottomClearance = bottomClearance,
                sharedPage = sharedPage,
            )
        }
    }
}

/** Brings the zoom back to "whole page" when another page settles, whichever way it was reached. */
@Composable
private fun ResetZoomOnPageChange(pagerState: PagerState, zoom: Float, onZoomChange: (Float) -> Unit) {
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { if (currentZoom != 1f) currentOnZoomChange(1f) }
    }
}

/**
 * One page, fitted to the screen at zoom 1. Zoomed in, the page area grows with the zoom and
 * scrolls in both directions; during a pinch it is only scaled visually ([pinchLayer]).
 */
@Suppress("LongParameterList") // Plain wiring of the state hoisted in PdfPagerView.
@Composable
private fun ZoomablePage(
    pageIndex: Int,
    page: PageSize,
    renderer: PageRenderer,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onTap: () -> Unit,
    pagerMoving: Boolean,
    nightMode: Boolean,
    bottomClearance: Dp,
    sharedPage: SharedPage?,
) {
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    var pinch by remember { mutableStateOf<PinchTransform?>(null) }
    var viewport by remember { mutableStateOf<Viewport?>(null) }
    val commit = rememberPageCommit(zoom, onZoomChange, horizontal, vertical)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { viewport = Viewport(it, it.size) }
                .zoomGestures(zoom, onPinch = { pinch = it }, commit = commit, onTap = onTap)
                .pinchLayer { pinch }
                .verticalScroll(vertical, enabled = zoom > 1f)
                .horizontalScroll(horizontal, enabled = zoom > 1f),
        ) {
            ZoomedArea(viewportSize, page, zoom, bottomClearance) { fitWidth ->
                PdfPage(
                    pageIndex = pageIndex,
                    page = page,
                    renderer = renderer,
                    fitWidth = fitWidth,
                    viewport = viewport,
                    zoom = zoom,
                    settled = pinch == null && !pagerMoving && !horizontal.isScrollInProgress &&
                        !vertical.isScrollInProgress,
                    nightMode = nightMode,
                    pageModifier = Modifier.sharedPage(sharedPage, pageIndex),
                )
            }
        }
    }
}

/**
 * Pinch, double tap and tap on a page: a pinch is reported at every move, then `null` once it is
 * committed; a double tap is committed at once.
 */
@Composable
private fun Modifier.zoomGestures(
    zoom: Float,
    onPinch: (PinchTransform?) -> Unit,
    commit: (PinchTransform) -> Unit,
    onTap: () -> Unit,
): Modifier = readerGestures(
    zoom = zoom,
    onPinch = onPinch,
    onPinchEnd = { transform ->
        commit(transform)
        onPinch(null)
    },
    onDoubleTap = { focus -> commit(doubleTapTransform(zoom, focus)) },
    onTap = onTap,
)

/**
 * The page area at [zoom]: the viewport above the [bottomClearance] scaled by the zoom
 * ([zoomedExtent]), the page centred in it, followed by the clearance itself, which does not
 * scale. [content] draws the page and receives its width at zoom 1, fitting it above the
 * clearance. At zoom 1 the whole area is the viewport: nothing scrolls.
 */
@Composable
private fun ZoomedArea(
    viewportSize: IntSize,
    page: PageSize,
    zoom: Float,
    bottomClearance: Dp,
    content: @Composable (fitWidth: Int) -> Unit,
) {
    val density = LocalDensity.current
    val margin = with(density) { PAGE_MARGIN.roundToPx() }
    // Never the whole viewport: a window too low for the bar still shows some of the page.
    val clearance = minOf(with(density) { bottomClearance.roundToPx() }, viewportSize.height / 2).coerceAtLeast(0)
    val pageAreaHeight = viewportSize.height - clearance
    val width = with(density) { zoomedExtent(viewportSize.width, zoom).toDp() }
    val height = with(density) { zoomedExtent(pageAreaHeight, zoom).toDp() }
    Box(Modifier.size(width, height + with(density) { clearance.toDp() }), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
            content(fitPageWidth(page, viewportSize.width, pageAreaHeight, margin))
        }
    }
}

/**
 * Returns a function applying a finished gesture to a page: sets the new zoom and, once the
 * larger page area is laid out, scrolls it so that the page stays where the gesture showed it.
 */
@Composable
private fun rememberPageCommit(
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    horizontal: ScrollState,
    vertical: ScrollState,
): (PinchTransform) -> Unit {
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    var pending by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(zoom, pending) {
        // The scroll ranges only grow once the new zoom is laid out: scroll afterwards.
        pending?.let {
            horizontal.scrollTo(it.x.roundToInt())
            vertical.scrollTo(it.y.roundToInt())
        }
        pending = null
    }
    return remember(horizontal, vertical) {
        { transform ->
            val newZoom = clampZoom(currentZoom * transform.scale)
            val scale = newZoom / currentZoom
            if (scale != 1f || !transform.isIdentity) {
                currentOnZoomChange(newZoom)
                pending =
                    Offset(
                        scrollAfterGesture(horizontal.value.toFloat(), scale, transform.translationX).coerceAtLeast(0f),
                        scrollAfterGesture(vertical.value.toFloat(), scale, transform.translationY).coerceAtLeast(0f),
                    )
            }
        }
    }
}
