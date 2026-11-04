// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.myxomatom.lutrin.feature.library.LibraryScreen
import io.github.myxomatom.lutrin.feature.reader.navigateToReader
import io.github.myxomatom.lutrin.feature.reader.readerScreen
import io.github.myxomatom.lutrin.feature.settings.navigateToSettings
import io.github.myxomatom.lutrin.feature.settings.settingsScreen

/** Route of the library (E1), the start destination. */
private const val LIBRARY_ROUTE = "library"

/** MIME type offered by the system file picker (F01). */
private const val PDF_MIME_TYPE = "application/pdf"

/**
 * Root composable: the navigation graph between the library (E1), the reader (E2) and the
 * settings (E3).
 *
 * @param modifier modifier applied to the navigation host.
 */
@Composable
fun LutrinApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = LIBRARY_ROUTE, modifier = modifier) {
        composable(LIBRARY_ROUTE) {
            val picker =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    uri?.let(navController::navigateToReader)
                }
            LibraryScreen(
                onOpenFile = { picker.launch(arrayOf(PDF_MIME_TYPE)) },
                onOpenSettings = navController::navigateToSettings,
            )
        }
        readerScreen(onBack = navController::popBackStack)
        settingsScreen(onBack = navController::popBackStack)
    }
}
