// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.myxomatom.lutrin.feature.reader.ui.ReaderRoute

/** Navigation argument holding the document URI, as a string. */
const val READER_URI_ARG = "uri"

/** Navigation argument holding the zero-based page to open the document at (0 by default). */
const val READER_PAGE_ARG = "page"

private const val READER_ROUTE = "reader?$READER_URI_ARG={$READER_URI_ARG}&$READER_PAGE_ARG={$READER_PAGE_ARG}"

/**
 * Opens the reader on the document at [uri].
 *
 * @param uri the document, usually a `content://` URI granted to the app.
 * @param page zero-based page shown first; the reader clamps it to the document.
 * @param builder navigation options, for example to replace the screens below the reader.
 */
fun NavController.navigateToReader(uri: Uri, page: Int = 0, builder: NavOptionsBuilder.() -> Unit = {}) {
    navigate("reader?$READER_URI_ARG=${Uri.encode(uri.toString())}&$READER_PAGE_ARG=$page", builder)
}

/**
 * Adds the reader destination (E2) to a navigation graph.
 *
 * @param onBack called when the user leaves the reader.
 */
fun NavGraphBuilder.readerScreen(onBack: () -> Unit) {
    composable(
        route = READER_ROUTE,
        arguments =
            listOf(
                navArgument(READER_URI_ARG) { type = NavType.StringType },
                navArgument(READER_PAGE_ARG) {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
    ) {
        ReaderRoute(onBack = onBack)
    }
}
