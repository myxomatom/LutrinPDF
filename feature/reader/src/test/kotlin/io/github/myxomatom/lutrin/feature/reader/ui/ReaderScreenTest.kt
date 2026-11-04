// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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

    private fun ready() = ReaderUiState.Ready(
        "Contrat de location",
        document.pageSizes,
        PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
    )

    private fun show(state: ReaderUiState, onBack: () -> Unit = {}) {
        composeRule.setContent { LutrinTheme(dynamicColor = false) { ReaderScreen(state, onBack) } }
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
                ReaderError.PASSWORD_REQUIRED to
                    "This document is protected by a password. Opening protected documents is coming soon.",
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
    fun `back arrow leaves the reader`() {
        var back = false
        show(ready(), onBack = { back = true })

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(back).isTrue()
    }

    private companion object {
        const val DOUBLE_TAP_WAIT_MS = 500L
    }
}
