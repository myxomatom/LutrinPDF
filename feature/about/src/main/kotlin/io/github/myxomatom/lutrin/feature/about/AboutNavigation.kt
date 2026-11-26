// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

private const val ABOUT_ROUTE = "about"

/** Opens « À propos et licences » (E4). */
fun NavController.navigateToAbout() {
    navigate(ABOUT_ROUTE)
}

/**
 * Adds the destination of « À propos et licences » (E4) to a navigation graph.
 *
 * @param onBack called when the user leaves E4.
 */
fun NavGraphBuilder.aboutScreen(onBack: () -> Unit) {
    composable(ABOUT_ROUTE) { AboutRoute(onBack = onBack) }
}
