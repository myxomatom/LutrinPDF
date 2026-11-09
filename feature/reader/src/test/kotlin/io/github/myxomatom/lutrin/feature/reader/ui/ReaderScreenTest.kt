// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderError
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val document = FakePdfDocument()

    private fun ready(
        scrollMode: ScrollMode = ScrollMode.CONTINUOUS,
        currentPage: Int = 0,
        doc: FakePdfDocument = document,
    ) = ReaderUiState.Ready(
        "Contrat de location",
        doc.pageSizes,
        PageRenderer(doc, doc.pageSizes, Dispatchers.Unconfined),
        scrollMode,
        currentPage,
    )

    /**
     * Shows the reader wired like [io.github.myxomatom.lutrin.feature.reader.ReaderViewModel]:
     * the chosen mode and the reported page come back in the state. Returns the reported pages.
     */
    private fun showConnected(initial: ReaderUiState.Ready): List<Int> {
        val reported = mutableListOf<Int>()
        var state by mutableStateOf(initial)
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                ReaderScreen(
                    state = state,
                    onBack = {},
                    onPageChange = {
                        reported += it
                        state = state.copy(currentPage = it)
                    },
                    onScrollModeChange = { state = state.copy(scrollMode = it) },
                )
            }
        }
        return reported
    }

    private fun swipeDocument(page: Int, pageCount: Int = 3, left: Boolean = true) {
        composeRule.onNodeWithContentDescription("Document, page $page of $pageCount").performTouchInput {
            if (left) swipeLeft() else swipeRight()
        }
        composeRule.waitForIdle()
    }

    private fun show(state: ReaderUiState, onBack: () -> Unit = {}, onRemoveFromRecents: () -> Unit = {}) {
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                ReaderScreen(state, onBack, onRemoveFromRecents = onRemoveFromRecents)
            }
        }
    }

    @Test
    fun `shows a progress indicator while loading`() {
        show(ReaderUiState.Loading)

        composeRule.onNodeWithContentDescription("Opening the document…").assertIsDisplayed()
    }

    @Test
    fun `explains an unreadable document and goes back`() {
        var back = false
        show(ReaderUiState.Error(ReaderError.UNREADABLE), onBack = { back = true })

        composeRule.onNodeWithText("This document cannot be opened").assertIsDisplayed()
        composeRule.onNodeWithText("The file may be damaged or incomplete.").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        assertThat(back).isTrue()
    }

    @Test
    fun `explains every error reason`() {
        val messages =
            mapOf(
                ReaderError.UNSUPPORTED_FORMAT to "This file is not a PDF.",
                ReaderError.NOT_ACCESSIBLE to "The file was moved or deleted, or access to it was revoked.",
            )
        var reason by mutableStateOf(ReaderError.UNSUPPORTED_FORMAT)
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) { ReaderScreen(ReaderUiState.Error(reason), onBack = {}) }
        }
        messages.forEach { (error, text) ->
            reason = error
            composeRule.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "fr")
    fun `error screen is translated into French`() {
        show(ReaderUiState.Error(ReaderError.UNREADABLE))

        composeRule.onNodeWithText("Impossible d’ouvrir ce document").assertIsDisplayed()
        composeRule.onNodeWithText("Retour").assertIsDisplayed()
    }

    @Test
    fun `shows the title, the page indicator and renders the first page`() {
        show(ready())

        composeRule.onNodeWithText("Contrat de location").assertIsDisplayed()
        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Fit to width").assertIsDisplayed()
        composeRule.waitUntil { document.renders.isNotEmpty() }
        assertThat(document.renders.first().pageIndex).isEqualTo(0)
    }

    @Test
    fun `a tap on the document switches to full screen and back`() {
        show(ready())

        composeRule.onNodeWithContentDescription("Document, page 1 of 3").performTouchInput { click(center) }
        // A single tap is confirmed once the double-tap delay has passed.
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MS)
        composeRule.onNodeWithText("Page 1 / 3").assertIsDisplayed()
        composeRule.onNodeWithText("Contrat de location").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Document, page 1 of 3").performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MS)
        composeRule.onNodeWithText("Contrat de location").assertIsDisplayed()
    }

    @Test
    fun `jumping to a page with the slider shows that page in the indicator`() {
        // Regression: the indicator read the page touching the top edge, one before the real one.
        show(ready())

        composeRule.onNodeWithContentDescription("Go to page").performSemanticsAction(SemanticsActions.SetProgress) {
            it(1f)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Document, page 2 of 3").assertIsDisplayed()
    }

    @Test
    fun `back arrow leaves the reader`() {
        var back = false
        show(ready(), onBack = { back = true })

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(back).isTrue()
    }

    @Test
    fun `scrolling continuously from a given page shows that page in the indicator`() {
        val reported = showConnected(ready(currentPage = 1))

        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()
        assertThat(reported).containsExactly(1)
    }

    @Test
    fun `more options opens the display options with the current scroll mode`() {
        show(ready())

        composeRule.onNodeWithContentDescription("More options").performClick()

        composeRule.onNodeWithText("Display").assertIsDisplayed()
        composeRule.onNodeWithText("Scrolling").assertIsDisplayed()
        composeRule.onNodeWithText("Continuous").assertIsSelected()
        composeRule.onNodeWithText("Page by page").assertIsNotSelected()
    }

    @Test
    fun `choosing page by page reports it and keeps the sheet open`() {
        val chosen = mutableListOf<ScrollMode>()
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                ReaderScreen(ready(), onBack = {}, onScrollModeChange = { chosen += it })
            }
        }

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Page by page").performClick()

        assertThat(chosen).containsExactly(ScrollMode.PAGED)
        composeRule.onNodeWithText("Display").assertIsDisplayed()
    }

    @Test
    fun `switching to page by page keeps the page and swiping moves through the document`() {
        val reported = showConnected(ready(currentPage = 1))
        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Page by page").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Page by page").assertIsSelected()
        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()

        swipeDocument(page = 2)

        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
        assertThat(reported.last()).isEqualTo(2)
    }

    @Test
    fun `switching back to continuous scrolling on the last page stays on it`() {
        showConnected(ready(scrollMode = ScrollMode.PAGED, currentPage = 2))
        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Continuous").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Document, page 3 of 3").assertIsDisplayed()
    }

    @Test
    fun `page by page, the indicator follows the swipes and stops at the last page`() {
        val reported = showConnected(ready(scrollMode = ScrollMode.PAGED))

        swipeDocument(page = 1)
        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()
        swipeDocument(page = 2)
        swipeDocument(page = 3)

        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
        assertThat(reported).containsExactly(0, 1, 2).inOrder()
        swipeDocument(page = 3, left = false)
        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()
    }

    @Test
    fun `page by page, a one-page document has no slider and does not move`() {
        showConnected(ready(scrollMode = ScrollMode.PAGED, doc = FakePdfDocument(listOf(A4))))

        swipeDocument(page = 1, pageCount = 1)

        composeRule.onNodeWithText("1 / 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Go to page").assertDoesNotExist()
    }

    @Test
    fun `page by page, a tap switches to full screen and the fit button fits the whole page`() {
        show(ready(scrollMode = ScrollMode.PAGED))
        composeRule.onNodeWithContentDescription("Fit page to screen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Fit to width").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Document, page 1 of 3").performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MS)

        composeRule.onNodeWithText("Page 1 / 3").assertIsDisplayed()
        composeRule.onNodeWithText("Contrat de location").assertDoesNotExist()
    }

    @Test
    fun `the error screen has no more options button`() {
        show(ReaderUiState.Error(ReaderError.UNREADABLE))

        composeRule.onNodeWithContentDescription("More options").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `display options are translated into French`() {
        show(ready())

        composeRule.onNodeWithContentDescription("Plus d’options").performClick()

        composeRule.onNodeWithText("Affichage").assertIsDisplayed()
        composeRule.onNodeWithText("Défilement").assertIsDisplayed()
        composeRule.onNodeWithText("Continu").assertIsSelected()
        composeRule.onNodeWithText("Page par page").assertIsDisplayed()
    }

    @Test
    fun `the error screen of a recent document offers to remove it`() {
        var removed = 0
        var back = 0
        show(
            ReaderUiState.Error(ReaderError.NOT_ACCESSIBLE, inRecents = true),
            onBack = { back++ },
            onRemoveFromRecents = { removed++ },
        )

        composeRule.onNodeWithText("Remove from recents").assertIsDisplayed().performClick()

        assertThat(removed).isEqualTo(1)
        assertThat(back).isEqualTo(0)
        composeRule.onNodeWithText("Back").assertIsDisplayed()
    }

    @Test
    fun `the error screen of a document out of the recent list has no remove button`() {
        show(ReaderUiState.Error(ReaderError.NOT_ACCESSIBLE))

        composeRule.onNodeWithText("Remove from recents").assertDoesNotExist()
        composeRule.onNodeWithText("Back").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the remove button is translated into French`() {
        show(ReaderUiState.Error(ReaderError.UNREADABLE, inRecents = true))

        composeRule.onNodeWithText("Retirer des récents").assertIsDisplayed()
    }

    private companion object {
        const val DOUBLE_TAP_WAIT_MS = 500L
    }
}
