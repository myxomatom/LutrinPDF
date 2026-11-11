// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.transition

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import io.github.myxomatom.lutrin.core.ui.theme.LutrinMotion

/**
 * Key of the shared element linking the thumbnail of a recent document in the library (E1) and
 * its page in the reader (E2). Both sides build it from the identifier of the recent entry, so
 * that it is equal on both sides for one document and different for any other.
 *
 * @property documentId identifier of the recent entry (`documentIdFor` of `core:domain`).
 */
@Immutable
data class DocumentPageKey(val documentId: String)

/**
 * Semantics of an element that carries the shared element of [documentId][DocumentPageKey]: set
 * by [sharedDocumentPage], so that tests can tell when the thumbnail and the page are linked
 * (only the page going back to the library since the amended ADR 0019). Not read by
 * accessibility services.
 */
val SharedDocumentPage = SemanticsPropertyKey<String>("SharedDocumentPage")

/**
 * What [DocumentTransitionLayout] gives to the destinations below it.
 *
 * @property sharedTransitionScope scope of the shared elements, around the whole navigation.
 * @property linkedDocumentId the only document whose thumbnail and page are linked, or `null`.
 */
@Stable
internal class DocumentTransitionHost(val sharedTransitionScope: SharedTransitionScope, val linkedDocumentId: String?)

/** Set by [DocumentTransitionLayout]; `null` outside of it (previews, tests, other activities). */
internal val LocalDocumentTransitionHost = compositionLocalOf<DocumentTransitionHost?> { null }

/** Set by [DocumentTransitionDestination]; `null` outside of a navigation destination. */
internal val LocalDestinationVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Lays out the navigation of the app so that the thumbnail of [linkedDocumentId] can grow into
 * the reader's page and shrink back (ADR 0019). Every screen stays usable without it: outside of
 * this layout, [rememberDocumentTransition] returns `null`.
 *
 * @param linkedDocumentId the document tapped in the library ([DocumentTransitionPolicy.linkedDocument]),
 *   or `null`: no shared element, the destinations only fade.
 * @param modifier modifier applied to the layout.
 * @param content the navigation host; each destination wraps its screen in
 *   [DocumentTransitionDestination].
 */
@Composable
fun DocumentTransitionLayout(
    linkedDocumentId: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SharedTransitionLayout(modifier) {
        val host = remember(this, linkedDocumentId) { DocumentTransitionHost(this, linkedDocumentId) }
        CompositionLocalProvider(LocalDocumentTransitionHost provides host, content = content)
    }
}

/**
 * Marks [content] as one navigation destination, whose entering and leaving drive the shared
 * element: call it with the `AnimatedContentScope` of the destination.
 *
 * @param content the screen of the destination.
 */
@Composable
fun AnimatedVisibilityScope.DocumentTransitionDestination(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDestinationVisibilityScope provides this, content = content)
}

/**
 * The transition of [documentId] in the current destination, or `null` when it has none: outside
 * of [DocumentTransitionLayout] or of a [DocumentTransitionDestination], when [documentId] is not
 * the linked document ([DocumentTransitionPolicy.isLinked]), or when it is `null`.
 *
 * Each call holds its own shared element state: a screen calls it once per element that takes
 * part, and never applies two of them with the same document at the same time.
 *
 * @param documentId the document shown by the element.
 */
@Composable
fun rememberDocumentTransition(documentId: String?): DocumentTransition? {
    val key = documentId?.let(::DocumentPageKey)
    val host = LocalDocumentTransitionHost.current
        ?.takeIf { DocumentTransitionPolicy.isLinked(it.linkedDocumentId, documentId) }
    val visibilityScope = LocalDestinationVisibilityScope.current
    if (host == null || visibilityScope == null || key == null) return null
    val scope = host.sharedTransitionScope
    val contentState = with(scope) { rememberSharedContentState(key) }
    return remember(scope, visibilityScope, contentState) {
        DocumentTransition(key.documentId, scope, visibilityScope, contentState)
    }
}

/**
 * One element (a thumbnail, or the reader's page) of the thumbnail → reader transition of a
 * document, in one destination. Apply it with [sharedDocumentPage].
 */
@Stable
class DocumentTransition internal constructor(
    internal val documentId: String,
    internal val sharedTransitionScope: SharedTransitionScope,
    internal val visibilityScope: AnimatedVisibilityScope,
    internal val contentState: SharedTransitionScope.SharedContentState,
) {
    /**
     * Whether the destination is entering or leaving: a navigation, or a predictive back gesture
     * in progress (the gesture seeks the transition). Changes only when such a move starts or ends.
     */
    val isRunning: Boolean
        get() {
            val transition = visibilityScope.transition
            return transition.isRunning || transition.currentState != transition.targetState
        }

    /**
     * Whether the destination is coming on screen with an animation, as opposed to being shown at
     * once (start of the app, restored activity after a rotation or the death of the process).
     */
    val isEntering: Boolean
        get() {
            val transition = visibilityScope.transition
            return transition.currentState == EnterExitState.PreEnter &&
                transition.targetState == EnterExitState.Visible
        }

    /** Whether the other element of the pair is on screen in the other destination. */
    val isMatchFound: Boolean get() = contentState.isMatchFound

    /** Whether the destination is (or will end) on screen: its element is the incoming one. */
    internal val isIncoming: Boolean get() = visibilityScope.transition.targetState == EnterExitState.Visible
}

/**
 * Makes this element the shared bounds of [transition], or leaves it unchanged when [transition]
 * is `null`. Apply it before any drawing modifier that must travel with the element (rotation,
 * shadow): during the transition the element is drawn above both screens, at bounds moving from
 * one side to the other, with its content scaled to them (never measured again, so that a page
 * of the reader is not laid out at every frame). The incoming element fades in over the outgoing
 * one near the end of the trip, so that the picture stays visible while it moves.
 *
 * @param transition the element's transition, from [rememberDocumentTransition].
 */
fun Modifier.sharedDocumentPage(transition: DocumentTransition?): Modifier {
    if (transition == null) return this
    val modifier = this.semantics { this[SharedDocumentPage] = transition.documentId }
    return with(transition.sharedTransitionScope) {
        modifier.sharedBounds(
            sharedContentState = transition.contentState,
            animatedVisibilityScope = transition.visibilityScope,
            enter = IncomingFadeIn,
            exit = ExitTransition.None,
            boundsTransform = PageBoundsTransform,
            resizeMode = PageResizeMode,
            // The incoming element is drawn over the outgoing one, which needs no fade of its own.
            zIndexInOverlay = if (transition.isIncoming) INCOMING_Z_INDEX else 0f,
        )
    }
}

/**
 * A value that follows the transition of the destination: [atRest] while it is on screen,
 * [inFlight] when it is off screen, animated in between with the shared element (the tilt of
 * the resume card's page straightens while it travels). Read it in a drawing lambda, such as
 * `graphicsLayer`, so that the animation does not recompose. Without [transition], always [atRest].
 *
 * @param transition the element's transition, or `null`.
 * @param atRest value while the destination is shown.
 * @param inFlight value when the destination is off screen.
 */
@Composable
fun animateWithDocumentTransition(transition: DocumentTransition?, atRest: Float, inFlight: Float): State<Float> {
    if (transition == null) return rememberUpdatedState(atRest)
    return transition.visibilityScope.transition.animateFloat(
        transitionSpec = { pageTransitionSpec() },
        label = "documentTransitionValue",
    ) { state -> if (state == EnterExitState.Visible) atRest else inFlight }
}

/** Overlay rank of the incoming element over the outgoing one. */
private const val INCOMING_Z_INDEX = 1f

/** The incoming element fades in during the last part of the trip. */
private const val INCOMING_FADE_DELAY_MILLIS = 150
private const val INCOMING_FADE_MILLIS = 200

private val IncomingFadeIn: EnterTransition =
    fadeIn(tween(INCOMING_FADE_MILLIS, delayMillis = INCOMING_FADE_DELAY_MILLIS, easing = LinearEasing))

/** Same length as the screen fade and the tilt: the whole move ends at once. */
private fun <T> pageTransitionSpec() =
    tween<T>(LutrinMotion.PAGE_TRANSITION_MILLIS, easing = LutrinMotion.EmphasizedEasing)

private val PageBoundsTransform = BoundsTransform { _, _ -> pageTransitionSpec() }

/** Both elements are pages: stretched to the moving bounds, their shapes differ only slightly. */
private val PageResizeMode =
    SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillBounds, Alignment.Center)
