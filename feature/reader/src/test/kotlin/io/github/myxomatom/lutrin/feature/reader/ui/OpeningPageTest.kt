// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransition
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransitionDestination
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransitionLayout
import io.github.myxomatom.lutrin.core.ui.transition.rememberDocumentTransition
import io.github.myxomatom.lutrin.core.ui.transition.sharedDocumentPage
import io.github.myxomatom.lutrin.feature.reader.ReaderError
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reader's side of the thumbnail → reader transition (ADR 0019): the opening page that the
 * thumbnail grows into, the rule that decides how long it stays, and the reader's page taking
 * over the shared element for the way back.
 */
@RunWith(AndroidJUnit4::class)
class OpeningPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    private enum class Screen { Library, Reader }

    private var screen by mutableStateOf(Screen.Library)
    private var readerState by mutableStateOf<ReaderUiState>(ReaderUiState.Loading)
    private var thumbnailTransition: DocumentTransition? = null

    private fun ready(): ReaderUiState.Ready {
        val document = FakePdfDocument()
        return ReaderUiState.Ready(
            "Contrat de location",
            document.pageSizes,
            PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
        )
    }

    /**
     * The library's thumbnail and the reader, as the app's navigation shows them.
     *
     * @param withThumbnail whether the library's element takes part (not for a protected document).
     * @param readerDocumentId what the app gives the reader; `null` as in previews and tests.
     */
    @Composable
    private fun LibraryAndReader(withThumbnail: Boolean = true, readerDocumentId: String? = DOCUMENT) {
        LutrinTheme(dynamicColor = false) {
            DocumentTransitionLayout(linkedDocumentId = DOCUMENT) {
                AnimatedContent(targetState = screen, label = "screens") { shown ->
                    DocumentTransitionDestination {
                        if (shown == Screen.Library) {
                            val transition = rememberDocumentTransition(DOCUMENT.takeIf { withThumbnail })
                            SideEffect { thumbnailTransition = transition }
                            Box(Modifier.size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT).sharedDocumentPage(transition))
                        } else {
                            ReaderScreen(readerState, onBack = {}, documentId = readerDocumentId)
                        }
                    }
                }
            }
        }
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent(content)
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }

    /** The clock is stopped: a change must reach the recomposer without waiting for idle. */
    private fun advanceFrames() {
        Snapshot.sendApplyNotifications()
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun finishTransitions() {
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    private fun openingPage() = composeRule.onNodeWithTag(OPENING_PAGE_TAG)

    @Test
    fun `the phase starts shared only for an animated entrance`() {
        assertThat(initialOpeningPagePhase(enteringWithTransition = true)).isEqualTo(OpeningPagePhase.Shared)
        assertThat(initialOpeningPagePhase(enteringWithTransition = false)).isEqualTo(OpeningPagePhase.Gone)
    }

    @Test
    fun `the page is shared while the navigation moves, whatever the document`() {
        listOf(true, false).forEach { loading ->
            assertThat(
                nextOpeningPagePhase(OpeningPagePhase.Shared, transitionRunning = true, documentLoading = loading),
            )
                .isEqualTo(OpeningPagePhase.Shared)
        }
    }

    @Test
    fun `after the navigation the page waits while the document opens, then goes`() {
        assertThat(nextOpeningPagePhase(OpeningPagePhase.Shared, transitionRunning = false, documentLoading = true))
            .isEqualTo(OpeningPagePhase.Waiting)
        assertThat(nextOpeningPagePhase(OpeningPagePhase.Waiting, transitionRunning = false, documentLoading = false))
            .isEqualTo(OpeningPagePhase.Gone)
        assertThat(nextOpeningPagePhase(OpeningPagePhase.Shared, transitionRunning = false, documentLoading = false))
            .isEqualTo(OpeningPagePhase.Gone)
    }

    @Test
    fun `a waiting page is shared again when the user goes back before the document opens`() {
        assertThat(nextOpeningPagePhase(OpeningPagePhase.Waiting, transitionRunning = true, documentLoading = true))
            .isEqualTo(OpeningPagePhase.Shared)
    }

    @Test
    fun `a page gone never comes back`() {
        listOf(true, false).forEach { running ->
            listOf(true, false).forEach { loading ->
                assertThat(
                    nextOpeningPagePhase(OpeningPagePhase.Gone, running, loading),
                ).isEqualTo(OpeningPagePhase.Gone)
            }
        }
    }

    @Test
    fun `the page shown is shared at zoom 1 or less, none when zoomed in or without transition`() {
        assertThat(sharedPageIndex(currentPage = 4, zoom = 1f, hasTransition = true)).isEqualTo(4)
        assertThat(sharedPageIndex(currentPage = 4, zoom = 0.5f, hasTransition = true)).isEqualTo(4)
        assertThat(sharedPageIndex(currentPage = 4, zoom = 2f, hasTransition = true)).isNull()
        assertThat(sharedPageIndex(currentPage = 4, zoom = 1f, hasTransition = false)).isNull()
    }

    @Test
    fun `only the shared page gets the shared modifier`() {
        val shared = SharedPage(2, Modifier.size(1.dp))

        assertThat(Modifier.sharedPage(shared, 2)).isSameInstanceAs(shared.modifier)
        assertThat(Modifier.sharedPage(shared, 1)).isSameInstanceAs(Modifier)
        assertThat(Modifier.sharedPage(null, 2)).isSameInstanceAs(Modifier)
    }

    @Test
    fun `a reader shown at once has no opening page`() {
        screen = Screen.Reader
        readerState = ready()

        show { LibraryAndReader() }

        openingPage().assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Document, page 1 of 3").assertExists()
    }

    @Test
    fun `opening from the thumbnail shows the opening page, which goes once the document is shown`() {
        readerState = ready()
        show { LibraryAndReader() }

        screen = Screen.Reader
        advanceFrames()

        openingPage().assertExists()
        assertThat(thumbnailTransition?.isMatchFound).isTrue()
        finishTransitions()
        openingPage().assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Document, page 1 of 3").assertExists()
    }

    @Test
    fun `the opening page waits while the document is still opening`() {
        show { LibraryAndReader() }

        screen = Screen.Reader
        advanceFrames()
        finishTransitions()

        openingPage().assertExists()
        readerState = ready()
        composeRule.waitForIdle()
        openingPage().assertDoesNotExist()
    }

    @Test
    fun `the opening page gives way to the error screen`() {
        show { LibraryAndReader() }
        screen = Screen.Reader
        advanceFrames()
        finishTransitions()

        readerState = ReaderUiState.Error(ReaderError.UNREADABLE)
        composeRule.waitForIdle()

        openingPage().assertDoesNotExist()
    }

    @Test
    fun `the opening page gives way to the password dialog`() {
        show { LibraryAndReader() }
        screen = Screen.Reader
        advanceFrames()
        finishTransitions()

        readerState = ReaderUiState.PasswordRequired("Contrat de location")
        composeRule.waitForIdle()

        openingPage().assertDoesNotExist()
    }

    @Test
    fun `a reader without document identifier has no opening page`() {
        readerState = ready()
        show { LibraryAndReader(readerDocumentId = null) }

        screen = Screen.Reader
        advanceFrames()

        openingPage().assertDoesNotExist()
        assertThat(thumbnailTransition?.isMatchFound).isFalse()
    }

    @Test
    fun `without a thumbnail on the other side the opening page finds no match`() {
        readerState = ready()
        show { LibraryAndReader(withThumbnail = false) }

        screen = Screen.Reader
        advanceFrames()

        // Composed but never drawn: it only shows once the thumbnail has been found.
        openingPage().assertExists()
        assertThat(thumbnailTransition).isNull()
        finishTransitions()
        openingPage().assertDoesNotExist()
    }

    @Test
    fun `going back during the opening shrinks the opening page into the thumbnail`() {
        show { LibraryAndReader() }
        screen = Screen.Reader
        advanceFrames()

        screen = Screen.Library
        advanceFrames()

        openingPage().assertExists()
        assertThat(thumbnailTransition?.isMatchFound).isTrue()
        finishTransitions()
        openingPage().assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Opening the document…").assertDoesNotExist()
    }

    @Test
    fun `once open, the page shown shrinks back into the thumbnail`() {
        readerState = ready()
        show { LibraryAndReader() }
        screen = Screen.Reader
        advanceFrames()
        finishTransitions()
        openingPage().assertDoesNotExist()
        composeRule.mainClock.autoAdvance = false

        screen = Screen.Library
        advanceFrames()

        assertThat(thumbnailTransition?.isMatchFound).isTrue()
        openingPage().assertDoesNotExist()
        finishTransitions()
    }

    @Test
    fun `back to a library without the thumbnail, the reader only fades`() {
        readerState = ready()
        screen = Screen.Reader
        show { LibraryAndReader(withThumbnail = false) }

        screen = Screen.Library
        advanceFrames()

        assertThat(thumbnailTransition).isNull()
        finishTransitions()
        composeRule.onNodeWithContentDescription("Document, page 1 of 3").assertDoesNotExist()
    }

    private companion object {
        const val DOCUMENT = "0123456789abcdef0123456789abcdef"
        const val FRAMES = 3
        const val SETTLE_MS = 2_000L
        val THUMBNAIL_WIDTH = 104.dp
        val THUMBNAIL_HEIGHT = 138.dp
    }
}
