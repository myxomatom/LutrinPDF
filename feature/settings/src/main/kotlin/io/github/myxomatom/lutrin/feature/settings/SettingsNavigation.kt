// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

private const val SETTINGS_ROUTE = "settings"

/** Opens the settings screen (E3). */
fun NavController.navigateToSettings() {
    navigate(SETTINGS_ROUTE)
}

/**
 * Adds the settings destination (E3) to a navigation graph.
 *
 * @param onBack called when the user leaves the settings.
 */
fun NavGraphBuilder.settingsScreen(onBack: () -> Unit) {
    composable(SETTINGS_ROUTE) { SettingsRoute(onBack = onBack) }
}
