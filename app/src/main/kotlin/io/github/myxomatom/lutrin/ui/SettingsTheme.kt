// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme

/** Scrim of the navigation bar in the light theme: the default of `enableEdgeToEdge`. */
private val LightNavigationScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)

/** Scrim of the navigation bar in the dark theme: the default of `enableEdgeToEdge`. */
private val DarkNavigationScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

/**
 * [LutrinTheme] as the user set it (F13, ADR 0010, ADR 0028): light, dark or the system theme,
 * followed live, with or without dynamic colours. The icons of the system bars follow the theme
 * of the app, not only that of the system, so that they stay visible when the user forces one.
 *
 * @param settings the stored settings.
 * @param content the UI to theme.
 */
@Composable
internal fun SettingsTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val darkTheme = settings.appTheme.isDark(systemDark = isSystemInDarkTheme())
    SystemBarsFollowTheme(darkTheme)
    LutrinTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor, content = content)
}

/**
 * Light or dark icons of the status and navigation bars for [darkTheme]; the bars stay
 * transparent, edge to edge. Nothing outside a [ComponentActivity] (previews, tests).
 */
@Composable
private fun SystemBarsFollowTheme(darkTheme: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(activity, darkTheme) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) { darkTheme },
        )
        onDispose {}
    }
}
