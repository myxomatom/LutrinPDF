// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.IntSize
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.myxomatom.lutrin.R
import io.github.myxomatom.lutrin.core.domain.document.IncomingDocumentResult
import io.github.myxomatom.lutrin.core.domain.document.IncomingRejection
import io.github.myxomatom.lutrin.core.domain.recents.documentIdFor
import io.github.myxomatom.lutrin.core.ui.theme.LutrinMotion
import io.github.myxomatom.lutrin.core.ui.transition.DocumentLink
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransitionDestination
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransitionLayout
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransitionPolicy
import io.github.myxomatom.lutrin.feature.library.LibraryScreen
import io.github.myxomatom.lutrin.feature.library.LibraryUiState
import io.github.myxomatom.lutrin.feature.library.PendingRemoval
import io.github.myxomatom.lutrin.feature.reader.navigateToReader
import io.github.myxomatom.lutrin.feature.reader.readerScreen
import io.github.myxomatom.lutrin.feature.settings.navigateToSettings
import io.github.myxomatom.lutrin.feature.settings.settingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Route of the library (E1), the start destination. */
private const val LIBRARY_ROUTE = "library"

/** MIME type offered by the system file picker (F01). */
private const val PDF_MIME_TYPE = "application/pdf"

/** Default of [LutrinApp]'s `pendingRemoval`: nothing to undo. */
private val NoPendingRemoval: StateFlow<PendingRemoval?> = MutableStateFlow(null)

/**
 * Root composable: the navigation graph between the library (E1), the reader (E2) and the
 * settings (E3), and the routing of documents sent by other apps (F01, ADR 0014).
 *
 * A document sent by another app replaces the whole back stack with its reader: back from it
 * leaves the activity and returns to that app. A refused request brings back the library, with
 * a message saying why. A document picked in the system file picker or tapped in the recent list
 * opens in the reader at the page where the user stopped reading (ADR 0015).
 *
 * Screens fade into each other. Once the reader of a document tapped in the library is settled,
 * its page is linked to the thumbnail and shrinks back into it on return, back gesture included
 * (ADR 0019, [DocumentLink]); only that document is linked, and only while the system plays
 * animations. Going to the reader is always a plain fade, as are the other ways into it (file
 * picker, other apps).
 *
 * @param libraryState the recent documents, collected while the library is shown.
 * @param modifier modifier applied to the screen.
 * @param loadThumbnail reads the thumbnail file of a recent document, for a frame of the given
 *   size in pixels.
 * @param onRefreshThumbnail called with a recent document shown with a missing or outdated
 *   thumbnail.
 * @param pendingRemoval the removal of a recent document for which « Annuler » is offered.
 * @param onRemoveRecent called with a recent document the user removes from the library.
 * @param onUndoRemoval called when the user undoes a removal.
 * @param onConfirmRemoval called when the offer to undo a removal ends without undo.
 * @param incomingRequest document request from another app not handled yet, or `null`.
 * @param onConsumeIncomingRequest called with [incomingRequest] once it is handled.
 * @param onExit leaves the activity, when back is pressed on the only screen left.
 */
@Composable
fun LutrinApp(
    libraryState: StateFlow<LibraryUiState>,
    modifier: Modifier = Modifier,
    loadThumbnail: suspend (String, IntSize) -> ImageBitmap? = { _, _ -> null },
    onRefreshThumbnail: (String) -> Unit = {},
    pendingRemoval: StateFlow<PendingRemoval?> = NoPendingRemoval,
    onRemoveRecent: (String) -> Unit = {},
    onUndoRemoval: (PendingRemoval) -> Unit = {},
    onConfirmRemoval: (PendingRemoval) -> Unit = {},
    incomingRequest: IncomingDocumentResult? = null,
    onConsumeIncomingRequest: (IncomingDocumentResult) -> Unit = {},
    onExit: () -> Unit = {},
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    // The document last opened by a tap on its thumbnail; kept across a rotation or the death of
    // the process, so that back from its reader still shrinks the page into the thumbnail.
    var link by rememberSaveable(stateSaver = DocumentLink.Saver) { mutableStateOf(DocumentLink()) }
    val linkedDocumentId = DocumentTransitionPolicy.linkedDocument(link.linked, rememberAnimationsEnabled())
    LinkOnceSettled(navController) { onLibrary -> link = link.settled(onLibrary) }
    Box(modifier.fillMaxSize()) {
        DocumentTransitionLayout(linkedDocumentId, Modifier.fillMaxSize()) {
            LutrinNavHost(
                navController = navController,
                onLinkDocument = { documentId ->
                    link = if (documentId == null) link.openedElsewhere() else link.tapped(documentId)
                },
                libraryState = libraryState,
                loadThumbnail = loadThumbnail,
                onRefreshThumbnail = onRefreshThumbnail,
                pendingRemoval = pendingRemoval,
                onRemoveRecent = onRemoveRecent,
                onUndoRemoval = onUndoRemoval,
                onConfirmRemoval = onConfirmRemoval,
                onExit = onExit,
            )
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
    }
    IncomingRequestEffect(incomingRequest, navController, snackbarHostState) { request ->
        // Opened by another app: no thumbnail of the library takes part.
        link = link.openedElsewhere()
        onConsumeIncomingRequest(request)
    }
}

/**
 * Calls [onSettle] each time the current screen is settled: its navigation entry resumed, which
 * Navigation Compose does only once the transition into it has ended. Tells whether it is the
 * library, so that [DocumentLink.settled] links the tapped document only when its reader is on
 * screen, never while it comes in.
 */
@Composable
private fun LinkOnceSettled(navController: NavHostController, onSettle: (onLibrary: Boolean) -> Unit) {
    val currentOnSettle by rememberUpdatedState(onSettle)
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collectLatest { entry ->
            entry.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            currentOnSettle(entry.destination.route == LIBRARY_ROUTE)
        }
    }
}

/**
 * The navigation graph: library (E1), reader (E2) and settings (E3), fading into each other.
 *
 * @param onLinkDocument called with the document whose thumbnail grows into the reader, or
 *   `null` when the reader opens another way.
 */
@Suppress("LongParameterList") // Plain wiring of the parameters of LutrinApp.
@Composable
private fun LutrinNavHost(
    navController: NavHostController,
    onLinkDocument: (String?) -> Unit,
    libraryState: StateFlow<LibraryUiState>,
    loadThumbnail: suspend (String, IntSize) -> ImageBitmap?,
    onRefreshThumbnail: (String) -> Unit,
    pendingRemoval: StateFlow<PendingRemoval?>,
    onRemoveRecent: (String) -> Unit,
    onUndoRemoval: (PendingRemoval) -> Unit,
    onConfirmRemoval: (PendingRemoval) -> Unit,
    onExit: () -> Unit,
) {
    NavHost(
        navController,
        startDestination = LIBRARY_ROUTE,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { fadeIn(tween(LutrinMotion.SCREEN_FADE_MILLIS)) },
        exitTransition = { fadeOut(tween(LutrinMotion.SCREEN_FADE_MILLIS)) },
    ) {
        composable(LIBRARY_ROUTE) {
            DocumentTransitionDestination {
                LibraryRoute(
                    libraryState = libraryState,
                    pendingRemoval = pendingRemoval,
                    navController = navController,
                    onLinkDocument = onLinkDocument,
                    loadThumbnail = loadThumbnail,
                    onRefreshThumbnail = onRefreshThumbnail,
                    onRemoveRecent = onRemoveRecent,
                    onUndoRemoval = onUndoRemoval,
                    onConfirmRemoval = onConfirmRemoval,
                )
            }
        }
        readerScreen(onBack = { navController.goBack(onExit) })
        settingsScreen(onBack = navController::popBackStack)
    }
}

/**
 * The library destination (E1), with its file picker. A recent document tapped is linked to its
 * reader once that one is settled, so that the page shrinks back into the thumbnail; a document
 * from the picker is not (ADR 0019).
 *
 * @param onLinkDocument called with the identifier of the document opened by a tap, or `null`
 *   for a document opened from the file picker, just before the reader opens.
 */
@Suppress("LongParameterList") // Plain wiring of the parameters of LutrinApp.
@Composable
private fun LibraryRoute(
    libraryState: StateFlow<LibraryUiState>,
    pendingRemoval: StateFlow<PendingRemoval?>,
    navController: NavController,
    onLinkDocument: (String?) -> Unit,
    loadThumbnail: suspend (String, IntSize) -> ImageBitmap?,
    onRefreshThumbnail: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onUndoRemoval: (PendingRemoval) -> Unit,
    onConfirmRemoval: (PendingRemoval) -> Unit,
) {
    val state by libraryState.collectAsStateWithLifecycle()
    val removal by pendingRemoval.collectAsStateWithLifecycle()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                onLinkDocument(null)
                navController.navigateToReader(it)
            }
        }
    LibraryScreen(
        state = state,
        onOpenFile = { picker.launch(arrayOf(PDF_MIME_TYPE)) },
        onOpenSettings = navController::navigateToSettings,
        onOpenDocument = { uri ->
            onLinkDocument(documentIdFor(uri))
            navController.navigateToReader(uri.toUri())
        },
        loadThumbnail = loadThumbnail,
        onRefreshThumbnail = onRefreshThumbnail,
        pendingRemoval = removal,
        onRemove = onRemoveRecent,
        onUndoRemoval = onUndoRemoval,
        onConfirmRemoval = onConfirmRemoval,
    )
}

/**
 * Handles [request] once: opens its document, or shows the library and the reason of the refusal.
 * Placed after the [NavHost], whose graph must be set before navigating.
 */
@Composable
private fun IncomingRequestEffect(
    request: IncomingDocumentResult?,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    onConsume: (IncomingDocumentResult) -> Unit,
) {
    val resources = LocalResources.current
    // The message outlives this effect, which ends as soon as the request is marked handled.
    val messageScope = rememberCoroutineScope()
    val currentOnConsume by rememberUpdatedState(onConsume)
    LaunchedEffect(request) {
        when (request) {
            is IncomingDocumentResult.Accepted -> navController.openIncomingDocument(request)

            is IncomingDocumentResult.Rejected -> {
                navController.showLibraryOnly()
                val message = resources.getString(request.reason.message)
                messageScope.launch { snackbarHostState.showSnackbar(message) }
            }

            IncomingDocumentResult.NoDocument, null -> return@LaunchedEffect
        }
        currentOnConsume(request)
    }
}

/** Opens the document of another app as the only screen: back returns to that app. */
private fun NavController.openIncomingDocument(request: IncomingDocumentResult.Accepted) {
    navigateToReader(request.uri.toUri(), request.pageIndex) {
        popUpTo(graph.id) { inclusive = true }
    }
}

/** Leaves only the library on the back stack. */
private fun NavController.showLibraryOnly() {
    if (currentDestination?.route == LIBRARY_ROUTE && previousBackStackEntry == null) return
    navigate(LIBRARY_ROUTE) { popUpTo(graph.id) { inclusive = true } }
}

/** Back from a screen: the previous screen, or out of the activity when it is the only one. */
private fun NavController.goBack(onExit: () -> Unit) {
    if (previousBackStackEntry != null) popBackStack() else onExit()
}

/** Message explaining a refused request (`AGENTS.md` §9). */
@get:StringRes
private val IncomingRejection.message: Int
    get() =
        when (this) {
            IncomingRejection.MISSING_URI -> R.string.incoming_rejected_missing

            IncomingRejection.MALFORMED_URI,
            IncomingRejection.UNSUPPORTED_SCHEME,
            IncomingRejection.APP_OWN_URI,
            -> R.string.incoming_rejected_link

            IncomingRejection.UNSUPPORTED_TYPE -> R.string.incoming_rejected_type
        }
