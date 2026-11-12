// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransition
import io.github.myxomatom.lutrin.core.ui.transition.sharedDocumentPage
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import kotlinx.coroutines.launch

/** Opacity of the reading background over the document during a preview (the mockup shows it at 55 %). */
private const val PREVIEW_SCRIM_ALPHA = 0.45f

/** Margin of the page number in full screen. */
private val FullscreenIndicatorPadding = 16.dp

/**
 * The reading area under the top bar: the pages, which run down to the bottom of the screen, and
 * over them the floating bar (ADR 0018) or, in full screen, a discreet page number. While the
 * slider previews a page, the pages are dimmed.
 *
 * @param state the open document.
 * @param navigator where the reader is, in the current scroll mode.
 * @param scrubber state of the slider gesture.
 * @param zoom current zoom, 1 = fit.
 * @param onZoomChange called with the new zoom.
 * @param controlsVisible whether the bars are shown (false in full screen, F14).
 * @param onTap called on a single tap on the pages.
 * @param onIndicatorClick called by a tap on the page indicator of the floating bar (V3).
 * @param modifier modifier applied to the area; it keeps clear of the top bar and the sides.
 * @param pageTransition the transition of the page shown, back to its thumbnail in the library
 *   (ADR 0019); `null` when the reader has none.
 */
@Suppress("LongParameterList") // Plain wiring of the state hoisted in ReaderContent.
@Composable
internal fun ReaderDocumentArea(
    state: ReaderUiState.Ready,
    navigator: PageNavigator,
    scrubber: PageScrubber,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    controlsVisible: Boolean,
    onTap: () -> Unit,
    onIndicatorClick: () -> Unit,
    modifier: Modifier = Modifier,
    pageTransition: DocumentTransition? = null,
) {
    val page = navigator.currentPage
    val pageCount = state.pageSizes.size
    val sharedPage = sharedPageIndex(page, zoom, pageTransition != null)?.let {
        SharedPage(it, Modifier.sharedDocumentPage(pageTransition))
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        DocumentPages(
            state = state,
            navigator = navigator,
            zoom = zoom,
            onZoomChange = onZoomChange,
            onTap = onTap,
            contentDescription = stringResource(R.string.reader_document, page + 1, pageCount),
            bottomClearance = if (controlsVisible) floatingPageBarClearance() else 0.dp,
            sharedPage = sharedPage,
        )
        if (scrubber.isPreviewing) PreviewScrim()
        if (controlsVisible) {
            ReaderPageBar(
                state = state,
                page = page,
                navigator = navigator,
                scrubber = scrubber,
                onIndicatorClick = onIndicatorClick,
                onFit = { onZoomChange(1f) },
                previewMaxHeight = maxHeight,
            )
        } else {
            FullscreenPageIndicator(page, pageCount, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** The pages, in the view of the current scroll mode (F03). */
@Suppress("LongParameterList") // Plain wiring of the state hoisted in ReaderContent.
@Composable
private fun DocumentPages(
    state: ReaderUiState.Ready,
    navigator: PageNavigator,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onTap: () -> Unit,
    contentDescription: String,
    bottomClearance: Dp,
    sharedPage: SharedPage?,
) {
    when (navigator) {
        is ContinuousNavigator ->
            PdfPagesView(
                pageSizes = state.pageSizes,
                renderer = state.renderer,
                listState = navigator.listState,
                zoom = zoom,
                onZoomChange = onZoomChange,
                onTap = onTap,
                contentDescription = contentDescription,
                bottomClearance = bottomClearance,
                sharedPage = sharedPage,
            )

        is PagedNavigator ->
            PdfPagerView(
                pageSizes = state.pageSizes,
                renderer = state.renderer,
                pagerState = navigator.pagerState,
                zoom = zoom,
                onZoomChange = onZoomChange,
                onTap = onTap,
                contentDescription = contentDescription,
                bottomClearance = bottomClearance,
                sharedPage = sharedPage,
            )
    }
}

/**
 * The floating bar, aligned at the bottom of the reading area, moving the [navigator]. After a
 * preview, the slider shows the page aimed at until the document has reached it.
 */
@Suppress("LongParameterList") // Plain wiring of the state hoisted in ReaderContent.
@Composable
private fun BoxScope.ReaderPageBar(
    state: ReaderUiState.Ready,
    page: Int,
    navigator: PageNavigator,
    scrubber: PageScrubber,
    onIndicatorClick: () -> Unit,
    onFit: () -> Unit,
    previewMaxHeight: Dp,
) {
    val scope = rememberCoroutineScope()
    val fitLabel = if (navigator is PagedNavigator) R.string.reader_fit_page else R.string.reader_fit_width
    FloatingPageBar(
        page = page,
        pageSizes = state.pageSizes,
        renderer = state.renderer,
        scrubber = scrubber,
        onGoTo = { target ->
            scope.launch {
                try {
                    navigator.goTo(target)
                } finally {
                    scrubber.onArrived()
                }
            }
        },
        onIndicatorClick = onIndicatorClick,
        fitLabel = stringResource(fitLabel),
        onFit = onFit,
        previewMaxHeight = previewMaxHeight,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
}

/**
 * Dims the document while the slider previews another page (mockup E2-Lecteur-Curseur): the
 * document stays on its page until the slider is released. It lets every touch through.
 */
@Composable
private fun BoxScope.PreviewScrim() {
    Box(
        Modifier
            .matchParentSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = PREVIEW_SCRIM_ALPHA)),
    )
}

/** Discreet page number shown in full screen (mockup E2-Lecteur-Nuit). */
@Composable
private fun FullscreenPageIndicator(page: Int, pageCount: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.reader_fullscreen_page, page + 1, pageCount),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.safeDrawingPadding().padding(FullscreenIndicatorPadding),
    )
}
