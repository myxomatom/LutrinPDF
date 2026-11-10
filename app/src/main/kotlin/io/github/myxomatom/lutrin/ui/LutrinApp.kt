// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.IntSize
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.myxomatom.lutrin.R
import io.github.myxomatom.lutrin.core.domain.document.IncomingDocumentResult
import io.github.myxomatom.lutrin.core.domain.document.IncomingRejection
import io.github.myxomatom.lutrin.feature.library.LibraryScreen
import io.github.myxomatom.lutrin.feature.library.LibraryUiState
import io.github.myxomatom.lutrin.feature.library.PendingRemoval
import io.github.myxomatom.lutrin.feature.reader.navigateToReader
import io.github.myxomatom.lutrin.feature.reader.readerScreen
import io.github.myxomatom.lutrin.feature.settings.navigateToSettings
import io.github.myxomatom.lutrin.feature.settings.settingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    Box(modifier.fillMaxSize()) {
        NavHost(navController, startDestination = LIBRARY_ROUTE, modifier = Modifier.fillMaxSize()) {
            composable(LIBRARY_ROUTE) {
                val state by libraryState.collectAsStateWithLifecycle()
                val removal by pendingRemoval.collectAsStateWithLifecycle()
                val picker =
                    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                        uri?.let { navController.navigateToReader(it) }
                    }
                LibraryScreen(
                    state = state,
                    onOpenFile = { picker.launch(arrayOf(PDF_MIME_TYPE)) },
                    onOpenSettings = navController::navigateToSettings,
                    onOpenDocument = { navController.navigateToReader(it.toUri()) },
                    loadThumbnail = loadThumbnail,
                    onRefreshThumbnail = onRefreshThumbnail,
                    pendingRemoval = removal,
                    onRemove = onRemoveRecent,
                    onUndoRemoval = onUndoRemoval,
                    onConfirmRemoval = onConfirmRemoval,
                )
            }
            readerScreen(onBack = { navController.goBack(onExit) })
            settingsScreen(onBack = navController::popBackStack)
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
    }
    IncomingRequestEffect(incomingRequest, navController, snackbarHostState, onConsumeIncomingRequest)
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
