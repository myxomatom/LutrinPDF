// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.transition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shared element of the thumbnail → reader transition (ADR 0019), between two screens of an
 * `AnimatedContent`, as the navigation host shows them.
 */
@RunWith(AndroidJUnit4::class)
class DocumentTransitionTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** What each screen saw, by screen: its transition and whether it entered with an animation. */
    private val transitions = mutableMapOf<Screen, DocumentTransition?>()
    private val entering = mutableMapOf<Screen, Boolean?>()
    private val tilts = mutableMapOf<Screen, () -> Float>()

    private var screen by mutableStateOf(Screen.Library)

    private enum class Screen { Library, Reader }

    @Composable
    private fun TwoScreens(linkedDocumentId: String?, documentId: String = DOCUMENT) {
        DocumentTransitionLayout(linkedDocumentId) {
            AnimatedContent(targetState = screen, label = "screens") { shown ->
                DocumentTransitionDestination {
                    val transition = rememberDocumentTransition(documentId)
                    val enteringAtStart = remember { transition?.isEntering }
                    val tilt = animateWithDocumentTransition(transition, atRest = TILT, inFlight = 0f)
                    SideEffect {
                        transitions[shown] = transition
                        entering[shown] = enteringAtStart
                        tilts[shown] = { tilt.value }
                    }
                    val size = if (shown == Screen.Library) THUMBNAIL_SIZE else PAGE_SIZE
                    Box(
                        Modifier
                            .testTag(shown.name)
                            .size(size)
                            .sharedDocumentPage(transition)
                            .graphicsLayer { rotationZ = tilt.value },
                    )
                }
            }
        }
    }

    /** Shows the reader and stops the clock a few frames into the change of screen. */
    private fun startOpening() {
        composeRule.mainClock.autoAdvance = false
        screen = Screen.Reader
        // The clock is stopped: the change must reach the recomposer without waiting for idle.
        Snapshot.sendApplyNotifications()
        repeat(FRAMES_INTO_TRANSITION) { composeRule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun `there is no transition outside of the app's layout`() {
        var transition: DocumentTransition? = null
        var composed = false
        composeRule.setContent {
            transition = rememberDocumentTransition(DOCUMENT)
            composed = true
        }
        composeRule.waitForIdle()

        assertThat(composed).isTrue()
        assertThat(transition).isNull()
    }

    @Test
    fun `there is no transition outside of a destination`() {
        var transition: DocumentTransition? = null
        var composed = false
        composeRule.setContent {
            DocumentTransitionLayout(DOCUMENT) {
                transition = rememberDocumentTransition(DOCUMENT)
                composed = true
            }
        }
        composeRule.waitForIdle()

        assertThat(composed).isTrue()
        assertThat(transition).isNull()
    }

    @Test
    fun `a document that is not the linked one has no transition`() {
        composeRule.setContent { TwoScreens(linkedDocumentId = "other") }
        composeRule.waitForIdle()

        assertThat(transitions).containsKey(Screen.Library)
        assertThat(transitions[Screen.Library]).isNull()
    }

    @Test
    fun `nothing is linked when the app links no document`() {
        composeRule.setContent { TwoScreens(linkedDocumentId = null) }
        composeRule.waitForIdle()

        assertThat(transitions[Screen.Library]).isNull()
    }

    @Test
    fun `without a transition the modifier is left unchanged`() {
        assertThat(Modifier.sharedDocumentPage(null)).isSameInstanceAs(Modifier)
    }

    @Test
    fun `a screen shown at once is not entering, nor running`() {
        composeRule.setContent { TwoScreens(DOCUMENT) }
        composeRule.waitForIdle()

        val library = transitions.getValue(Screen.Library)
        assertThat(library).isNotNull()
        assertThat(entering[Screen.Library]).isFalse()
        assertThat(library?.isRunning).isFalse()
        assertThat(library?.isMatchFound).isFalse()
    }

    @Test
    fun `the thumbnail and the page find each other while the screens change`() {
        composeRule.setContent { TwoScreens(DOCUMENT) }
        composeRule.waitForIdle()

        startOpening()

        val library = transitions.getValue(Screen.Library)
        val reader = transitions.getValue(Screen.Reader)
        assertThat(entering[Screen.Reader]).isTrue()
        assertThat(reader?.isEntering).isTrue()
        assertThat(library?.isRunning).isTrue()
        assertThat(reader?.isRunning).isTrue()
        assertThat(library?.isMatchFound).isTrue()
        assertThat(reader?.isMatchFound).isTrue()
    }

    @Test
    fun `once the screens have changed, the page is alone and still`() {
        composeRule.setContent { TwoScreens(DOCUMENT) }
        composeRule.waitForIdle()
        startOpening()

        composeRule.mainClock.advanceTimeBy(TRANSITION_END_MS)
        composeRule.waitForIdle()

        val reader = transitions.getValue(Screen.Reader)
        assertThat(reader?.isRunning).isFalse()
        assertThat(reader?.isEntering).isFalse()
        assertThat(reader?.isMatchFound).isFalse()
        composeRule.onNodeWithTag(Screen.Library.name).assertDoesNotExist()
        composeRule.onNodeWithTag(Screen.Reader.name).assertExists()
    }

    @Test
    fun `two different documents never find each other`() {
        composeRule.setContent {
            DocumentTransitionLayout(DOCUMENT) {
                AnimatedContent(targetState = screen, label = "screens") { shown ->
                    DocumentTransitionDestination {
                        // The reader shows another document than the thumbnail.
                        val transition = rememberDocumentTransition(if (shown == Screen.Library) DOCUMENT else "other")
                        SideEffect { transitions[shown] = transition }
                        Box(Modifier.size(THUMBNAIL_SIZE).sharedDocumentPage(transition))
                    }
                }
            }
        }
        composeRule.waitForIdle()

        startOpening()

        assertThat(transitions[Screen.Reader]).isNull()
        assertThat(transitions.getValue(Screen.Library)?.isMatchFound).isFalse()
    }

    @Test
    fun `the value at rest is kept on screen and follows the transition when leaving`() {
        composeRule.setContent { TwoScreens(DOCUMENT) }
        composeRule.waitForIdle()
        assertThat(tilts.getValue(Screen.Library)()).isEqualTo(TILT)

        startOpening()
        composeRule.mainClock.advanceTimeBy(HALF_TRANSITION_MS)

        val tilt = tilts.getValue(Screen.Library)()
        assertThat(tilt).isGreaterThan(TILT)
        assertThat(tilt).isLessThan(0f)
    }

    @Test
    fun `without a transition the value stays at rest`() {
        composeRule.setContent { TwoScreens(linkedDocumentId = null) }
        composeRule.waitForIdle()

        startOpening()
        composeRule.mainClock.advanceTimeBy(HALF_TRANSITION_MS)

        assertThat(tilts.getValue(Screen.Library)()).isEqualTo(TILT)
    }

    @Test
    fun `an element inside a nested animation follows the destination, not that animation`() {
        var visible by mutableStateOf(false)
        composeRule.setContent {
            DocumentTransitionLayout(DOCUMENT) {
                AnimatedContent(targetState = screen, label = "screens") { shown ->
                    DocumentTransitionDestination {
                        AnimatedVisibility(visible) {
                            val transition = rememberDocumentTransition(DOCUMENT)
                            SideEffect { transitions[shown] = transition }
                            Box(Modifier.size(THUMBNAIL_SIZE).sharedDocumentPage(transition))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        // The nested element appears with its own animation while the screen stays put.
        visible = true
        Snapshot.sendApplyNotifications()
        repeat(FRAMES_INTO_TRANSITION) { composeRule.mainClock.advanceTimeByFrame() }

        val library = transitions.getValue(Screen.Library)
        assertThat(library).isNotNull()
        assertThat(library?.isEntering).isFalse()
        assertThat(library?.isRunning).isFalse()
    }

    private companion object {
        const val DOCUMENT = "0123456789abcdef0123456789abcdef"
        const val TILT = -3f
        const val FRAMES_INTO_TRANSITION = 3
        const val HALF_TRANSITION_MS = 200L
        const val TRANSITION_END_MS = 2_000L
        val THUMBNAIL_SIZE = 52.dp
        val PAGE_SIZE = 300.dp
    }
}
