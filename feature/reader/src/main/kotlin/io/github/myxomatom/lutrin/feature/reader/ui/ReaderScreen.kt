// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.myxomatom.lutrin.core.domain.pdf.RenderOptions
import io.github.myxomatom.lutrin.core.domain.settings.DocumentColorMode
import io.github.myxomatom.lutrin.core.domain.settings.FitMode
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.ui.theme.LocalDarkTheme
import io.github.myxomatom.lutrin.core.ui.theme.LutrinReaderColors
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransition
import io.github.myxomatom.lutrin.core.ui.transition.rememberDocumentTransition
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.ReaderViewModel
import io.github.myxomatom.lutrin.feature.reader.print.startPrinting
import io.github.myxomatom.lutrin.feature.reader.share.ShareOutcome
import io.github.myxomatom.lutrin.feature.reader.share.startSharing
import kotlinx.coroutines.launch

/** Reader destination, connected to its [ReaderViewModel]. */
@Suppress("LongMethod") // Plain wiring of the ViewModel to the screen.
@Composable
fun ReaderRoute(onBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val externalLink by viewModel.linkController.externalLink.collectAsStateWithLifecycle()
    val searchState by viewModel.searchController.state.collectAsStateWithLifecycle()
    val selection by viewModel.selectionController.selection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ReaderScreen(
        state = state,
        onBack = onBack,
        onPageChange = viewModel::onPageChange,
        onScrollModeChange = viewModel::onScrollModeChange,
        onDocumentColorModeChange = viewModel::onDocumentColorModeChange,
        onFitModeChange = viewModel::onFitModeChange,
        onLockOrientationChange = viewModel::onLockOrientationChange,
        onRotate = viewModel::onRotate,
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
        search = readerSearch(viewModel.searchController, searchState),
        selection =
            readerSelection(
                viewModel.selectionController,
                selection,
                canCopy = (state as? ReaderUiState.Ready)?.canCopyText == true,
            ),
        onPrint = { viewModel.printJob()?.let { startPrinting(context, it) } ?: true },
        onShare = { viewModel.shareRequest()?.let { startSharing(context, it) } ?: ShareOutcome.NOT_SHAREABLE },
        properties = rememberReaderProperties(viewModel),
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
 * @param onDocumentColorModeChange called with the colours of the document chosen in the options
 *   (V6, F13, ADR 0028).
 * @param onFitModeChange called with what the pages are fitted to, chosen in the options (V6, F04,
 *   ADR 0029).
 * @param onLockOrientationChange called with the state of the orientation switch of the options (V6,
 *   F17, ADR 0029).
 * @param onRotate called by « Tourner » of the top bar or of its menu (F27, ADR 0032): turns the
 *   pages a quarter clockwise.
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
 * @param search the search in the document (F07, V1): while it is open, its bar replaces the top
 *   bar, the excerpt of the current result replaces the floating bar, and back closes it.
 * @param selection the text selection (F08, ADR 0024): long press, handles, « Copier » and
 *   « Tout sélectionner ».
 * @param onPrint called by the print button of the floating bar (F15, ADR 0026) when the document
 *   may be printed ([ReaderUiState.Ready.canPrint]); opens the print dialog of the system and
 *   returns `false` when the device cannot print.
 * @param onShare called by « Partager » of the menu and of the properties sheet (F15, ADR 0027);
 *   opens the chooser of the system for the document as it is, and tells whether it did.
 * @param properties the properties of the document (F16, V5): « Propriétés » of the menu opens a
 *   sheet over the reader, with « Partager » and « Imprimer ».
 */
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPageChange: (Int) -> Unit = {},
    onScrollModeChange: (ScrollMode) -> Unit = {},
    onDocumentColorModeChange: (DocumentColorMode) -> Unit = {},
    onFitModeChange: (FitMode) -> Unit = {},
    onLockOrientationChange: (Boolean) -> Unit = {},
    onRotate: () -> Unit = {},
    onRemoveFromRecents: () -> Unit = {},
    onPasswordSubmit: (CharSequence) -> Unit = {},
    documentId: String? = null,
    links: ReaderLinks = ReaderLinks(),
    onOutlineOpen: () -> Unit = {},
    onOutlineToggle: (Int) -> Unit = {},
    onExternalOutlineEntry: (String) -> Unit = {},
    search: ReaderSearch = ReaderSearch(),
    selection: ReaderSelection = ReaderSelection(),
    onPrint: () -> Boolean = { true },
    onShare: () -> ShareOutcome = { ShareOutcome.STARTED },
    properties: ReaderProperties = ReaderProperties(),
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
                    display =
                        ReaderDisplayActions(
                            onScrollModeChange = onScrollModeChange,
                            onFitModeChange = onFitModeChange,
                            onColorModeChange = onDocumentColorModeChange,
                            onLockOrientationChange = onLockOrientationChange,
                            onRotate = onRotate,
                        ),
                    links = links,
                    pageTransition = pageTransition,
                    onOutlineOpen = onOutlineOpen,
                    onOutlineToggle = onOutlineToggle,
                    onExternalOutlineEntry = onExternalOutlineEntry,
                    search = search,
                    selection = selection,
                    onPrint = onPrint,
                    onShare = onShare,
                    properties = properties,
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

@Suppress("LongParameterList", "LongMethod") // Plain wiring of the state and callbacks of ReaderScreen.
@Composable
private fun ReaderContent(
    state: ReaderUiState.Ready,
    onBack: () -> Unit,
    onPageChange: (Int) -> Unit,
    display: ReaderDisplayActions,
    links: ReaderLinks,
    modifier: Modifier = Modifier,
    pageTransition: DocumentTransition? = null,
    onOutlineOpen: () -> Unit = {},
    onOutlineToggle: (Int) -> Unit = {},
    onExternalOutlineEntry: (String) -> Unit = {},
    search: ReaderSearch = ReaderSearch(),
    selection: ReaderSelection = ReaderSelection(),
    onPrint: () -> Boolean = { true },
    onShare: () -> ShareOutcome = { ShareOutcome.STARTED },
    properties: ReaderProperties = ReaderProperties(),
) {
    // Zoom 1 is what the pages are fitted to (the width or the whole page, ADR 0029): start again from it
    // whenever the layout, the fit or the rotation of the pages (F27) changes.
    var zoom by rememberSaveable(state.scrollMode, state.fitMode, state.rotation) { mutableFloatStateOf(1f) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var optionsVisible by rememberSaveable { mutableStateOf(false) }
    var goToPageVisible by rememberSaveable { mutableStateOf(false) }
    // Shared with the document area: the messages of the menu and of the sheets appear in the same place.
    val snackbar = remember { SnackbarHostState() }
    val onShareClick = rememberShareClick(onShare, snackbar)
    // A new navigator per mode, starting on the page the previous one last reported.
    val navigator = rememberPageNavigator(state.scrollMode, state.currentPage, rememberPageFrames(state))
    KeepPageAfterRotation(state.rotation, state.currentPage, navigator)
    val scrubber = remember(navigator, state.pagePreview) { PageScrubber(state.pagePreview) }
    ReportCurrentPage(navigator, onPageChange)
    ImmersiveMode(enabled = !controlsVisible)
    OrientationLock(state.lockOrientation)
    KeepScreenOn(state.keepScreenOn)
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
            // The search bar stays while the search is open: a tap on the pages keeps the controls.
            controlsVisible = controlsVisible || search.state.isOpen,
            onToggleControls = { if (!search.state.isOpen) controlsVisible = !controlsVisible },
            actions = ReaderBarActions(
                onBack = onBack,
                menu =
                    ReaderMenu(
                        onDisplay = { optionsVisible = true },
                        onShare = onShareClick,
                        onProperties = properties.onOpen,
                        rotation = MenuRotation(state.rotation, display.onRotate),
                    ),
                onOutline = openOutline,
                onIndicatorClick = { goToPageVisible = true },
                onPrint = onPrint,
            ),
            pageTransition = pageTransition,
            links = links,
            search = search,
            selection = selection,
            snackbar = snackbar,
        )
    }
    PropertiesSheet(properties, state.canPrint, onShareClick, rememberPrintClick(state.canPrint, onPrint, snackbar))
    GoToPage(goToPageVisible, navigator, state.pageSizes.size, onDismiss = { goToPageVisible = false })
    if (optionsVisible) {
        ReaderOptionsSheet(
            display =
                ReaderDisplay(
                    scrollMode = state.scrollMode,
                    fitMode = state.fitMode,
                    colorMode = state.renderOptions(LocalDarkTheme.current).colorMode,
                    fullscreen = !controlsVisible,
                    lockOrientation = state.lockOrientation,
                ),
            // Full screen is the state of the bars, which a tap on the page toggles too (F14).
            actions = ReaderDisplayActions(
                display.onScrollModeChange,
                display.onFitModeChange,
                display.onColorModeChange,
                onFullscreenChange = { controlsVisible = !it },
                onLockOrientationChange = display.onLockOrientationChange,
            ),
            onDismiss = { optionsVisible = false },
        )
    }
}

/**
 * What the bars of the reader do: back, the buttons and the menu of the top bar, the page indicator and the
 * print button of the floating bar.
 */
private class ReaderBarActions(
    val onBack: () -> Unit,
    val menu: ReaderMenu,
    val onOutline: (() -> Unit)?,
    val onIndicatorClick: () -> Unit,
    val onPrint: () -> Boolean,
)

/**
 * The top bar over the reading area, which the table of contents drawer covers, or the search
 * bar (V1) while the search is open.
 */
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
    search: ReaderSearch,
    selection: ReaderSelection,
    snackbar: SnackbarHostState,
) {
    SearchHandling(search, navigator)
    Scaffold(
        topBar = {
            if (search.state.isOpen) {
                SearchBar(search.state, search)
            } else if (controlsVisible) {
                ReaderTopBar(
                    title = state.title,
                    onBack = actions.onBack,
                    menu = actions.menu,
                    onOutline = actions.onOutline,
                    onSearch = search.onOpen,
                )
            }
        },
        containerColor = readingBackground(state.renderOptions(LocalDarkTheme.current)),
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
            search = search.state,
            selection = selection,
            onPrint = actions.onPrint,
            snackbar = snackbar,
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

/**
 * Background of the reading area around the pages: that of the theme, or the darker background of
 * night mode (design tokens, `reader.backgroundNight`), whatever the theme, so that the dark pages
 * stand out from it (F13, ADR 0028).
 */
@Composable
internal fun readingBackground(options: RenderOptions): Color =
    if (options.colorMode.isDark) LutrinReaderColors.BackgroundNight else MaterialTheme.colorScheme.surfaceContainerHigh
