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
import io.github.myxomatom.lutrin.feature.reader.search.SearchState
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
 * @param search the search in the document (F07): its occurrences are highlighted on the pages
 *   and, while it is open, the excerpt of the current one replaces the floating bar (V1).
 * @param selection the text selection (F08, ADR 0024): a long press on a word selects it, a tap
 *   closes it; TalkBack copies the text of the page with an action of the document area.
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
    search: SearchState = SearchState(),
    selection: ReaderSelection = ReaderSelection(),
) {
    val page = navigator.currentPage
    val onPageTap = rememberSelectionTap(selection, rememberPageTap(state.links, navigator, onTap, links.onLinkClick))
    val onPageLongPress = rememberPageLongPress(selection, onPageTap)
    val pageActions = rememberLinkActions(state.links, page, links.onLinkClick) + copyPageAction(selection, page)
    val snackbar = rememberLinkHandling(links, navigator)
    SelectionHandling(selection, navigator, search.isOpen, snackbar)
    val sharedPage = sharedPage(page, zoom, pageTransition)
    BoxWithConstraints(modifier.fillMaxSize()) {
        DocumentPages(
            state = state,
            navigator = navigator,
            zoom = zoom,
            onZoomChange = onZoomChange,
            onTap = onPageTap,
            contentDescription = stringResource(R.string.reader_document, page + 1, state.pageSizes.size),
            bottomClearance = if (controlsVisible) floatingPageBarClearance() else 0.dp,
            sharedPage = sharedPage,
            linkActions = pageActions,
            highlights = rememberSearchHighlights(search),
            onLongPress = onPageLongPress,
            selection = selection,
        )
        if (scrubber.isPreviewing) PreviewScrim()
        if (search.isOpen) {
            SearchResultCard(search, state.outline::sectionTitleAt, Modifier.align(Alignment.BottomCenter))
        } else if (controlsVisible) {
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
            FullscreenPageIndicator(page, state.pageSizes.size, Modifier.align(Alignment.BottomCenter))
        }
        ReaderSnackbarHost(snackbar, controlsVisible, Modifier.align(Alignment.BottomCenter))
    }
}

/** The page shown as the shared element of the thumbnail transition, if any ([sharedPageIndex]). */
@Composable
private fun sharedPage(page: Int, zoom: Float, pageTransition: DocumentTransition?): SharedPage? =
    sharedPageIndex(page, zoom, pageTransition != null)?.let {
        SharedPage(it, Modifier.sharedDocumentPage(pageTransition))
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
    highlights: SearchHighlights,
    onLongPress: (PageHit?) -> Unit,
    selection: ReaderSelection,
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
                highlights = highlights,
                onLongPress = onLongPress,
                selection = selection,
                canCopy = state.canCopyText,
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
                highlights = highlights,
                onLongPress = onLongPress,
                selection = selection,
                canCopy = state.canCopyText,
            )
    }
}

/**
 * A tap on the pages while text is selected only closes the selection (ADR 0024); otherwise it
 * goes to [onTap] (link, or full screen).
 */
@Composable
private fun rememberSelectionTap(selection: ReaderSelection, onTap: (PageHit?) -> Unit): (PageHit?) -> Unit {
    val currentSelection by rememberUpdatedState(selection)
    val currentOnTap by rememberUpdatedState(onTap)
    return remember {
        { hit -> if (currentSelection.selection != null) currentSelection.onClear() else currentOnTap(hit) }
    }
}

/**
 * Handles a long press on the pages: selects the word under the finger (F08). Outside the text,
 * or on a page without text, the press does what a tap does ([onTap]), as before the selection
 * existed (ADR 0024).
 */
@Composable
private fun rememberPageLongPress(selection: ReaderSelection, onTap: (PageHit?) -> Unit): (PageHit?) -> Unit {
    val scope = rememberCoroutineScope()
    val currentSelection by rememberUpdatedState(selection)
    val currentOnTap by rememberUpdatedState(onTap)
    return remember {
        { hit ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val selected = hit != null && currentSelection.onLongPress(hit.pageIndex, hit.point)
                if (!selected) currentOnTap(hit)
            }
        }
    }
}

/**
 * « Copier le texte de la page », the TalkBack action of the document area (F08, F18): the whole
 * text of the page shown, without a long press, under the same copy permission (ADR 0024).
 */
@Composable
private fun copyPageAction(selection: ReaderSelection, page: Int): CustomAccessibilityAction {
    val currentSelection by rememberUpdatedState(selection)
    return CustomAccessibilityAction(stringResource(R.string.reader_selection_copy_page_action)) {
        currentSelection.onCopyPage(page)
        true
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
        sectionTitle = state.outline::sectionTitleAt,
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
