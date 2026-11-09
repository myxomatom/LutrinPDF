// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.R as UiR
import io.github.myxomatom.lutrin.core.ui.component.StatusMessage
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import java.time.LocalTime

/** Bottom padding of the offline notice (E1 mockup). */
private val OfflineNoticeBottom = 32.dp

/** Gap between the shield and the text of the offline notice. */
private val OfflineNoticeGap = 8.dp

/** Gap between the icon and the label of the floating button (Material 3). */
private val FabIconSpacing = 12.dp

/**
 * Library screen (E1): the recent documents (F11) of `docs/design/png/E1-Bibliotheque.png`, the
 * empty state of `E1-Bibliotheque-Vide.png` when there are none, or a message when the list
 * cannot be read. « Ouvrir un fichier » is a button of the empty state and a floating button
 * over the list. The tools and display actions of the mock-up appear with their screens
 * (ADR 0009, 0015).
 *
 * @param state what to show.
 * @param onOpenFile called when the user asks to pick a PDF with the system file picker.
 * @param onOpenSettings called when the user opens the settings (E3).
 * @param onOpenDocument called with the URI of the recent document the user taps.
 * @param modifier modifier applied to the screen.
 * @param loadThumbnail reads a thumbnail file, off the main thread; `null` when it cannot.
 * @param onRefreshThumbnail called with the identifier of a row shown with a missing or
 *   outdated thumbnail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onOpenFile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocument: (String) -> Unit,
    modifier: Modifier = Modifier,
    loadThumbnail: suspend (String) -> ImageBitmap? = { null },
    onRefreshThumbnail: (String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.library_settings))
                    }
                },
            )
        },
        floatingActionButton = { if (state is LibraryUiState.Content) OpenFileFab(onOpenFile) },
    ) { innerPadding ->
        val content = Modifier.fillMaxSize().padding(innerPadding)
        when (state) {
            LibraryUiState.Loading -> LoadingContent(content)

            LibraryUiState.Empty -> EmptyContent(onOpenFile, content)

            LibraryUiState.Error -> ErrorContent(onOpenFile, content)

            is LibraryUiState.Content ->
                RecentDocumentList(state.documents, onOpenDocument, loadThumbnail, onRefreshThumbnail, content)
        }
    }
}

/** Nothing but a label for screen readers: reading the list takes a few milliseconds. */
@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.library_loading)
    Box(modifier.semantics { contentDescription = label })
}

@Composable
private fun EmptyContent(onOpenFile: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        StatusMessage(
            title = stringResource(R.string.library_empty_title),
            message = stringResource(R.string.library_empty_message),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            illustration = {
                Image(painter = painterResource(UiR.drawable.illustration_lectern), contentDescription = null)
            },
            actions = { OpenFileButton(onOpenFile) },
        )
        OfflineNotice()
    }
}

@Composable
private fun ErrorContent(onOpenFile: () -> Unit, modifier: Modifier = Modifier) {
    StatusMessage(
        title = stringResource(R.string.library_error_title),
        message = stringResource(R.string.library_error_message),
        modifier = modifier,
        actions = { OpenFileButton(onOpenFile) },
    )
}

@Composable
private fun OpenFileButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = Modifier.heightIn(min = LutrinDimens.ButtonHeight),
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_folder),
            contentDescription = null,
            modifier = Modifier.size(LutrinDimens.ButtonIcon),
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.library_open_file))
    }
}

/**
 * The content-slot overload: with the `text` and `icon` slots, the label was not merged into the
 * button's semantics under Robolectric (a button without name for screen readers).
 */
@Composable
private fun OpenFileFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick) {
        Icon(painter = painterResource(UiR.drawable.ic_folder), contentDescription = null)
        Spacer(Modifier.width(FabIconSpacing))
        Text(stringResource(R.string.library_open_file))
    }
}

@Composable
private fun OfflineNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = LutrinDimens.ScreenGutter, end = LutrinDimens.ScreenGutter, bottom = OfflineNoticeBottom),
        horizontalArrangement = Arrangement.spacedBy(OfflineNoticeGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_shield),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(LutrinDimens.SmallIcon),
        )
        Text(
            text = stringResource(R.string.library_offline_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Time shown by the previews. */
private val PreviewOpenedAt: LocalTime = LocalTime.of(20, 41)

@Preview
@Composable
private fun LibraryScreenEmptyPreview() {
    LutrinTheme(dynamicColor = false) {
        LibraryScreen(state = LibraryUiState.Empty, onOpenFile = {}, onOpenSettings = {}, onOpenDocument = {})
    }
}

@Preview
@Composable
private fun LibraryScreenContentPreview() {
    val document =
        RecentDocumentItem(
            documentId = "1",
            uri = "content://preview/1",
            name = "Contrat de location",
            pageCount = 8,
            lastPage = 2,
            openedOn = OpenedOn.Today(PreviewOpenedAt),
            thumbnailPath = null,
        )
    LutrinTheme(dynamicColor = false) {
        LibraryScreen(
            state = LibraryUiState.Content(listOf(document)),
            onOpenFile = {},
            onOpenSettings = {},
            onOpenDocument = {},
        )
    }
}
