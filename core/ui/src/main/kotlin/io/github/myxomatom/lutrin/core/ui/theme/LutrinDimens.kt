// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing and sizes of Lutrin (`docs/design/design-tokens.json`, section `spacing`, and the
 * paddings of the mockups in `docs/design/screens/`). Screens use these instead of raw values.
 */
object LutrinDimens {
    /** Horizontal margin of the content of a screen. */
    val ScreenGutter = 16.dp

    /** Minimum size of a touch target (WCAG, Material 3). */
    val TouchTargetMin = 48.dp

    /** Height of a full-width button of the mockups. */
    val ButtonHeight = 48.dp

    /** Horizontal margin of a centred status message (empty, error). */
    val StatusMessageGutter = 40.dp

    /** Vertical gap between the elements of a status message. */
    val StatusMessageSpacing = 16.dp

    /** Extra space above the actions of a status message. */
    val StatusMessageActionsTop = 12.dp

    /** Gap between stacked buttons. */
    val ButtonSpacing = 8.dp

    /** Size of an icon inside a button. */
    val ButtonIcon = 20.dp

    /** Size of a small inline icon (footers, captions). */
    val SmallIcon = 16.dp
}
