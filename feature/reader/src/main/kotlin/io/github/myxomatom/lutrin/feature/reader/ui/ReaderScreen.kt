// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.ReaderError
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.ReaderViewModel
import io.github.myxomatom.lutrin.feature.reader.render.currentPage
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Reader destination, connected to its [ReaderViewModel]. */
@Composable
fun ReaderRoute(onBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReaderScreen(state = state, onBack = onBack)
}

/**
 * Reader screen (E2): loading, error (V8) or the document with its controls.
 *
 * @param state what to show.
 * @param onBack leaves the reader.
 */
@Composable
fun ReaderScreen(state: ReaderUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        ReaderUiState.Loading -> LoadingContent(modifier)
        is ReaderUiState.Error -> ReaderErrorContent(state.reason, onBack, modifier)
        is ReaderUiState.Ready -> ReaderContent(state, onBack, modifier)
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
private fun ReaderContent(state: ReaderUiState.Ready, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val pageCount = state.pageSizes.size
    val page by remember(listState, pageCount) { derivedStateOf { listState.currentPageIndex(pageCount) } }
    ImmersiveMode(enabled = !controlsVisible)
    Scaffold(
        modifier = modifier,
        topBar = { if (controlsVisible) ReaderTopBar(state.title, onBack) },
        bottomBar = {
            if (controlsVisible) PageBar(page, pageCount, listState, onFitWidth = { zoom = 1f })
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            PdfPagesView(
                pageSizes = state.pageSizes,
                renderer = state.renderer,
                listState = listState,
                zoom = zoom,
                onZoomChange = { zoom = it },
                onTap = { controlsVisible = !controlsVisible },
                contentDescription = stringResource(R.string.reader_document, page + 1, pageCount),
            )
            if (!controlsVisible) FullscreenPageIndicator(page, pageCount, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
            }
        },
    )
}

/** Bottom bar: "page / count", a slider to jump through the document (F05), "fit to width" (F04). */
@Composable
private fun PageBar(page: Int, pageCount: Int, listState: LazyListState, onFitWidth: () -> Unit) {
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
                    onValueChange = { scope.launch { listState.scrollToItem(it.roundToInt()) } },
                    valueRange = 0f..(pageCount - 1).toFloat(),
                    modifier = Modifier.weight(1f).semantics { contentDescription = sliderLabel },
                )
            } else {
                Box(Modifier.weight(1f))
            }
            IconButton(onClick = onFitWidth) {
                Icon(
                    painterResource(R.drawable.ic_fit_width),
                    contentDescription = stringResource(R.string.reader_fit_width),
                )
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

private fun LazyListState.currentPageIndex(pageCount: Int): Int {
    val first = layoutInfo.visibleItemsInfo.firstOrNull() ?: return firstVisibleItemIndex
    return currentPage(
        first.index,
        firstVisibleItemScrollOffset,
        first.size + layoutInfo.mainAxisItemSpacing,
        pageCount,
    )
}

/** Error screen (V8): what went wrong and a way back. */
@Composable
internal fun ReaderErrorContent(reason: ReaderError, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier, topBar = { ReaderTopBar(title = "", onBack = onBack) }) { padding ->
        ErrorMessage(reason, onBack, Modifier.padding(padding))
    }
}
