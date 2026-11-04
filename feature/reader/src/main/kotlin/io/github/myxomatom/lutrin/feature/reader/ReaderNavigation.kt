// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.myxomatom.lutrin.feature.reader.ui.ReaderRoute

/** Navigation argument holding the document URI, as a string. */
const val READER_URI_ARG = "uri"

private const val READER_ROUTE = "reader?$READER_URI_ARG={$READER_URI_ARG}"

/** Opens the reader on the document at [uri]. */
fun NavController.navigateToReader(uri: Uri) {
    navigate("reader?$READER_URI_ARG=${Uri.encode(uri.toString())}")
}

/**
 * Adds the reader destination (E2) to a navigation graph.
 *
 * @param onBack called when the user leaves the reader.
 */
fun NavGraphBuilder.readerScreen(onBack: () -> Unit) {
    composable(
        route = READER_ROUTE,
        arguments = listOf(navArgument(READER_URI_ARG) { type = NavType.StringType }),
    ) {
        ReaderRoute(onBack = onBack)
    }
}
