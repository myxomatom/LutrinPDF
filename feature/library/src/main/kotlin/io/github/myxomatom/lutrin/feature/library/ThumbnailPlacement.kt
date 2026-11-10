// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Where a thumbnail is shown, which sets its size and colours (E1 mock-up adopted by ADR 0018).
 *
 * @property size size of the frame.
 */
internal enum class ThumbnailPlacement(val size: DpSize) {
    /** In a row of the « Récents » list, on the surface: 52 × 68 dp. */
    InList(DpSize(52.dp, 68.dp)),

    /** In the « Reprendre la lecture » card, on `primaryContainer`: 104 × 138 dp. */
    InCard(DpSize(104.dp, 138.dp)),
}
