// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

/** Zoom factors of the reader (F04), relative to the page fitted to the screen width. */
internal object ZoomLevels {
    /** Pages fill the width of the screen, minus margins. */
    const val FIT_WIDTH: Float = 1f

    /** Smallest zoom: 25 %. */
    const val MIN: Float = 0.25f

    /** Largest zoom: 800 %. */
    const val MAX: Float = 8f

    /** Zoom reached by a double tap from the fitted width. */
    const val DOUBLE_TAP: Float = 2f

    /** Zoom after multiplying [current] by a pinch [factor], kept within [MIN]..[MAX]. */
    fun pinched(current: Float, factor: Float): Float = (current * factor).coerceIn(MIN, MAX)

    /** Zoom after a double tap: zooms in from the fitted width or below, back to it otherwise. */
    fun doubleTapped(current: Float): Float = if (current > FIT_WIDTH) FIT_WIDTH else DOUBLE_TAP
}
