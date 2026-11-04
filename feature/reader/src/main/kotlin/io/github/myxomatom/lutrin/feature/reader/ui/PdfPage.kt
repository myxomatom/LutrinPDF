// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import io.github.myxomatom.lutrin.feature.reader.render.PixelRect
import io.github.myxomatom.lutrin.feature.reader.render.baseRenderWidth
import io.github.myxomatom.lutrin.feature.reader.render.pageHeightPx
import io.github.myxomatom.lutrin.feature.reader.render.pageWidthPx
import io.github.myxomatom.lutrin.feature.reader.render.visiblePart
import kotlinx.coroutines.delay

/** Wait after the last gesture before rendering the sharp detail of the visible part. */
private const val DETAIL_DELAY_MS = 150L

/** Shadow lifting the page off the reading background (mockup E2). */
private val PAGE_ELEVATION = 1.dp

/** Page background before its rendering arrives (pages are white paper, whatever the theme). */
private val PAPER = Color.White

/** Page background in night mode, matching the inverted rendering (design tokens, reader.pageNight). */
private val PAPER_NIGHT = Color(0xFF16181C)

/**
 * One page: a base rendering of the whole page, stretched to the displayed size, and, when
 * zoomed in beyond "fit width", a sharp rendering of the visible part drawn on top (F02).
 * Both renderings are cancelled when the page leaves the screen.
 */
@Composable
internal fun PdfPage(
    pageIndex: Int,
    page: PageSize,
    renderer: PageRenderer,
    fitWidth: Int,
    viewport: Viewport?,
    zoom: Float,
    settled: Boolean,
    nightMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val displayedWidth = pageWidthPx(fitWidth, zoom)
    val baseWidth = baseRenderWidth(fitWidth, zoom)
    val base by produceState<Bitmap?>(null, pageIndex, baseWidth, nightMode) {
        value = renderer.renderPage(pageIndex, baseWidth, nightMode)
    }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val detail =
        rememberDetail(
            renderer = renderer,
            pageIndex = pageIndex,
            page = page,
            displayedWidth = displayedWidth,
            viewport = viewport,
            zoom = zoom,
            settled = settled,
            nightMode = nightMode,
            coordinates = coordinates,
        )
    val density = LocalDensity.current
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .size(
                    with(density) {
                        displayedWidth.toDp()
                    },
                    with(density) { pageHeightPx(page, displayedWidth).toDp() },
                )
                .shadow(PAGE_ELEVATION)
                .background(if (nightMode) PAPER_NIGHT else PAPER)
                .onGloballyPositioned { coordinates = it },
        ) {
            PageImages(base, detail)
        }
    }
}

/** The base rendering stretched over the page, and the sharp detail drawn over its part. */
@Composable
private fun PageImages(base: Bitmap?, detail: Pair<PixelRect, Bitmap>?) {
    Box(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        base?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        detail?.let { (part, bitmap) ->
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier =
                    Modifier
                        .offset { IntOffset(part.left, part.top) }
                        .size(with(density) { part.width.toDp() }, with(density) { part.height.toDp() }),
            )
        }
    }
}

/** Sharp rendering of the visible part of the page, once gestures have settled; `null` at zoom ≤ 1. */
@Suppress("LongParameterList") // Every input changes what must be rendered.
@Composable
private fun rememberDetail(
    renderer: PageRenderer,
    pageIndex: Int,
    page: PageSize,
    displayedWidth: Int,
    viewport: Viewport?,
    zoom: Float,
    settled: Boolean,
    nightMode: Boolean,
    coordinates: LayoutCoordinates?,
): Pair<PixelRect, Bitmap>? {
    var detail by remember(pageIndex, zoom, nightMode) { mutableStateOf<Pair<PixelRect, Bitmap>?>(null) }
    LaunchedEffect(pageIndex, zoom, settled, nightMode, coordinates, viewport) {
        if (zoom <= 1f || !settled) return@LaunchedEffect
        val pageCoordinates = coordinates?.takeIf { it.isAttached } ?: return@LaunchedEffect
        val (viewportCoordinates, viewportSize) = viewport?.takeIf { it.coordinates.isAttached }
            ?: return@LaunchedEffect
        delay(DETAIL_DELAY_MS)
        // Position of the page's top-left corner in the reading area (negative when scrolled past).
        val position = viewportCoordinates.localPositionOf(pageCoordinates, Offset.Zero)
        val displayedHeight = (displayedWidth * page.height / page.width).toInt()
        val part =
            visiblePart(
                position.x,
                position.y,
                displayedWidth,
                displayedHeight,
                viewportSize.width,
                viewportSize.height,
            )
        if (part != null && part != detail?.first) {
            renderer.renderPart(pageIndex, displayedWidth, part, nightMode)?.let { detail = part to it }
        }
    }
    return detail
}
