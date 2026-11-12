// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.ui.theme.titleLargeCompact
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransition
import io.github.myxomatom.lutrin.core.ui.transition.rememberDocumentTransition
import io.github.myxomatom.lutrin.feature.reader.OutlineEntry
import io.github.myxomatom.lutrin.feature.reader.OutlineTarget
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.ReaderViewModel
import kotlinx.coroutines.launch

/** Reader destination, connected to its [ReaderViewModel]. */
@Composable
fun ReaderRoute(onBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val externalLink by viewModel.linkController.externalLink.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ReaderScreen(
        state = state,
        onBack = onBack,
        onPageChange = viewModel::onPageChange,
        onScrollModeChange = viewModel::onScrollModeChange,
        onRemoveFromRecents = {
            viewModel.onRemoveFromRecents()
            onBack()
        },
        onPasswordSubmit = viewModel::onPasswordSubmit,
        documentId = viewModel.documentId,
        links =
            ReaderLinks(
                externalLink = externalLink,
                effects = viewModel.linkController.effects,
                onLinkClick = viewModel.linkController::onLinkClick,
                onExternalLinkConfirm = viewModel.linkController::onExternalLinkConfirm,
                onExternalLinkDismiss = viewModel.linkController::onExternalLinkDismiss,
                onOpenExternalLink = { uri -> openExternalLink(context, uri) },
            ),
        onOutlineOpen = viewModel::onOutlineOpened,
        onOutlineToggle = viewModel::onOutlineToggle,
        onExternalOutlineEntry = viewModel::onExternalOutlineEntry,
    )
}

/**
 * Reader screen (E2): loading, error (V8), password of an encrypted document (V4) or the document
 * with its controls: the top bar, and the floating bar with the page slider (ADR 0018), both
 * hidden in full screen.
 *
 * @param state what to show.
 * @param onBack leaves the reader.
 * @param modifier modifier applied to the screen.
 * @param onPageChange called with the zero-based page shown, whenever it changes.
 * @param onScrollModeChange called with the scroll mode chosen in the options (V6).
 * @param onRemoveFromRecents called when the user removes, from the error screen (V8), a document
 *   of the recent list that cannot be opened; the reader is expected to close.
 * @param onPasswordSubmit called with the password typed in the password dialog (V4); « Annuler »
 *   calls [onBack].
 * @param documentId identifier of the document in the recent list: once the app links it, the
 *   page shown shrinks back into its thumbnail in the library (ADR 0019); without it, or outside
 *   of the app's navigation, the reader has no shared element and only fades in and out.
 * @param links the links of the document (F09) and the confirmation of external ones (V7).
 * @param onOutlineOpen called when the table of contents (V2) is about to open, so that the
 *   current section is expanded in it.
 * @param onOutlineToggle called with the id of an outline entry whose sub-sections are shown or
 *   hidden.
 * @param onExternalOutlineEntry called with the address of an outline entry that leads outside
 *   the document; the drawer stays open and the confirmation of external links (V7) takes over.
 */
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPageChange: (Int) -> Unit = {},
    onScrollModeChange: (ScrollMode) -> Unit = {},
    onRemoveFromRecents: () -> Unit = {},
    onPasswordSubmit: (CharSequence) -> Unit = {},
    documentId: String? = null,
    links: ReaderLinks = ReaderLinks(),
    onOutlineOpen: () -> Unit = {},
    onOutlineToggle: (Int) -> Unit = {},
    onExternalOutlineEntry: (String) -> Unit = {},
) {
    // Linked by the app only once the reader is settled: the page shrinks back into its thumbnail,
    // while coming in is a plain fade (ADR 0019).
    val pageTransition = rememberDocumentTransition(documentId)
    Box(modifier.fillMaxSize()) {
        when (state) {
            ReaderUiState.Loading -> LoadingContent()

            is ReaderUiState.Error -> ReaderErrorContent(state, onBack, onRemoveFromRecents)

            is ReaderUiState.PasswordRequired -> ReaderPasswordContent(state, onBack, onPasswordSubmit)

            is ReaderUiState.Ready ->
                ReaderContent(
                    state = state,
                    onBack = onBack,
                    onPageChange = onPageChange,
                    onScrollModeChange = onScrollModeChange,
                    links = links,
                    pageTransition = pageTransition,
                    onOutlineOpen = onOutlineOpen,
                    onOutlineToggle = onOutlineToggle,
                    onExternalOutlineEntry = onExternalOutlineEntry,
                )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.reader_loading)
    Box(modifier.fillMaxSize().semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Suppress("LongParameterList") // Plain wiring of the state and callbacks of ReaderScreen.
@Composable
private fun ReaderContent(
    state: ReaderUiState.Ready,
    onBack: () -> Unit,
    onPageChange: (Int) -> Unit,
    onScrollModeChange: (ScrollMode) -> Unit,
    links: ReaderLinks,
    modifier: Modifier = Modifier,
    pageTransition: DocumentTransition? = null,
    onOutlineOpen: () -> Unit = {},
    onOutlineToggle: (Int) -> Unit = {},
    onExternalOutlineEntry: (String) -> Unit = {},
) {
    // Zoom 1 is "fit width" when scrolling and "whole page" page by page: start again from it.
    var zoom by rememberSaveable(state.scrollMode) { mutableFloatStateOf(1f) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var optionsVisible by rememberSaveable { mutableStateOf(false) }
    var goToPageVisible by rememberSaveable { mutableStateOf(false) }
    // A new navigator per mode, starting on the page the previous one last reported.
    val navigator = rememberPageNavigator(state.scrollMode, state.currentPage, state.pageSizes)
    val scrubber = remember(navigator, state.pagePreview) { PageScrubber(state.pagePreview) }
    ReportCurrentPage(navigator, onPageChange)
    ImmersiveMode(enabled = !controlsVisible)
    OutlineHost(
        state = state,
        navigator = navigator,
        onOpen = onOutlineOpen,
        onToggle = onOutlineToggle,
        onExternalEntry = onExternalOutlineEntry,
        modifier = modifier,
    ) { openOutline ->
        ReaderScaffold(
            state = state,
            navigator = navigator,
            scrubber = scrubber,
            zoom = zoom,
            onZoomChange = { zoom = it },
            controlsVisible = controlsVisible,
            onToggleControls = { controlsVisible = !controlsVisible },
            actions = ReaderBarActions(
                onBack = onBack,
                onMoreOptions = { optionsVisible = true },
                onOutline = openOutline,
                onIndicatorClick = { goToPageVisible = true },
            ),
            pageTransition = pageTransition,
            links = links,
        )
    }
    GoToPage(goToPageVisible, navigator, state.pageSizes.size, onDismiss = { goToPageVisible = false })
    OptionsSheet(optionsVisible, state.scrollMode, onScrollModeChange, onDismiss = { optionsVisible = false })
}

/** The display options (V6) over the reader, when [visible]. */
@Composable
private fun OptionsSheet(
    visible: Boolean,
    scrollMode: ScrollMode,
    onScrollModeChange: (ScrollMode) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    ReaderOptionsSheet(scrollMode = scrollMode, onScrollModeChange = onScrollModeChange, onDismiss = onDismiss)
}

/** What the bars of the reader do: back, the two buttons of the top bar and the page indicator. */
private class ReaderBarActions(
    val onBack: () -> Unit,
    val onMoreOptions: () -> Unit,
    val onOutline: (() -> Unit)?,
    val onIndicatorClick: () -> Unit,
)

/** The top bar over the reading area, which the table of contents drawer covers. */
@Suppress("LongParameterList") // Plain wiring of the state hoisted in ReaderContent.
@Composable
private fun ReaderScaffold(
    state: ReaderUiState.Ready,
    navigator: PageNavigator,
    scrubber: PageScrubber,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    actions: ReaderBarActions,
    pageTransition: DocumentTransition?,
    links: ReaderLinks,
) {
    Scaffold(
        topBar = {
            if (controlsVisible) {
                ReaderTopBar(
                    title = state.title,
                    onBack = actions.onBack,
                    onMoreOptions = actions.onMoreOptions,
                    onOutline = actions.onOutline,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        // The pages run down to the bottom of the screen, under the floating bar and the
        // navigation bar: the bar keeps clear of the bottom insets itself (ADR 0018).
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(TopAndSides),
    ) { padding ->
        ReaderDocumentArea(
            state = state,
            navigator = navigator,
            scrubber = scrubber,
            zoom = zoom,
            onZoomChange = onZoomChange,
            controlsVisible = controlsVisible,
            onTap = onToggleControls,
            links = links,
            onIndicatorClick = actions.onIndicatorClick,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            pageTransition = pageTransition,
        )
    }
}

/**
 * « Aller à la page » (V3) over the reader, when [visible]: shows the page asked for, then closes. The jump runs
 * in a scope that outlives the dialog.
 */
@Composable
private fun GoToPage(visible: Boolean, navigator: PageNavigator, pageCount: Int, onDismiss: () -> Unit) {
    // Outside the condition: the jump goes on after the dialog has closed.
    val scope = rememberCoroutineScope()
    if (!visible) return
    GoToPageDialog(
        currentPage = navigator.currentPage,
        pageCount = pageCount,
        onGoTo = { page ->
            onDismiss()
            scope.launch { navigator.goTo(page) }
        },
        onDismiss = onDismiss,
    )
}

/** The insets the reading area keeps clear of; the bottom ones are left to the floating bar. */
private val TopAndSides = WindowInsetsSides.Top + WindowInsetsSides.Horizontal

/** Tells [onPageChange] about every page the [navigator] reaches, so that a change of mode keeps it. */
@Composable
private fun ReportCurrentPage(navigator: PageNavigator, onPageChange: (Int) -> Unit) {
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    LaunchedEffect(navigator) {
        snapshotFlow { navigator.currentPage }.collect { currentOnPageChange(it) }
    }
}

/**
 * Top bar of the reader: back, title and, once a document is open, the table of contents (V2)
 * and « More options » (V6).
 *
 * @param onMoreOptions opens the display options; `null` hides the button (error screen).
 * @param onOutline opens the table of contents; `null` hides the button (error screen, and a
 *   document without an outline: a button to an empty panel would be a dead end, ADR 0021).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    onMoreOptions: (() -> Unit)? = null,
    onOutline: (() -> Unit)? = null,
) {
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLargeCompact,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
            }
        },
        actions = {
            if (onOutline != null) {
                IconButton(onClick = onOutline) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.reader_outline_title),
                    )
                }
            }
            if (onMoreOptions != null) {
                IconButton(onClick = onMoreOptions) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.reader_more_options))
                }
            }
        },
    )
}

/** Error screen (V8): what went wrong, « Retirer des récents » for a recent document, a way back. */
@Composable
internal fun ReaderErrorContent(
    state: ReaderUiState.Error,
    onBack: () -> Unit,
    onRemoveFromRecents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, topBar = { ReaderTopBar(title = "", onBack = onBack) }) { padding ->
        ErrorMessage(
            reason = state.reason,
            onBack = onBack,
            modifier = Modifier.padding(padding),
            onRemoveFromRecents = onRemoveFromRecents.takeIf { state.inRecents },
        )
    }
}
