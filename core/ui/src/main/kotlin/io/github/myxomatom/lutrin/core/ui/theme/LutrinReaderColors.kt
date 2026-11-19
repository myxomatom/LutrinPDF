// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.theme

import androidx.compose.ui.graphics.Color

// Values from docs/design/design-tokens.json (color.reader). Keep both in sync (DesignTokensTest).

/**
 * Colours drawn over the pages of a document, whatever the theme of the interface: a page is
 * white paper in the dark theme too, so these colours do not follow the colour scheme. They come
 * from the section `color.reader` of the design tokens.
 */
object LutrinReaderColors {
    /** Every occurrence of a search on the pages shown (F07, V1): translucent yellow. */
    val SearchHit: Color = Color(red = 255, green = 214, blue = 90, alpha = 179)

    /** The current occurrence of a search, darker than [SearchHit] so that it stands out. */
    val SearchHitCurrent: Color = Color(red = 245, green = 166, blue = 35, alpha = 191)

    /** Outline of the current occurrence, so that it is told apart without relying on hue only. */
    val SearchHitCurrentOutline: Color = Color(0xFFB06A00)

    /** Text selected on a page (F08, ADR 0024): translucent blue of the brand, the text stays legible. */
    val Selection: Color = Color(red = 42, green = 91, blue = 140, alpha = 77)

    /** Handles of a selection, opaque so that they stand out on any page. */
    val SelectionHandle: Color = Color(0xFF2A5B8C)
}
