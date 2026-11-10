// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the page slider (F05) does with a gesture, in both states of the page preview option
 * (ADR 0018). It only decides which page to show and when; the caller moves the document.
 *
 * - **Preview on**: while the slider is dragged, the document stays where it is and
 *   [previewPage] is the page aimed at (the card above the slider shows it). On release,
 *   [onRelease] gives that page to go to; [aimedPage] keeps it until [onArrived], so that the
 *   indicator and the thumb do not flash back to the page left. A tap on the track, a key press
 *   or an accessibility action is a press and a release: the document goes there at once.
 * - **Preview off**: every move gives the page to show at once ([onSlide]), as before.
 *
 * A cancelled gesture ([cancel]) forgets the page aimed at: the document stays on the page where
 * the gesture started.
 *
 * @property previewEnabled whether the slider previews the page (the user's setting).
 */
@Stable
internal class PageScrubber(val previewEnabled: Boolean) {
    /**
     * Page shown by the slider and the page indicator instead of the current page: the page aimed
     * at, from the first move until the document has reached it. `null` otherwise.
     */
    var aimedPage: Int? by mutableStateOf(null)
        private set

    /** Whether the slider is being dragged with the preview on: the preview card is shown. */
    var isPreviewing: Boolean by mutableStateOf(false)
        private set

    /** Page shown in the preview card while [isPreviewing], `null` otherwise. */
    val previewPage: Int? get() = aimedPage.takeIf { isPreviewing }

    /**
     * The slider moved to [page].
     *
     * @return the page to show now (preview off), or `null` to wait for the release (preview on).
     */
    fun onSlide(page: Int): Int? {
        if (!previewEnabled) return page
        aimedPage = page
        isPreviewing = true
        return null
    }

    /**
     * The slider was released.
     *
     * @return the page to go to (preview on, after a move), or `null` when there is nothing to do.
     *   The caller reports the end of the move with [onArrived].
     */
    fun onRelease(): Int? {
        if (!isPreviewing) return null
        isPreviewing = false
        return aimedPage
    }

    /** The document reached the page given by [onRelease]: the slider shows the current page again. */
    fun onArrived() {
        if (!isPreviewing) aimedPage = null
    }

    /** The gesture was cancelled: no page to go to, the document stays where it is. */
    fun cancel() {
        isPreviewing = false
        aimedPage = null
    }
}
