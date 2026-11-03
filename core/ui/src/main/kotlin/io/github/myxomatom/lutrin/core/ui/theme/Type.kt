// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val baseline = Typography()

private fun TextStyle.sized(fontSize: Int, lineHeight: Int, fontWeight: FontWeight) =
    copy(fontSize = fontSize.sp, lineHeight = lineHeight.sp, fontWeight = fontWeight)

/**
 * Type scale of Lutrin (`docs/design/design-tokens.json`, section `typography`), on the system
 * font (Roboto). Styles absent from the tokens keep the Material 3 defaults.
 */
val LutrinTypography: Typography =
    baseline.copy(
        headlineSmall = baseline.headlineSmall.sized(fontSize = 24, lineHeight = 32, fontWeight = FontWeight.Normal),
        titleLarge = baseline.titleLarge.sized(fontSize = 22, lineHeight = 28, fontWeight = FontWeight.Normal),
        titleMedium = baseline.titleMedium.sized(fontSize = 16, lineHeight = 24, fontWeight = FontWeight.Medium),
        bodyLarge = baseline.bodyLarge.sized(fontSize = 16, lineHeight = 22, fontWeight = FontWeight.Normal),
        bodyMedium = baseline.bodyMedium.sized(fontSize = 14, lineHeight = 20, fontWeight = FontWeight.Normal),
        bodySmall = baseline.bodySmall.sized(fontSize = 12, lineHeight = 16, fontWeight = FontWeight.Normal),
        labelLarge = baseline.labelLarge.sized(fontSize = 14, lineHeight = 20, fontWeight = FontWeight.Medium),
    )
