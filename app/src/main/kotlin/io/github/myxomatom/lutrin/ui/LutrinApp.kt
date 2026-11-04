// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.myxomatom.lutrin.feature.library.LibraryScreen
import io.github.myxomatom.lutrin.feature.reader.ReaderDestination
import io.github.myxomatom.lutrin.feature.reader.readerScreen

/** Route of the library (E1), the start destination. */
internal const val LIBRARY_ROUTE: String = "library"

/** MIME type the system file picker is restricted to. */
private const val PDF_MIME_TYPE = "application/pdf"

/**
 * Root composable of the application: the navigation graph between the library (E1) and the
 * reader (E2).
 *
 * @param modifier modifier applied to the navigation host.
 * @param navController navigation state, replaceable in tests.
 */
@Composable
fun LutrinApp(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val resolver = LocalContext.current.contentResolver
    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) navController.navigate(ReaderDestination.route(uri.toString(), resolver.displayName(uri)))
    }
    NavHost(navController = navController, startDestination = LIBRARY_ROUTE, modifier = modifier) {
        composable(LIBRARY_ROUTE) {
            LibraryScreen(onOpenFile = { pickDocument.launch(arrayOf(PDF_MIME_TYPE)) })
        }
        readerScreen(onBack = { navController.popBackStack() })
    }
}

/**
 * Display name of a document granted by the system, or the last segment of its URI when the
 * provider does not give one. Never logged (`AGENTS.md` §1.8).
 */
internal fun ContentResolver.displayName(uri: Uri): String {
    // A missing name is not an error: the reader then shows the end of the URI.
    val name = try {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (ignored: SecurityException) {
        null
    } catch (ignored: IllegalArgumentException) {
        null
    }
    return name ?: uri.lastPathSegment.orEmpty()
}
