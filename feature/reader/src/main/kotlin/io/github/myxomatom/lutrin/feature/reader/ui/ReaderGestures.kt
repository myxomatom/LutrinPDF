// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import io.github.myxomatom.lutrin.feature.reader.render.MAX_ZOOM
import io.github.myxomatom.lutrin.feature.reader.render.MIN_ZOOM
import io.github.myxomatom.lutrin.feature.reader.render.PinchTransform
import io.github.myxomatom.lutrin.feature.reader.render.clampZoom
import io.github.myxomatom.lutrin.feature.reader.render.doubleTapZoom

/**
 * Transform of a double tap at [focus]: to [doubleTapZoom], around the tapped point.
 *
 * @param zoom zoom before the double tap.
 * @param focus tapped point, in the coordinates of the reading area.
 */
internal fun doubleTapTransform(zoom: Float, focus: Offset): PinchTransform =
    PinchTransform.zoomAround(clampZoom(doubleTapZoom(zoom)) / zoom, focus.x, focus.y)

/**
 * Draws the content with the transform of the pinch in progress, if any; read at draw time
 * only, so that a pinch neither recomposes nor relays out the pages.
 *
 * @param pinch the pinch in progress, or `null`.
 */
internal fun Modifier.pinchLayer(pinch: () -> PinchTransform?): Modifier = graphicsLayer {
    val transform = pinch() ?: PinchTransform()
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = transform.scale
    scaleY = transform.scale
    translationX = transform.translationX
    translationY = transform.translationY
}

/**
 * Pinch, double tap and tap detection of the reader (F04, F14), shared by the continuous and
 * the page-by-page views. The gesture code outlives recompositions, so it reads the zoom
 * through [rememberUpdatedState]-backed lambdas instead of capturing it once.
 *
 * @param zoom current zoom, to keep the pinch within the limits of F04.
 * @param onPinch transform since the start of the pinch, at every move.
 * @param onPinchEnd final transform of the pinch, when the fingers lift.
 * @param onDoubleTap called with the tapped point.
 * @param onTap called on a single tap, with the tapped point (in the coordinates of the modified
 *   element), to activate a link under it or else switch to full screen (F09, F14).
 */
@Composable
internal fun Modifier.readerGestures(
    zoom: Float,
    onPinch: (PinchTransform) -> Unit,
    onPinchEnd: (PinchTransform) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onTap: (Offset) -> Unit,
): Modifier {
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnPinch by rememberUpdatedState(onPinch)
    val currentOnPinchEnd by rememberUpdatedState(onPinchEnd)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnTap by rememberUpdatedState(onTap)
    return pinchToZoom(
        scaleBounds = { MIN_ZOOM / currentZoom to MAX_ZOOM / currentZoom },
        onPinch = { currentOnPinch(it) },
        onPinchEnd = { currentOnPinchEnd(it) },
    ).pointerInput(Unit) {
        detectTapGestures(onTap = { currentOnTap(it) }, onDoubleTap = { currentOnDoubleTap(it) })
    }
}

/**
 * Detects two-finger pinches without stealing single-finger scrolling from the list or the
 * pager. Once a pinch has started, every event of the gesture is consumed until all fingers are
 * up, so that the finger left on screen does not scroll the page.
 *
 * @param scaleBounds smallest and largest scale allowed for this gesture.
 * @param onPinch transform since the start of the gesture, at every move.
 * @param onPinchEnd final transform, when the fingers lift.
 */
private fun Modifier.pinchToZoom(
    scaleBounds: () -> Pair<Float, Float>,
    onPinch: (PinchTransform) -> Unit,
    onPinchEnd: (PinchTransform) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var transform: PinchTransform? = null
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val (minScale, maxScale) = scaleBounds()
                val centroid = event.calculateCentroid()
                val pan = event.calculatePan()
                val next = (transform ?: PinchTransform())
                    .step(event.calculateZoom(), centroid.x, centroid.y, pan.x, pan.y, minScale, maxScale)
                transform = next
                onPinch(next)
            }
            if (transform != null) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
        transform?.let(onPinchEnd)
    }
}
