// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.ui.theme.ReaderColors

/** Margin on each side of a page fitted to the width (E2 mockup: 24 dp). */
private val PageMargin = 24.dp

/** Gap between two pages. */
private val PageSpacing = 12.dp

/** Space above the first page, below the top bar. */
private val ListTopPadding = 80.dp

/** Space below the last page, above the page bar. */
private val ListBottomPadding = 88.dp

/** Elevation of a page above the background. */
private val PageElevation = 1.dp

/** Below this luminance, a surface colour belongs to a dark scheme. */
private const val HALF_LUMINANCE = 0.5f

/** Pages are rendered at widths rounded up to this step, so that a pinch does not redraw them at every frame. */
internal const val RENDER_WIDTH_STEP_PX: Int = 256

/** Width in pixels a page displayed [displayWidthPx] wide is rendered at. */
internal fun renderWidthFor(displayWidthPx: Int): Int =
    ((displayWidthPx + RENDER_WIDTH_STEP_PX - 1) / RENDER_WIDTH_STEP_PX).coerceAtLeast(1) * RENDER_WIDTH_STEP_PX

/**
 * The pages of the document in a vertical list (F03) that can be zoomed (F04): pinch, double
 * tap; a single tap toggles the bars (F14). Only pages near the screen are composed, and the
 * rendering of a page that leaves the screen is cancelled with its composition (`AGENTS.md` §8).
 */
@Composable
internal fun DocumentPages(
    pageSizes: List<PageSize>,
    listState: LazyListState,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onTap: () -> Unit,
    loadPage: suspend (pageIndex: Int, widthPx: Int) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    // The gesture detectors outlive recompositions: they read the latest values through these.
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val currentOnTap by rememberUpdatedState(onTap)
    // Follows the app theme (which may differ from the system setting), not Material You.
    val isDark = MaterialTheme.colorScheme.surface.luminance() < HALF_LUMINANCE
    val background = if (isDark) ReaderColors.BackgroundNight else ReaderColors.Background
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(background)
            .pinchToZoom { factor -> currentOnZoomChange(ZoomLevels.pinched(currentZoom, factor)) }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { currentOnZoomChange(ZoomLevels.doubleTapped(currentZoom)) },
                )
            },
    ) {
        val pageWidth = (maxWidth - PageMargin * 2) * zoom
        val listWidth = maxOf(pageWidth + PageMargin * 2, maxWidth)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(listWidth).fillMaxHeight(),
                contentPadding = listPadding(),
                verticalArrangement = Arrangement.spacedBy(PageSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(pageSizes, key = { index, _ -> index }) { index, size ->
                    PageItem(index, pageSizes.size, size, pageWidth, loadPage)
                }
            }
        }
    }
}

/** Padding of the list: bars and system bars, ignoring their visibility so pages never jump. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun listPadding(): PaddingValues {
    val systemBars = WindowInsets.systemBarsIgnoringVisibility.asPaddingValues()
    return PaddingValues(
        top = systemBars.calculateTopPadding() + ListTopPadding,
        bottom = systemBars.calculateBottomPadding() + ListBottomPadding,
    )
}

@Composable
private fun PageItem(
    index: Int,
    pageCount: Int,
    size: PageSize,
    width: Dp,
    loadPage: suspend (pageIndex: Int, widthPx: Int) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val renderWidth = renderWidthFor(with(LocalDensity.current) { width.roundToPx() })
    val currentLoadPage by rememberUpdatedState(loadPage)
    // Keeps the previous rendering on screen while a sharper one is drawn after a zoom.
    var image by remember(index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(index, renderWidth) {
        currentLoadPage(index, renderWidth)?.let { image = it }
    }
    val description = stringResource(R.string.reader_page_description, index + 1, pageCount)
    Box(
        modifier
            .width(width)
            .aspectRatio(size.width / size.height)
            .shadow(PageElevation)
            .background(ReaderColors.PageNormal)
            .semantics { contentDescription = description },
    ) {
        image?.let {
            Image(
                it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

/**
 * Reports the zoom factor of two-finger gestures and consumes them, so that the list does not
 * scroll at the same time. One-finger gestures are left to the list.
 */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val factor = event.calculateZoom()
                if (factor != 1f) onZoom(factor)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
