// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.myxomatom.lutrin.core.ui.R

/**
 * Font family of the titles: Fraunces SemiBold (600), a static TTF bundled in `core:ui`
 * (`res/font/fraunces_semibold.ttf`, SIL OFL 1.1, ADR 0018). It covers Basic Latin, Latin-1 and
 * Latin Extended, so French and English titles are fully covered; any other character falls back
 * to the system font. Nothing is downloaded at run time.
 *
 * Only the 600 weight exists: whatever weight a style asks for, this family draws SemiBold.
 */
val LutrinTitleFontFamily: FontFamily = FontFamily(Font(R.font.fraunces_semibold, FontWeight.SemiBold))

private val baseline = Typography()

private fun TextStyle.sized(fontSize: Int, lineHeight: Int, fontWeight: FontWeight) =
    copy(fontSize = fontSize.sp, lineHeight = lineHeight.sp, fontWeight = fontWeight)

/** Same as [sized], on the title font ([LutrinTitleFontFamily]) at weight 600. */
private fun TextStyle.titled(fontSize: Int, lineHeight: Int) =
    sized(fontSize, lineHeight, FontWeight.SemiBold).copy(fontFamily = LutrinTitleFontFamily)

private const val COMPACT_TITLE_SIZE_SP = 20
private const val COMPACT_TITLE_LINE_HEIGHT_SP = 26
private const val SERIF_SUBTITLE_SIZE_SP = 18
private const val SERIF_SUBTITLE_LINE_HEIGHT_SP = 24

/**
 * Type scale of Lutrin (`docs/design/design-tokens.json`, section `typography`).
 *
 * Titles are set in Fraunces 600 ([LutrinTitleFontFamily]); body text and labels stay on the
 * system font (Roboto on Pixel and AOSP). Styles absent from the tokens keep the Material 3
 * defaults. Which style to use where (ADR 0018):
 *
 * | Style | Size / line | Use |
 * |-------|-------------|-----|
 * | `displayLarge` | 56 / 64 sp | The big percentage of a running tool (E9). |
 * | `headlineLarge` | 32 / 40 sp | The application title on the library (E1), "PDF créé" (E9). |
 * | `headlineSmall` | 24 / 32 sp | Dialog titles (`AlertDialog` uses it by default), title of the tool screens (E9). |
 * | `titleLarge` | 22 / 28 sp | Top app bar and sheet titles (`TopAppBar` uses it by default), title of the resume card (E1). |
 * | [titleLargeCompact] | 20 / 26 sp | Title of the reader bar (E2), which shares its row with three actions. |
 * | [titleMediumSerif] | 18 / 24 sp | Small serif caption, e.g. the page number of the slider preview (E2). |
 * | `titleMedium` | 16 / 24 sp, 500 | List item and card titles, system font. |
 * | `bodyLarge`, `bodyMedium`, `bodySmall`, `labelLarge` | as before | Unchanged, system font. |
 */
val LutrinTypography: Typography =
    baseline.copy(
        displayLarge = baseline.displayLarge.titled(fontSize = 56, lineHeight = 64),
        headlineLarge = baseline.headlineLarge.titled(fontSize = 32, lineHeight = 40),
        headlineSmall = baseline.headlineSmall.titled(fontSize = 24, lineHeight = 32),
        titleLarge = baseline.titleLarge.titled(fontSize = 22, lineHeight = 28),
        titleMedium = baseline.titleMedium.sized(fontSize = 16, lineHeight = 24, fontWeight = FontWeight.Medium),
        bodyLarge = baseline.bodyLarge.sized(fontSize = 16, lineHeight = 22, fontWeight = FontWeight.Normal),
        bodyMedium = baseline.bodyMedium.sized(fontSize = 14, lineHeight = 20, fontWeight = FontWeight.Normal),
        bodySmall = baseline.bodySmall.sized(fontSize = 12, lineHeight = 16, fontWeight = FontWeight.Normal),
        labelLarge = baseline.labelLarge.sized(fontSize = 14, lineHeight = 20, fontWeight = FontWeight.Medium),
    )

/**
 * Variant of `titleLarge` at 20 / 26 sp, for the title of the reader bar (E2): same font, so the
 * long title of a document fits next to the search, outline and options actions.
 */
val Typography.titleLargeCompact: TextStyle
    get() = titleLarge.sized(COMPACT_TITLE_SIZE_SP, COMPACT_TITLE_LINE_HEIGHT_SP, FontWeight.SemiBold)

/**
 * Small serif style at 18 / 24 sp (Fraunces 600), for a short caption inside a card, such as
 * "Page 6" under the slider preview (E2).
 */
val Typography.titleMediumSerif: TextStyle
    get() = titleLarge.sized(SERIF_SUBTITLE_SIZE_SP, SERIF_SUBTITLE_LINE_HEIGHT_SP, FontWeight.SemiBold)
