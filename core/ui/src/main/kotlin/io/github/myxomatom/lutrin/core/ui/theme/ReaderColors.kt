// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colours around the rendered pages of the reader (`docs/design/design-tokens.json`, section
 * `color.reader`). They do not follow Material You: a document keeps the same frame whatever
 * the wallpaper.
 */
object ReaderColors {
    /** Background behind the pages, light theme. */
    val Background: Color = Color(0xFFE6E8ED)

    /** Background behind the pages, dark theme. */
    val BackgroundNight: Color = Color(0xFF0B0D10)

    /** Paper colour shown while a page is being rendered. */
    val PageNormal: Color = Color(0xFFFFFFFF)

    /** Paper colour of a page in night mode (F13). */
    val PageNight: Color = Color(0xFF16181C)
}
