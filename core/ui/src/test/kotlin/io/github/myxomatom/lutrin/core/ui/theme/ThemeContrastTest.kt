// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * WCAG 2.1 AA contrast (F18, `AGENTS.md` §11) of the colours that carry text in the two themes
 * without dynamic colour: 4.5:1 for every text on the colour it is drawn on, 3:1 for the borders of
 * the controls. The same pairs are drawn by the Material components, so a failing pair is a real
 * defect of the design tokens, not of a screen.
 */
class ThemeContrastTest {
    private val themes = mapOf("light" to LutrinLightColorScheme, "dark" to LutrinDarkColorScheme)

    /** Text colour on background colour, for the pairs Material 3 draws. */
    private fun textPairs(scheme: ColorScheme): Map<String, Pair<Color, Color>> = with(scheme) {
        mapOf(
            "onBackground on background" to (onBackground to background),
            "onSurface on surface" to (onSurface to surface),
            "onSurfaceVariant on surface" to (onSurfaceVariant to surface),
            "onSurfaceVariant on surfaceVariant" to (onSurfaceVariant to surfaceVariant),
            "onSurface on surfaceContainer" to (onSurface to surfaceContainer),
            "onSurfaceVariant on surfaceContainer" to (onSurfaceVariant to surfaceContainer),
            "onSurface on surfaceContainerHigh" to (onSurface to surfaceContainerHigh),
            "onSurfaceVariant on surfaceContainerHigh" to (onSurfaceVariant to surfaceContainerHigh),
            "onSurface on surfaceContainerLow" to (onSurface to surfaceContainerLow),
            "onSurfaceVariant on surfaceContainerLow" to (onSurfaceVariant to surfaceContainerLow),
            "onPrimary on primary" to (onPrimary to primary),
            "onPrimaryContainer on primaryContainer" to (onPrimaryContainer to primaryContainer),
            "onSecondary on secondary" to (onSecondary to secondary),
            "onSecondaryContainer on secondaryContainer" to (onSecondaryContainer to secondaryContainer),
            "onTertiary on tertiary" to (onTertiary to tertiary),
            "onTertiaryContainer on tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
            "onError on error" to (onError to error),
            "onErrorContainer on errorContainer" to (onErrorContainer to errorContainer),
            "primary on surface (text buttons)" to (primary to surface),
            "primary on surfaceContainerHigh (dialog buttons)" to (primary to surfaceContainerHigh),
            "error on surface (error messages)" to (error to surface),
            "inverseOnSurface on inverseSurface (snackbar)" to (inverseOnSurface to inverseSurface),
            "inversePrimary on inverseSurface (snackbar action)" to (inversePrimary to inverseSurface),
        )
    }

    @Test
    fun `the text of both themes has at least 4,5 to 1 on its background`() {
        themes.forEach { (theme, scheme) ->
            textPairs(scheme).forEach { (pair, colours) ->
                val ratio = contrast(colours.first, colours.second)
                assertWithMessage("$theme theme, $pair: %s to 1", "%.2f".format(ratio)).that(ratio).isAtLeast(AA_TEXT)
            }
        }
    }

    @Test
    fun `the borders of the controls have at least 3 to 1 on the surface`() {
        themes.forEach { (theme, scheme) ->
            val ratio = contrast(scheme.outline, scheme.surface)
            assertWithMessage("$theme theme, outline on surface: %s to 1", "%.2f".format(ratio))
                .that(ratio).isAtLeast(AA_NON_TEXT)
        }
    }

    /** WCAG relative contrast of two colours, the first one drawn over the second when it is translucent. */
    private fun contrast(foreground: Color, background: Color): Double {
        val front = foreground.compositeOver(background)
        val lighter = maxOf(front.luminance(), background.luminance()).toDouble()
        val darker = minOf(front.luminance(), background.luminance()).toDouble()
        return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
    }

    private companion object {
        const val AA_TEXT = 4.5
        const val AA_NON_TEXT = 3.0
        const val CONTRAST_OFFSET = 0.05
    }
}
