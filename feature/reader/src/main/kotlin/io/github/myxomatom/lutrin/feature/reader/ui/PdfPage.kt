// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import android.graphics.Bitmap
import androidx.activity.compose.ReportDrawnWhen
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
import androidx.compose.ui.zIndex
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.RenderOptions
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import io.github.myxomatom.lutrin.feature.reader.render.PixelRect
import io.github.myxomatom.lutrin.feature.reader.render.RenderResult
import io.github.myxomatom.lutrin.feature.reader.render.baseRenderWidth
import io.github.myxomatom.lutrin.feature.reader.render.bitmapOrNull
import io.github.myxomatom.lutrin.feature.reader.render.pageHeightPx
import io.github.myxomatom.lutrin.feature.reader.render.pageWidthPx
import io.github.myxomatom.lutrin.feature.reader.render.visiblePart
import kotlinx.coroutines.delay

/** Wait after the last gesture before rendering the sharp detail of the visible part. */
private const val DETAIL_DELAY_MS = 150L

/** Shadow lifting the page off the reading background (mockup E2). */
internal val PAGE_ELEVATION = 1.dp

/**
 * Paper of the pages drawn with [options]: the colour the engine gives a blank page (ADR 0028),
 * shown until the rendering arrives and under a damaged page. White in the original colours,
 * whatever the theme of the app.
 */
internal fun paperOf(options: RenderOptions): Color = Color(options.colorMode.paper)

/**
 * One page: a base rendering of the whole page, stretched to the displayed size, and, when
 * zoomed in beyond "fit width", a sharp rendering of the visible part drawn on top (F02).
 * Both renderings are cancelled when the page leaves the screen. A page the engine cannot read
 * shows [DamagedPageMessage] on its paper instead (ADR 0031). The activity is reported fully drawn
 * once the pages first shown have their base rendering, or are known to be damaged.
 *
 * @param options how the page is drawn (ADR 0028): its colours, original, night or sepia. A change
 *   renders the page again, on the paper of the new colours.
 * @param pageModifier modifier applied to the page itself, first (the paper, not the margins
 *   around it): the shared element of the thumbnail transition ([SharedPage], ADR 0019).
 * @param highlights occurrences of the search drawn over the page (F07), `null` for none.
 * @param selection the text selected on this page (F08), with its handles and action bar;
 *   `null` for none.
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
    options: RenderOptions,
    modifier: Modifier = Modifier,
    pageModifier: Modifier = Modifier,
    highlights: PageHighlights? = null,
    selection: PageSelection? = null,
) {
    val displayedWidth = pageWidthPx(fitWidth, zoom)
    val baseWidth = baseRenderWidth(fitWidth, zoom)
    val base by produceState<RenderResult?>(null, pageIndex, baseWidth, options) {
        value = renderer.renderPage(pageIndex, baseWidth, options)
    }
    // The activity reports itself fully drawn once every page first shown has its rendering:
    // the end of "open a document" for the startup metrics of :benchmark (ADR 0012). A damaged
    // page is drawn too, with its message, or the measure would never end (ADR 0031).
    // A no-op after that first report, and outside an activity.
    ReportDrawnWhen { base is RenderResult.Rendered || base is RenderResult.Unreadable }
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
            options = options,
            coordinates = coordinates,
        )
    val density = LocalDensity.current
    val displayedHeight = pageHeightPx(page, displayedWidth)
    // Above the next page while selected: the handles of its last line may hang over it.
    Box(modifier.zIndex(if (selection != null) 1f else 0f), contentAlignment = Alignment.TopCenter) {
        Box(
            pageModifier
                .size(with(density) { displayedWidth.toDp() }, with(density) { displayedHeight.toDp() })
                .shadow(PAGE_ELEVATION)
                .background(paperOf(options))
                .onGloballyPositioned { coordinates = it },
        ) {
            PageImages(base, detail, options.colorMode.isDark)
            SearchHighlightLayer(highlights, page)
            SelectionHighlightLayer(selection?.selection, page)
        }
        // Outside the page, which clips: a handle may hang below the last line of the page.
        selection?.let { TextSelectionOverlay(it, page, displayedWidth, displayedHeight) }
    }
}

/**
 * The base rendering stretched over the page, and the sharp detail drawn over its part; the
 * message of a damaged page instead when the page cannot be read (ADR 0031).
 */
@Composable
private fun PageImages(base: RenderResult?, detail: Pair<PixelRect, Bitmap>?, darkPaper: Boolean) {
    if (base is RenderResult.Unreadable) {
        DamagedPageMessage(darkPaper, Modifier.fillMaxSize())
        return
    }
    Box(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        base?.bitmapOrNull?.let {
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
    options: RenderOptions,
    coordinates: LayoutCoordinates?,
): Pair<PixelRect, Bitmap>? {
    // Forgotten with the options: a detail of the previous colours is never drawn over the page.
    var detail by remember(pageIndex, zoom, options) { mutableStateOf<Pair<PixelRect, Bitmap>?>(null) }
    LaunchedEffect(pageIndex, zoom, settled, options, coordinates, viewport) {
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
            renderer.renderPart(pageIndex, displayedWidth, part, options).bitmapOrNull?.let { detail = part to it }
        }
    }
    return detail
}
