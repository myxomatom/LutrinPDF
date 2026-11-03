// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Values from docs/design/design-tokens.json (color.light / color.dark). Keep both in sync.

/**
 * Light colour scheme of Lutrin, used when dynamic colours (Material You) are unavailable
 * (Android < 12) or disabled.
 */
val LutrinLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF2A5B8C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD3E4FF),
        onPrimaryContainer = Color(0xFF001C38),
        inversePrimary = Color(0xFFA1CAFD),
        secondary = Color(0xFF535F70),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD7E3F7),
        onSecondaryContainer = Color(0xFF101C2B),
        tertiary = Color(0xFF6B5778),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF3DAFF),
        onTertiaryContainer = Color(0xFF523F5F),
        background = Color(0xFFF8F9FF),
        onBackground = Color(0xFF191C20),
        surface = Color(0xFFF8F9FC),
        onSurface = Color(0xFF191C20),
        surfaceVariant = Color(0xFFE1E2E8),
        onSurfaceVariant = Color(0xFF43474E),
        inverseSurface = Color(0xFF2E3135),
        inverseOnSurface = Color(0xFFEFF0F7),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF73777F),
        outlineVariant = Color(0xFFC3C6CF),
        surfaceBright = Color(0xFFF8F9FF),
        surfaceDim = Color(0xFFD8DAE0),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF2F3F8),
        surfaceContainer = Color(0xFFECEEF3),
        surfaceContainerHigh = Color(0xFFE6E8ED),
        surfaceContainerHighest = Color(0xFFE1E2E8),
    )

/**
 * Dark colour scheme of Lutrin, used when dynamic colours are unavailable or disabled.
 * Generated from the same seed as the light scheme (see design-tokens.json).
 */
val LutrinDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFA1CAFD),
        onPrimary = Color(0xFF003259),
        primaryContainer = Color(0xFF1A4975),
        onPrimaryContainer = Color(0xFFD1E4FF),
        inversePrimary = Color(0xFF36618E),
        secondary = Color(0xFFBBC7DB),
        onSecondary = Color(0xFF253140),
        secondaryContainer = Color(0xFF3B4858),
        onSecondaryContainer = Color(0xFFD7E3F8),
        tertiary = Color(0xFFD7BEE4),
        onTertiary = Color(0xFF3B2948),
        tertiaryContainer = Color(0xFF523F5F),
        onTertiaryContainer = Color(0xFFF3DAFF),
        background = Color(0xFF111418),
        onBackground = Color(0xFFE1E2E8),
        surface = Color(0xFF111418),
        onSurface = Color(0xFFE1E2E8),
        surfaceVariant = Color(0xFF43474E),
        onSurfaceVariant = Color(0xFFC3C6CF),
        inverseSurface = Color(0xFFE1E2E8),
        inverseOnSurface = Color(0xFF2E3135),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8D9199),
        outlineVariant = Color(0xFF43474E),
        surfaceBright = Color(0xFF36393E),
        surfaceDim = Color(0xFF111418),
        surfaceContainerLowest = Color(0xFF0B0E13),
        surfaceContainerLow = Color(0xFF191C20),
        surfaceContainer = Color(0xFF1D2024),
        surfaceContainerHigh = Color(0xFF272A2F),
        surfaceContainerHighest = Color(0xFF32353A),
    )
