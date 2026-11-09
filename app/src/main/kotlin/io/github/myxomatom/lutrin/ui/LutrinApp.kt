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
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.myxomatom.lutrin.R
import io.github.myxomatom.lutrin.core.domain.document.IncomingDocumentResult
import io.github.myxomatom.lutrin.core.domain.document.IncomingRejection
import io.github.myxomatom.lutrin.feature.library.LibraryScreen
import io.github.myxomatom.lutrin.feature.reader.navigateToReader
import io.github.myxomatom.lutrin.feature.reader.readerScreen
import io.github.myxomatom.lutrin.feature.settings.navigateToSettings
import io.github.myxomatom.lutrin.feature.settings.settingsScreen
import kotlinx.coroutines.launch

/** Route of the library (E1), the start destination. */
private const val LIBRARY_ROUTE = "library"

/** MIME type offered by the system file picker (F01). */
private const val PDF_MIME_TYPE = "application/pdf"

/**
 * Root composable: the navigation graph between the library (E1), the reader (E2) and the
 * settings (E3), and the routing of documents sent by other apps (F01, ADR 0014).
 *
 * A document sent by another app replaces the whole back stack with its reader: back from it
 * leaves the activity and returns to that app. A refused request brings back the library, with
 * a message saying why.
 *
 * @param modifier modifier applied to the screen.
 * @param incomingRequest document request from another app not handled yet, or `null`.
 * @param onConsumeIncomingRequest called with [incomingRequest] once it is handled.
 * @param onExit leaves the activity, when back is pressed on the only screen left.
 */
@Composable
fun LutrinApp(
    modifier: Modifier = Modifier,
    incomingRequest: IncomingDocumentResult? = null,
    onConsumeIncomingRequest: (IncomingDocumentResult) -> Unit = {},
    onExit: () -> Unit = {},
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    Box(modifier.fillMaxSize()) {
        NavHost(navController, startDestination = LIBRARY_ROUTE, modifier = Modifier.fillMaxSize()) {
            composable(LIBRARY_ROUTE) {
                val picker =
                    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                        uri?.let { navController.navigateToReader(it) }
                    }
                LibraryScreen(
                    onOpenFile = { picker.launch(arrayOf(PDF_MIME_TYPE)) },
                    onOpenSettings = navController::navigateToSettings,
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
