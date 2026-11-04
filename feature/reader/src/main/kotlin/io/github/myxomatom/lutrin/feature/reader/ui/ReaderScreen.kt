// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.ReaderError
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.ReaderViewModel
import kotlin.math.roundToInt
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
    )
}

/**
 * Reader screen (E2): loading, error (V8) or the document with its controls.
 *
 * @param state what to show.
 * @param onBack leaves the reader.
 * @param modifier modifier applied to the screen.
 * @param onPageChange called with the zero-based page shown, whenever it changes.
 * @param onScrollModeChange called with the scroll mode chosen in the options (V6).
 */
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPageChange: (Int) -> Unit = {},
    onScrollModeChange: (ScrollMode) -> Unit = {},
) {
    when (state) {
        ReaderUiState.Loading -> LoadingContent(modifier)
        is ReaderUiState.Error -> ReaderErrorContent(state.reason, onBack, modifier)
        is ReaderUiState.Ready -> ReaderContent(state, onBack, onPageChange, onScrollModeChange, modifier)
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.reader_loading)
    Box(modifier.fillMaxSize().semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState.Ready,
    onBack: () -> Unit,
    onPageChange: (Int) -> Unit,
    onScrollModeChange: (ScrollMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Zoom 1 is "fit width" when scrolling and "whole page" page by page: start again from it.
    var zoom by rememberSaveable(state.scrollMode) { mutableFloatStateOf(1f) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var optionsVisible by rememberSaveable { mutableStateOf(false) }
    val pageCount = state.pageSizes.size
    // A new navigator per mode, starting on the page the previous one last reported.
    val navigator = rememberPageNavigator(state.scrollMode, state.currentPage, pageCount)
    val page = navigator.currentPage
    ReportCurrentPage(navigator, onPageChange)
    ImmersiveMode(enabled = !controlsVisible)
    Scaffold(
        modifier = modifier,
        topBar = {
            if (controlsVisible) ReaderTopBar(state.title, onBack, onMoreOptions = { optionsVisible = true })
        },
        bottomBar = {
            if (controlsVisible) PageBar(page, pageCount, navigator, onFit = { zoom = 1f })
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            DocumentPages(
                state = state,
                navigator = navigator,
                zoom = zoom,
                onZoomChange = { zoom = it },
                onTap = { controlsVisible = !controlsVisible },
                contentDescription = stringResource(R.string.reader_document, page + 1, pageCount),
            )
            if (!controlsVisible) FullscreenPageIndicator(page, pageCount, Modifier.align(Alignment.BottomCenter))
        }
    }
    if (optionsVisible) {
        ReaderOptionsSheet(state.scrollMode, onScrollModeChange, onDismiss = { optionsVisible = false })
    }
}

/** Tells [onPageChange] about every page the [navigator] reaches, so that a change of mode keeps it. */
@Composable
private fun ReportCurrentPage(navigator: PageNavigator, onPageChange: (Int) -> Unit) {
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    LaunchedEffect(navigator) {
        snapshotFlow { navigator.currentPage }.collect { currentOnPageChange(it) }
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
            )
    }
}

/**
 * Top bar of the reader: back, title and, once a document is open, « More options » (V6).
 *
 * @param onMoreOptions opens the display options; `null` hides the button (error screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(title: String, onBack: () -> Unit, onMoreOptions: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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

/**
 * Bottom bar: "page / count", a slider to jump through the document (F05), and a button
 * bringing the zoom back to 1 (F04): "fit to width" when scrolling, "whole page" page by page.
 */
@Composable
private fun PageBar(page: Int, pageCount: Int, navigator: PageNavigator, onFit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sliderLabel = stringResource(R.string.reader_page_slider)
    BottomAppBar {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.reader_page_indicator, page + 1, pageCount),
                style = MaterialTheme.typography.titleMedium,
            )
            if (pageCount > 1) {
                Slider(
                    value = page.toFloat(),
                    onValueChange = { scope.launch { navigator.goTo(it.roundToInt()) } },
                    valueRange = 0f..(pageCount - 1).toFloat(),
                    modifier = Modifier.weight(1f).semantics { contentDescription = sliderLabel },
                )
            } else {
                Box(Modifier.weight(1f))
            }
            IconButton(onClick = onFit) {
                val label = if (navigator is PagedNavigator) R.string.reader_fit_page else R.string.reader_fit_width
                Icon(painterResource(R.drawable.ic_fit_width), contentDescription = stringResource(label))
            }
        }
    }
}

/** Discreet page number shown in full screen (mockup E2-Lecteur-Nuit). */
@Composable
private fun FullscreenPageIndicator(page: Int, pageCount: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.reader_fullscreen_page, page + 1, pageCount),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.safeDrawingPadding().padding(16.dp),
    )
}

/** Error screen (V8): what went wrong and a way back. */
@Composable
internal fun ReaderErrorContent(reason: ReaderError, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier, topBar = { ReaderTopBar(title = "", onBack = onBack) }) { padding ->
        ErrorMessage(reason, onBack, Modifier.padding(padding))
    }
}
