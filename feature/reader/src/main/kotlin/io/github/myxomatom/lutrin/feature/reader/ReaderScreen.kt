// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.myxomatom.lutrin.core.ui.component.ErrorIllustration
import io.github.myxomatom.lutrin.core.ui.component.StatusMessage
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens

/**
 * Reader screen (E2, `docs/design/png/E2-Lecteur.png`) and its loading and error states (V8).
 * Stateless: the document comes from [state], the pages from [loadPage].
 *
 * @param state what to show.
 * @param onBack called when the user leaves the reader.
 * @param loadPage renders a page at a width in pixels; `null` leaves the page blank.
 * @param modifier modifier applied to the screen.
 */
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    loadPage: suspend (pageIndex: Int, widthPx: Int) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is ReaderUiState.Content -> DocumentReader(state, onBack, loadPage, modifier)

        is ReaderUiState.Loading -> StatusScaffold(state.title, onBack, modifier) { LoadingIndicator() }

        is ReaderUiState.Error -> StatusScaffold(state.title, onBack, modifier) {
            ReaderErrorMessage(state.reason, onBack)
        }
    }
}

@Composable
private fun DocumentReader(
    state: ReaderUiState.Content,
    onBack: () -> Unit,
    loadPage: suspend (pageIndex: Int, widthPx: Int) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var barsVisible by rememberSaveable { mutableStateOf(true) }
    var zoom by rememberSaveable { mutableFloatStateOf(ZoomLevels.FIT_WIDTH) }
    ImmersiveMode(enabled = !barsVisible)
    Box(modifier.fillMaxSize()) {
        DocumentPages(
            pageSizes = state.pageSizes,
            listState = listState,
            zoom = zoom,
            onZoomChange = { zoom = it },
            onTap = { barsVisible = !barsVisible },
            loadPage = loadPage,
        )
        AnimatedVisibility(visible = barsVisible, enter = fadeIn(), exit = fadeOut()) {
            ReaderTopBar(state.title, onBack)
        }
        AnimatedVisibility(
            visible = barsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PageBar(listState = listState, pageCount = state.pageCount, onFitWidth = { zoom = ZoomLevels.FIT_WIDTH })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun StatusScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(modifier = modifier.fillMaxSize(), topBar = { ReaderTopBar(title, onBack) }) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.reader_loading)
    CircularProgressIndicator(modifier.semantics { contentDescription = description })
}

@Composable
private fun ReaderErrorMessage(reason: ReaderError, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val (title, message) = when (reason) {
        ReaderError.UNREADABLE -> R.string.reader_error_unreadable_title to R.string.reader_error_unreadable_message

        ReaderError.NOT_ACCESSIBLE ->
            R.string.reader_error_not_accessible_title to
                R.string.reader_error_not_accessible_message

        ReaderError.PASSWORD_PROTECTED ->
            R.string.reader_error_protected_title to
                R.string.reader_error_protected_message
    }
    Column(modifier.fillMaxSize()) {
        StatusMessage(
            title = stringResource(title),
            message = stringResource(message),
            modifier = Modifier.fillMaxSize(),
            illustration = { ErrorIllustration() },
            actions = {
                OutlinedButton(
                    onClick = onBack,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = LutrinDimens.ButtonHeight),
                ) { Text(stringResource(R.string.reader_back_to_library)) }
            },
        )
    }
}

/** Current page: the one crossing the middle of the viewport. */
internal fun LazyListState.centerPageIndex(): Int {
    val info = layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.firstOrNull { it.offset <= center && it.offset + it.size >= center }?.index
        ?: firstVisibleItemIndex
}
