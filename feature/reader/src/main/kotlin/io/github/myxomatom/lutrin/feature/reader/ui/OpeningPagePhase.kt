// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransition

/**
 * Where the page standing for the document is, while the reader opens from a thumbnail of the
 * library (ADR 0019). The thumbnail grows into this page during the transition; the page then
 * waits, blank, while the document is still opening, and fades away once the reader shows
 * something else: the document (whose pages are blank paper too until they are drawn), the
 * password dialog or the error screen.
 */
internal enum class OpeningPagePhase {
    /** The navigation (or a back gesture) is moving: the page is the shared element. */
    Shared,

    /** The navigation has ended but the document is still opening: the page stays in place. */
    Waiting,

    /** No opening page, for good: the reader shows its own state. */
    Gone,
}

/**
 * The phase of the opening page when the reader first shows: [OpeningPagePhase.Shared] when it
 * comes on screen with an animation from a linked thumbnail, otherwise [OpeningPagePhase.Gone]
 * (no thumbnail, animations off, opened by another app or from the file picker, restored after
 * a rotation or the death of the process).
 *
 * @param enteringWithTransition whether the reader is entering with the shared element.
 */
internal fun initialOpeningPagePhase(enteringWithTransition: Boolean): OpeningPagePhase =
    if (enteringWithTransition) OpeningPagePhase.Shared else OpeningPagePhase.Gone

/**
 * The next phase of the opening page. Once [OpeningPagePhase.Gone], it never comes back: the
 * reader's page takes over the shared element (back to the library).
 *
 * @param previous the current phase.
 * @param transitionRunning whether the navigation is moving (entering, or leaving again).
 * @param documentLoading whether the document is still opening (`ReaderUiState.Loading`).
 */
internal fun nextOpeningPagePhase(
    previous: OpeningPagePhase,
    transitionRunning: Boolean,
    documentLoading: Boolean,
): OpeningPagePhase = when {
    previous == OpeningPagePhase.Gone -> OpeningPagePhase.Gone
    transitionRunning -> OpeningPagePhase.Shared
    documentLoading -> OpeningPagePhase.Waiting
    else -> OpeningPagePhase.Gone
}

/**
 * The phase of the opening page of the reader, following [transition] and the opening of the
 * document. Reads only states that change when the navigation starts or ends and when the
 * document opens: nothing recomposes during the animation.
 *
 * @param transition the opening page's transition, `null` when the reader has none.
 * @param documentLoading whether the document is still opening.
 */
@Composable
internal fun rememberOpeningPagePhase(transition: DocumentTransition?, documentLoading: Boolean): OpeningPagePhase {
    var phase by remember { mutableStateOf(initialOpeningPagePhase(transition?.isEntering == true)) }
    val next = nextOpeningPagePhase(phase, transition?.isRunning == true, documentLoading)
    SideEffect { phase = next }
    return next
}
