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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.ReaderViewModel
import kotlinx.coroutines.launch

/** Reader destination, connected to its [ReaderViewModel]. */
@Composable
fun ReaderRoute(onBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReaderScreen(
        state = state,
        onBack = onBack,
        onPageChange = viewModel::onPageChange,
        onScrollModeChange = viewModel::onScrollModeChange,
        onPagePreviewChange = viewModel::onPagePreviewChange,
        onRemoveFromRecents = {
            viewModel.onRemoveFromRecents()
            onBack()
        },
        onPasswordSubmit = viewModel::onPasswordSubmit,
        documentId = viewModel.documentId,
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
 * @param onPagePreviewChange called with the page preview choice made in the options (V6).
 * @param onRemoveFromRecents called when the user removes, from the error screen (V8), a document
 *   of the recent list that cannot be opened; the reader is expected to close.
 * @param onPasswordSubmit called with the password typed in the password dialog (V4); « Annuler »
 *   calls [onBack].
 * @param documentId identifier of the document in the recent list, linking its thumbnail in the
 *   library to the page of the reader (ADR 0019); without it, or outside of the app's navigation,
 *   the reader has no shared element and only fades in and out.
 */
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPageChange: (Int) -> Unit = {},
    onScrollModeChange: (ScrollMode) -> Unit = {},
    onPagePreviewChange: (Boolean) -> Unit = {},
    onRemoveFromRecents: () -> Unit = {},
    onPasswordSubmit: (CharSequence) -> Unit = {},
    documentId: String? = null,
) {
    // The opening page and the reader's page each hold their own shared element; never both at once.
    val openingTransition = rememberDocumentTransition(documentId)
    val openingPhase = rememberOpeningPagePhase(openingTransition, documentLoading = state == ReaderUiState.Loading)
    val pageTransition = rememberDocumentTransition(documentId.takeIf { openingPhase == OpeningPagePhase.Gone })
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
                    onPagePreviewChange = onPagePreviewChange,
                    pageTransition = pageTransition,
                )
        }
        OpeningPage(openingTransition, openingPhase)
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
    onPagePreviewChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pageTransition: DocumentTransition? = null,
) {
    // Zoom 1 is "fit width" when scrolling and "whole page" page by page: start again from it.
    var zoom by rememberSaveable(state.scrollMode) { mutableFloatStateOf(1f) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var optionsVisible by rememberSaveable { mutableStateOf(false) }
    // A new navigator per mode, starting on the page the previous one last reported.
    val navigator = rememberPageNavigator(state.scrollMode, state.currentPage, state.pageSizes.size)
    val scrubber = remember(navigator, state.pagePreview) { PageScrubber(state.pagePreview) }
    ReportCurrentPage(navigator, onPageChange)
    ImmersiveMode(enabled = !controlsVisible)
    Scaffold(
        modifier = modifier,
        topBar = {
            if (controlsVisible) ReaderTopBar(state.title, onBack, onMoreOptions = { optionsVisible = true })
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
            onZoomChange = { zoom = it },
            controlsVisible = controlsVisible,
            onTap = { controlsVisible = !controlsVisible },
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            pageTransition = pageTransition,
        )
    }
    if (optionsVisible) {
        ReaderOptionsSheet(
            scrollMode = state.scrollMode,
            onScrollModeChange = onScrollModeChange,
            pagePreview = state.pagePreview,
            onPagePreviewChange = onPagePreviewChange,
            onDismiss = { optionsVisible = false },
        )
    }
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
 * Top bar of the reader: back, title and, once a document is open, « More options » (V6).
 *
 * @param onMoreOptions opens the display options; `null` hides the button (error screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(title: String, onBack: () -> Unit, onMoreOptions: (() -> Unit)? = null) {
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
