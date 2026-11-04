// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var backClicks = 0

    private fun show(state: ReaderUiState) {
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                ReaderScreen(state = state, onBack = { backClicks++ }, loadPage = { _, _ -> null })
            }
        }
    }

    @Test
    fun `loading shows the title and a progress indicator`() {
        show(ReaderUiState.Loading("contrat.pdf"))

        composeRule.onNodeWithText("contrat.pdf").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Opening the document…").assertIsDisplayed()
    }

    @Test
    fun `unreadable document shows the error and goes back to the library`() {
        show(ReaderUiState.Error("scan_facture.pdf", ReaderError.UNREADABLE))

        composeRule.onNodeWithText("Can’t open this document").assertIsDisplayed()
        composeRule.onNodeWithText("Back to library").performClick()
        assertThat(backClicks).isEqualTo(1)
    }

    @Test
    fun `inaccessible and protected documents explain why`() {
        show(ReaderUiState.Error("a.pdf", ReaderError.NOT_ACCESSIBLE))
        composeRule.onNodeWithText("Document unavailable").assertIsDisplayed()
    }

    @Test
    fun `protected document explains that passwords come later`() {
        show(ReaderUiState.Error("a.pdf", ReaderError.PASSWORD_PROTECTED))
        composeRule.onNodeWithText("Protected document").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `error is translated in French`() {
        show(ReaderUiState.Error("a.pdf", ReaderError.UNREADABLE))
        composeRule.onNodeWithText("Impossible d’ouvrir ce document").assertIsDisplayed()
        composeRule.onNodeWithText("Retour à la bibliothèque").assertIsDisplayed()
    }

    @Test
    fun `content shows the pages, the page indicator and the back action`() {
        show(content(pageCount = 8))

        composeRule.onNodeWithText("1 / 8").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Page 1 of 8").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Fit to width").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertThat(backClicks).isEqualTo(1)
    }

    @Test
    fun `a single page document has no slider`() {
        show(content(pageCount = 1))

        composeRule.onNodeWithText("1 / 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Page").assertDoesNotExist()
    }

    @Test
    fun `a tap on the page hides the bars and a second tap shows them`() {
        show(content(pageCount = 3))

        composeRule.onNodeWithContentDescription("Page 1 of 3").performTouchInput { click() }
        composeRule.mainClock.advanceTimeBy(TAP_TIMEOUT_MS)
        composeRule.onNodeWithText("1 / 3").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Page 1 of 3").performTouchInput { click() }
        composeRule.mainClock.advanceTimeBy(TAP_TIMEOUT_MS)
        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
    }

    private fun content(pageCount: Int) = ReaderUiState.Content("contrat.pdf", List(pageCount) { PageSize(595f, 842f) })

    private companion object {
        /** Longer than the double-tap timeout, so that a click counts as a single tap. */
        const val TAP_TIMEOUT_MS = 1_000L
    }
}
