// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 theme of Lutrin. Every screen is drawn inside it.
 *
 * @param darkTheme whether to use the dark colour scheme; follows the system setting by default.
 * @param dynamicColor whether to use the wallpaper-based colours of Material You when the device
 *   supports them (Android 12+). Falls back to [LutrinLightColorScheme] / [LutrinDarkColorScheme].
 * @param content the UI to theme.
 */
@Composable
fun LutrinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = lutrinColorScheme(LocalContext.current, darkTheme, dynamicColor),
        typography = LutrinTypography,
        shapes = LutrinShapes,
        content = content,
    )
}

/**
 * Chooses the colour scheme for the given settings.
 *
 * @param context used to read the dynamic colours of the device.
 * @param darkTheme whether the dark scheme is wanted.
 * @param dynamicColor whether Material You colours are wanted; ignored before Android 12.
 * @return the dynamic scheme when requested and supported, otherwise the static Lutrin scheme.
 */
internal fun lutrinColorScheme(context: Context, darkTheme: Boolean, dynamicColor: Boolean): ColorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    darkTheme -> LutrinDarkColorScheme

    else -> LutrinLightColorScheme
}
