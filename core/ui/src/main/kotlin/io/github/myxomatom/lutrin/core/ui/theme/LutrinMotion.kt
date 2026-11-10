// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Durations and easing of the transitions between screens (ADR 0019), taken from the Material 3
 * motion tokens. Material 3 for Compose keeps its duration tokens internal and its public
 * `MotionScheme` only offers springs, whose length cannot be given: the thumbnail → reader
 * transition needs a known length to time its cross-fade, so the values are written here.
 */
object LutrinMotion {
    /** Material 3 `medium4`: the thumbnail growing into the reader's page, and back. */
    const val PAGE_TRANSITION_MILLIS = 400

    /** Material 3 `medium2`: the fade between two screens. */
    const val SCREEN_FADE_MILLIS = 300

    /** Material 3 `short3`: a short fade, such as the opening page giving way to the document. */
    const val SHORT_FADE_MILLIS = 150

    /** Material 3 emphasized easing, as Compose Material 3 writes it (`EasingEmphasizedCubicBezier`). */
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
