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

/**
 * Navigation argument holding the zero-based page to open the document at; [NO_PAGE] by
 * default, and the reader then resumes the saved page, or opens the first one (ADR 0015).
 */
const val READER_PAGE_ARG = "page"

/** Value of [READER_PAGE_ARG] when no page is asked: navigation arguments cannot be `null` integers. */
const val NO_PAGE: Int = -1

private const val READER_ROUTE = "reader?$READER_URI_ARG={$READER_URI_ARG}&$READER_PAGE_ARG={$READER_PAGE_ARG}"

/**
 * Opens the reader on the document at [uri].
 *
 * @param uri the document, usually a `content://` URI granted to the app.
 * @param page zero-based page shown first, clamped to the document by the reader; `null` (or a
 *   negative page) resumes where the user stopped reading, or opens the first page.
 * @param builder navigation options, for example to replace the screens below the reader.
 */
fun NavController.navigateToReader(uri: Uri, page: Int? = null, builder: NavOptionsBuilder.() -> Unit = {}) {
    val pageArgument = page?.takeIf { it >= 0 } ?: NO_PAGE
    navigate("reader?$READER_URI_ARG=${Uri.encode(uri.toString())}&$READER_PAGE_ARG=$pageArgument", builder)
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
                    defaultValue = NO_PAGE
                },
            ),
    ) {
        ReaderRoute(onBack = onBack)
    }
}
