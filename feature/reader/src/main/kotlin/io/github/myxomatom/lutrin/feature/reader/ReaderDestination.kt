// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** Navigation destination of the reader (E2). */
object ReaderDestination {
    /** Argument holding the `content://` URI of the document. */
    const val ARG_URI: String = "uri"

    /** Argument holding the display name of the document. */
    const val ARG_TITLE: String = "title"

    /** Route pattern registered in the navigation graph. */
    const val ROUTE: String = "reader?$ARG_URI={$ARG_URI}&$ARG_TITLE={$ARG_TITLE}"

    /**
     * Route opening a document.
     *
     * @param uri `content://` URI granted by the system file picker or an incoming intent.
     * @param title display name of the document.
     */
    fun route(uri: String, title: String): String = "reader?$ARG_URI=${Uri.encode(uri)}&$ARG_TITLE=${Uri.encode(title)}"
}

/**
 * Adds the reader to a navigation graph.
 *
 * @param onBack called when the user leaves the reader.
 */
fun NavGraphBuilder.readerScreen(onBack: () -> Unit) {
    composable(
        route = ReaderDestination.ROUTE,
        arguments = listOf(
            navArgument(ReaderDestination.ARG_URI) { type = NavType.StringType },
            navArgument(ReaderDestination.ARG_TITLE) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) {
        ReaderRoute(onBack = onBack)
    }
}
