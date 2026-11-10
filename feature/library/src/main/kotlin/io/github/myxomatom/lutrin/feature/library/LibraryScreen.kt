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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
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

/** Height of the top bar, for the 32 sp title « Lutrin » (E1 mock-up adopted by ADR 0018). */
private val TopBarHeight = 72.dp

/**
 * Library screen (E1): the recent documents (F11) of
 * `docs/design/proposals/png/E1-Bibliotheque.png` (ADR 0018), the most recent one in a
 * « Reprendre la lecture » card and the others in a list; the empty state of
 * `docs/design/png/E1-Bibliotheque-Vide.png` when there are none, or a message when the list
 * cannot be read. The title « Lutrin » is set in `headlineLarge`. « Ouvrir un fichier » is a
 * button of the empty state and a floating button, in the primary colour, over the list. The
 * tools and display actions of the mock-up appear with their screens (ADR 0009, 0015). The card
 * and each row have a « ⋮ » menu with « Retirer des récents »; a removed document disappears at
 * once (the next one takes the card) and a message offers « Annuler » while [pendingRemoval] is
 * set (ADR 0016).
 *
 * @param state what to show.
 * @param onOpenFile called when the user asks to pick a PDF with the system file picker.
 * @param onOpenSettings called when the user opens the settings (E3).
 * @param onOpenDocument called with the URI of the recent document the user taps (card or row).
 * @param modifier modifier applied to the screen.
 * @param loadThumbnail reads a thumbnail file for a frame of the given size in pixels, off the
 *   main thread; `null` when it cannot.
 * @param onRefreshThumbnail called with the identifier of a row shown with a missing or
 *   outdated thumbnail.
 * @param pendingRemoval the removal for which « Annuler » is offered, or `null`.
 * @param onRemove called with the identifier of the document whose « Retirer des récents » is
 *   chosen, in the card or in a row.
 * @param onUndoRemoval called when the user taps « Annuler ».
 * @param onConfirmRemoval called when the offer to undo ends without undo: timeout, swipe, or
 *   the screen leaving the composition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onOpenFile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocument: (String) -> Unit,
    modifier: Modifier = Modifier,
    loadThumbnail: suspend (String, IntSize) -> ImageBitmap? = { _, _ -> null },
    onRefreshThumbnail: (String) -> Unit = {},
    pendingRemoval: PendingRemoval? = null,
    onRemove: (String) -> Unit = {},
    onUndoRemoval: (PendingRemoval) -> Unit = {},
    onConfirmRemoval: (PendingRemoval) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    RemovalMessageEffect(pendingRemoval, snackbarHostState, onUndoRemoval, onConfirmRemoval)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineLarge)
                },
                expandedHeight = TopBarHeight,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.library_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { if (state is LibraryUiState.Content) OpenFileFab(onOpenFile) },
    ) { innerPadding ->
        val content = Modifier.fillMaxSize().padding(innerPadding)
        when (state) {
            LibraryUiState.Loading -> LoadingContent(content)

            LibraryUiState.Empty -> EmptyContent(onOpenFile, content)

            LibraryUiState.Error -> ErrorContent(onOpenFile, content)

            is LibraryUiState.Content ->
                RecentDocumentList(
                    content = state,
                    onOpen = onOpenDocument,
                    onRemove = onRemove,
                    loadThumbnail = loadThumbnail,
                    onRefreshThumbnail = onRefreshThumbnail,
                    modifier = content,
                )
        }
    }
}

/**
 * Shows « “name” retiré des récents » with « Annuler » while [removal] is pending, then reports
 * how the offer ended. Any other ending than « Annuler » confirms the removal, including this
 * effect leaving the composition (another removal, navigation, rotation): the row never comes
 * back without the user asking for it.
 */
@Composable
private fun RemovalMessageEffect(
    removal: PendingRemoval?,
    hostState: SnackbarHostState,
    onUndo: (PendingRemoval) -> Unit,
    onConfirm: (PendingRemoval) -> Unit,
) {
    val message = removal?.let { stringResource(R.string.library_removed, it.name) }
    val undo = stringResource(R.string.library_undo)
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    LaunchedEffect(removal) {
        if (removal == null || message == null) return@LaunchedEffect
        var result: SnackbarResult? = null
        try {
            result = hostState.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Long)
        } finally {
            if (result == SnackbarResult.ActionPerformed) currentOnUndo(removal) else currentOnConfirm(removal)
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
 * In the primary colour (ADR 0018). The content-slot overload: with the `text` and `icon` slots,
 * the label was not merged into the button's semantics under Robolectric (a button without name
 * for screen readers).
 */
@Composable
private fun OpenFileFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
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
            isReadingInProgress = true,
        )
    val other =
        document.copy(
            documentId = "2",
            name = "Billet de train",
            pageCount = 1,
            lastPage = 0,
            isReadingInProgress = false,
        )
    LutrinTheme(dynamicColor = false) {
        LibraryScreen(
            state = LibraryUiState.Content(document, listOf(other)),
            onOpenFile = {},
            onOpenSettings = {},
            onOpenDocument = {},
        )
    }
}
