// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
// Real bitmaps for the thumbnails.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var openClicks = 0
    private var settingsClicks = 0
    private val openedDocuments = mutableListOf<String>()
    private val outdatedThumbnails = mutableListOf<String>()

    private fun show(
        state: LibraryUiState = LibraryUiState.Empty,
        loadThumbnail: suspend (String) -> ImageBitmap? = {
            null
        },
    ) {
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                LibraryScreen(
                    state = state,
                    onOpenFile = { openClicks++ },
                    onOpenSettings = { settingsClicks++ },
                    onOpenDocument = { openedDocuments += it },
                    loadThumbnail = loadThumbnail,
                    onRefreshThumbnail = { outdatedThumbnails += it },
                )
            }
        }
    }

    private val contract =
        RecentDocumentItem(
            documentId = "contract",
            uri = "content://com.example.files/document/contract",
            name = "Contrat de location",
            pageCount = 8,
            lastPage = 2,
            openedOn = OpenedOn.Today(LocalTime.of(20, 41)),
            thumbnailPath = "/thumbnails/contract.png",
        )

    private val invoice =
        contract.copy(
            documentId = "invoice",
            uri = "content://com.example.files/document/invoice",
            name = "Facture octobre 2026",
            pageCount = 2,
            lastPage = 0,
            openedOn = OpenedOn.Yesterday,
            thumbnailPath = null,
            thumbnailOutdated = true,
        )

    @Test
    fun `empty library explains how to open a document`() {
        show()

        composeRule.onNodeWithText("Lutrin").assertIsDisplayed()
        composeRule.onNodeWithText("No recent documents").assertIsDisplayed()
        composeRule.onNodeWithText("Works fully offline. No data collected.").assertIsDisplayed()
    }

    @Test
    fun `open button asks for a file`() {
        show()

        composeRule.onNodeWithText("Open a file").assertHasClickAction().performClick()

        assertThat(openClicks).isEqualTo(1)
    }

    @Test
    fun `settings action opens the settings`() {
        show()

        composeRule.onNodeWithContentDescription("Settings").performClick()

        assertThat(settingsClicks).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `empty library is translated in French`() {
        show()

        composeRule.onNodeWithText("Aucun document récent").assertIsDisplayed()
        composeRule.onNodeWithText("Ouvrir un fichier").assertIsDisplayed()
    }

    @Test
    fun `loading shows neither the empty state nor a list`() {
        show(LibraryUiState.Loading)

        composeRule.onNodeWithContentDescription("Loading recent documents").assertExists()
        composeRule.onNodeWithText("No recent documents").assertDoesNotExist()
        composeRule.onNodeWithText("Recent").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun `an unreadable list explains it and still opens files`() {
        show(LibraryUiState.Error)

        composeRule.onNodeWithText("Recent documents unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Open a file").performClick()

        assertThat(openClicks).isEqualTo(1)
    }

    @Test
    fun `lists the recent documents with their page and date`() {
        show(LibraryUiState.Content(listOf(contract, invoice)))

        composeRule.onNodeWithText("Recent").assertIsDisplayed()
        composeRule.onNodeWithText("2 documents").assertIsDisplayed()
        composeRule.onNodeWithText("Contrat de location").assertIsDisplayed()
        composeRule.onNodeWithText("Page 3 / 8 · Today, 8:41 PM").assertIsDisplayed()
        composeRule.onNodeWithText("Facture octobre 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Page 1 / 2 · Yesterday").assertIsDisplayed()
        composeRule.onNodeWithText("No recent documents").assertDoesNotExist()
    }

    @Test
    fun `a single document is counted in the singular`() {
        show(LibraryUiState.Content(listOf(contract)))

        composeRule.onNodeWithText("1 document").assertIsDisplayed()
    }

    @Test
    fun `older dates are written without the time, and with the year when it is another one`() {
        val older = contract.copy(documentId = "a", openedOn = OpenedOn.ThisYear(LocalDate.of(2026, 10, 28)))
        val oldest = contract.copy(documentId = "b", openedOn = OpenedOn.OtherYear(LocalDate.of(2025, 3, 2)))

        show(LibraryUiState.Content(listOf(older, oldest)))

        composeRule.onNodeWithText("Page 3 / 8 · Oct 28").assertIsDisplayed()
        composeRule.onNodeWithText("Page 3 / 8 · Mar 2, 2025").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `recent documents are written in French`() {
        val older = invoice.copy(documentId = "older", openedOn = OpenedOn.ThisYear(LocalDate.of(2026, 10, 28)))

        show(LibraryUiState.Content(listOf(contract, invoice, older)))

        composeRule.onNodeWithText("Récents").assertIsDisplayed()
        composeRule.onNodeWithText("3 documents").assertIsDisplayed()
        composeRule.onNodeWithText("Page 3 / 8 · Aujourd’hui, 20:41").assertIsDisplayed()
        composeRule.onNodeWithText("Page 1 / 2 · Hier").assertIsDisplayed()
        composeRule.onNodeWithText("Page 1 / 2 · 28 oct.").assertIsDisplayed()
    }

    @Test
    fun `tapping a recent document opens it`() {
        show(LibraryUiState.Content(listOf(contract, invoice)))

        composeRule.onNodeWithText("Facture octobre 2026").performClick()

        assertThat(openedDocuments).containsExactly(invoice.uri)
    }

    @Test
    fun `the floating button still opens a file`() {
        show(LibraryUiState.Content(listOf(contract)))

        composeRule.onNode(hasClickAction() and hasText("Open a file")).performClick()

        assertThat(openClicks).isEqualTo(1)
    }

    @Test
    fun `thumbnails are described and loaded from their file`() {
        val requested = mutableListOf<String>()

        show(LibraryUiState.Content(listOf(contract))) { path -> ImageBitmap(44, 58).also { requested += path } }

        composeRule.onNodeWithContentDescription("First page of Contrat de location", useUnmergedTree = true)
            .assertExists()
        assertThat(requested).containsExactly(contract.thumbnailPath)
        assertThat(outdatedThumbnails).isEmpty()
    }

    @Test
    fun `an outdated thumbnail is still shown while it is drawn again`() {
        val requested = mutableListOf<String>()
        val outdated = contract.copy(thumbnailOutdated = true)

        show(LibraryUiState.Content(listOf(outdated))) { path -> ImageBitmap(44, 58).also { requested += path } }
        composeRule.waitForIdle()

        assertThat(requested).containsExactly(contract.thumbnailPath)
        assertThat(outdatedThumbnails).containsExactly(contract.documentId)
    }

    @Test
    fun `a row without thumbnail asks for it and shows a placeholder`() {
        show(LibraryUiState.Content(listOf(invoice)))

        composeRule.waitForIdle()

        assertThat(outdatedThumbnails).containsExactly(invoice.documentId)
        composeRule.onNodeWithContentDescription("First page of Facture octobre 2026", useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
