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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransition
import io.github.myxomatom.lutrin.core.ui.transition.sharedDocumentPage
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.links.PageLinks
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Opacity of the reading background over the document during a preview (the mockup shows it at 55 %). */
private const val PREVIEW_SCRIM_ALPHA = 0.45f

/**
 * How far from a link a tap still activates it: a fingertip covers more than a small link
 * (`AGENTS.md` §11 asks 48 dp for targets; links are drawn by the document, at any size).
 */
private val LinkTouchSlop = 8.dp

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
 * @param onTap called on a single tap on the pages, outside of their links.
 * @param links the links of the document (F09): a link activated by a tap or a TalkBack action
 *   goes there with the page shown at that moment; their messages are shown above the bar.
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
    links: ReaderLinks,
    onIndicatorClick: () -> Unit,
    modifier: Modifier = Modifier,
    pageTransition: DocumentTransition? = null,
) {
    val page = navigator.currentPage
    val onPageTap = rememberPageTap(state.links, navigator, onTap, links.onLinkClick)
    val linkActions = rememberLinkActions(state.links, page, links.onLinkClick)
    val snackbar = rememberLinkHandling(links, navigator)
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
            onTap = onPageTap,
            contentDescription = stringResource(R.string.reader_document, page + 1, pageCount),
            bottomClearance = if (controlsVisible) floatingPageBarClearance() else 0.dp,
            sharedPage = sharedPage,
            linkActions = linkActions,
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
        ReaderSnackbarHost(snackbar, controlsVisible, Modifier.align(Alignment.BottomCenter))
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
    onTap: (PageHit?) -> Unit,
    contentDescription: String,
    bottomClearance: Dp,
    sharedPage: SharedPage?,
    linkActions: List<CustomAccessibilityAction>,
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
                linkActions = linkActions,
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
                linkActions = linkActions,
            )
    }
}

/**
 * Handles a tap on the pages: the link under the finger, within [LinkTouchSlop], is activated
 * (F09); anywhere else, [onTap] switches to full screen as before (F14). The links of the pages
 * on screen are normally cached already, so the answer comes at once.
 */
@Composable
private fun rememberPageTap(
    links: PageLinks,
    navigator: PageNavigator,
    onTap: () -> Unit,
    onLinkClick: (PdfLink, Int) -> Unit,
): (PageHit?) -> Unit {
    val scope = rememberCoroutineScope()
    val slop = with(LocalDensity.current) { LinkTouchSlop.toPx() }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    return remember(links, navigator, slop) {
        { hit ->
            // Undispatched: with the links in the cache, the tap is answered in the same frame.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val link = hit?.let { linkAt(links.linksOf(it.pageIndex), it.point, slop * it.pointsPerPixel) }
                if (link != null) currentOnLinkClick(link, navigator.currentPage) else currentOnTap()
            }
        }
    }
}

/**
 * TalkBack actions of the document area, one per link of the current [page] (« Lien : page 3 »,
 * « Lien : https://… »), so that links are reachable without seeing them (F09, F18).
 */
@Composable
private fun rememberLinkActions(
    links: PageLinks,
    page: Int,
    onLinkClick: (PdfLink, Int) -> Unit,
): List<CustomAccessibilityAction> {
    val pageLinks by produceState(links.cached(page).orEmpty(), links, page) { value = links.linksOf(page) }
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    return pageLinks.map { link ->
        val label =
            when (link) {
                is PdfLink.Internal -> stringResource(R.string.reader_link_action_page, link.destination.pageIndex + 1)
                is PdfLink.External -> stringResource(R.string.reader_link_action_external, link.uri)
            }
        CustomAccessibilityAction(label) {
            currentOnLinkClick(link, page)
            true
        }
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
