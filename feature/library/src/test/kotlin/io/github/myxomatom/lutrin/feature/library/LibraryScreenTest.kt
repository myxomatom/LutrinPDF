// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import java.time.Instant
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
    private val removed = mutableListOf<String>()
    private val undone = mutableListOf<PendingRemoval>()
    private val confirmed = mutableListOf<PendingRemoval>()

    private fun show(
        state: LibraryUiState = LibraryUiState.Empty,
        pendingRemoval: PendingRemoval? = null,
        loadThumbnail: suspend (String, IntSize) -> ImageBitmap? = { _, _ ->
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
                    pendingRemoval = pendingRemoval,
                    onRemove = { removed += it },
                    onUndoRemoval = { undone += it },
                    onConfirmRemoval = { confirmed += it },
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
            isReadingInProgress = true,
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

    /** Opened, but not in progress: read to its last page. */
    private val statement =
        contract.copy(
            documentId = "statement",
            uri = "content://com.example.files/document/statement",
            name = "Relevé de compte",
            pageCount = 4,
            lastPage = 3,
            openedOn = OpenedOn.Yesterday,
            isReadingInProgress = false,
        )

    /** A single page: never in progress. */
    private val ticket =
        contract.copy(
            documentId = "ticket",
            uri = "content://com.example.files/document/ticket",
            name = "Billet de train Lyon–Paris",
            pageCount = 1,
            lastPage = 0,
            openedOn = OpenedOn.ThisYear(LocalDate.of(2026, 10, 19)),
            isReadingInProgress = false,
        )

    private fun content(resume: RecentDocumentItem, vararg others: RecentDocumentItem) =
        LibraryUiState.Content(resume, others.toList())

    private fun progressBars() =
        composeRule.onAllNodesWithTag(READING_PROGRESS_TAG, useUnmergedTree = true).fetchSemanticsNodes()

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
        composeRule.onNodeWithText("RESUME READING", substring = true).assertDoesNotExist()
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
    fun `the most recent document is in the resume card, the others in the list`() {
        show(content(contract, invoice))

        composeRule.onNode(hasClickAction() and hasContentDescription(RESUME_CONTRACT))
            .assertIsDisplayed()
            .assert(hasText("RESUME READING"))
            .assert(hasText("Contrat de location"))
            // A narrow no-break space before PM, as the English time format writes it.
            .assert(hasText("Page 3 of 8 · today, 8:41\u202FPM"))
        composeRule.onNodeWithText("Recent").assertIsDisplayed()
        composeRule.onNodeWithText("1 other document").assertIsDisplayed()
        composeRule.onNodeWithText("Facture octobre 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Page 1 of 2 · yesterday").assertIsDisplayed()
        composeRule.onNodeWithText("No recent documents").assertDoesNotExist()
    }

    @Test
    fun `a single document shows the card without the recent section`() {
        show(content(contract))

        composeRule.onNodeWithContentDescription(RESUME_CONTRACT).assertIsDisplayed()
        composeRule.onNodeWithText("Recent").assertDoesNotExist()
        composeRule.onNodeWithText("other document", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the other documents are counted in the plural`() {
        show(content(contract, invoice, statement))

        composeRule.onNodeWithText("2 other documents").assertIsDisplayed()
    }

    @Test
    fun `older dates are written without the time, and with the year when it is another one`() {
        val older = contract.copy(documentId = "a", openedOn = OpenedOn.ThisYear(LocalDate.of(2026, 10, 28)))
        val oldest = contract.copy(documentId = "b", openedOn = OpenedOn.OtherYear(LocalDate.of(2025, 3, 2)))

        show(content(invoice, older, oldest))

        composeRule.onNodeWithText("Page 3 of 8 · Oct 28").assertIsDisplayed()
        composeRule.onNodeWithText("Page 3 of 8 · Mar 2, 2025").assertIsDisplayed()
    }

    @Test
    fun `a document not in progress shows its number of pages and no progress bar`() {
        show(content(statement, ticket, statement.copy(documentId = "other", name = "Autre relevé")))

        composeRule.onNodeWithContentDescription("Resume reading Relevé de compte, 4 pages").assertIsDisplayed()
        composeRule.onNodeWithText("1 page · Oct 19").assertIsDisplayed()
        composeRule.onAllNodesWithText("4 pages · yesterday").assertCountEquals(2)
        assertThat(progressBars()).isEmpty()
    }

    @Test
    fun `only the readings in progress have a progress bar, in the card and in the list`() {
        show(content(contract, statement, invoice, ticket))

        // The card (page 3 of 8) and the invoice (page 1 of 2), not the statement nor the ticket.
        assertThat(progressBars()).hasSize(2)
    }

    @Test
    fun `a resume card of a single page is described with its number of pages`() {
        show(content(ticket))

        composeRule.onNodeWithContentDescription("Resume reading Billet de train Lyon–Paris, 1 page")
            .assertHasClickAction()
            .assert(hasText("1 page · Oct 19"))
        assertThat(progressBars()).isEmpty()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `recent documents are written in French`() {
        val older = invoice.copy(documentId = "older", openedOn = OpenedOn.ThisYear(LocalDate.of(2026, 10, 28)))

        show(content(contract, invoice, older))

        composeRule.onNodeWithContentDescription("Reprendre la lecture\u00A0: Contrat de location, page 3 sur 8")
            .assert(hasText("REPRENDRE LA LECTURE"))
            .assert(hasText("Page 3 sur 8 · aujourd’hui, 20:41"))
            .assert(hasText("Continuer"))
        composeRule.onNodeWithText("Récents").assertIsDisplayed()
        composeRule.onNodeWithText("2 autres documents").assertIsDisplayed()
        composeRule.onNodeWithText("Page 1 sur 2 · hier").assertIsDisplayed()
        composeRule.onNodeWithText("Page 1 sur 2 · 28 oct.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `a single other document is counted in the singular in French`() {
        show(content(ticket, statement))

        composeRule.onNodeWithText("1 autre document").assertIsDisplayed()
        composeRule.onNodeWithText("4 pages · hier").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reprendre la lecture\u00A0: Billet de train Lyon–Paris, 1 page")
            .assert(hasText("1 page · 19 oct."))
    }

    @Test
    fun `tapping a recent document opens it`() {
        show(content(contract, invoice))

        composeRule.onNodeWithText("Facture octobre 2026").performClick()

        assertThat(openedDocuments).containsExactly(invoice.uri)
    }

    @Test
    fun `tapping the resume card opens its document`() {
        show(content(contract, invoice))

        composeRule.onNodeWithContentDescription(RESUME_CONTRACT).performClick()

        assertThat(openedDocuments).containsExactly(contract.uri)
    }

    @Test
    fun `continue is part of the card, not a second target`() {
        show(content(contract))

        // The node holding « Continue » is the card itself.
        composeRule.onNodeWithText("Continue").assert(hasContentDescription(RESUME_CONTRACT)).performClick()
        composeRule.onAllNodes(hasText("Continue") and hasClickAction()).assertCountEquals(1)

        assertThat(openedDocuments).containsExactly(contract.uri)
    }

    @Test
    fun `the resume card is a large target`() {
        show(content(contract))

        composeRule.onNodeWithContentDescription(RESUME_CONTRACT).assertHeightIsAtLeast(CARD_MIN_HEIGHT)
    }

    @Test
    fun `the floating button still opens a file`() {
        show(content(contract))

        composeRule.onNode(hasClickAction() and hasText("Open a file")).performClick()

        assertThat(openClicks).isEqualTo(1)
    }

    @Test
    fun `thumbnails are described and loaded from their file at the size they are shown`() {
        val requested = mutableListOf<Pair<String, IntSize>>()
        val other = invoice.copy(thumbnailPath = "/thumbnails/invoice.png", thumbnailOutdated = false)

        show(content(contract, other)) { path, size -> ImageBitmap(52, 68).also { requested += path to size } }

        composeRule.onNodeWithContentDescription("First page of Facture octobre 2026", useUnmergedTree = true)
            .assertExists()
        // Robolectric's default density is 1: 104 x 138 dp and 52 x 68 dp.
        assertThat(requested)
            .containsExactly(contract.thumbnailPath to IntSize(104, 138), other.thumbnailPath to IntSize(52, 68))
        assertThat(outdatedThumbnails).isEmpty()
    }

    @Test
    fun `the picture of the card is said by the card only`() {
        show(content(contract)) { _, _ -> ImageBitmap(104, 138) }

        // Only the description of the card: neither « First page of … » nor the lock merge into it.
        composeRule.onNodeWithContentDescription(RESUME_CONTRACT)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(RESUME_CONTRACT)))
        composeRule.onNodeWithContentDescription("First page of Contrat de location").assertDoesNotExist()
    }

    @Test
    fun `an outdated thumbnail is still shown while it is drawn again`() {
        val requested = mutableListOf<String>()
        val outdated = contract.copy(thumbnailOutdated = true)

        show(content(outdated)) { path, _ -> ImageBitmap(44, 58).also { requested += path } }
        composeRule.waitForIdle()

        assertThat(requested).containsExactly(contract.thumbnailPath)
        assertThat(outdatedThumbnails).containsExactly(contract.documentId)
    }

    @Test
    fun `a protected document shows a lock and never asks for its thumbnail`() {
        // Even marked outdated, as by a stale state: the lock wins.
        val protected = invoice.copy(documentId = "statement", name = "Relevé de compte", isProtected = true)

        show(content(contract.copy(thumbnailPath = null, thumbnailOutdated = false), protected))
        composeRule.waitForIdle()

        assertThat(outdatedThumbnails).isEmpty()
        composeRule.onNodeWithContentDescription("Protected document", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("First page of Relevé de compte", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `the lock is read with the row`() {
        show(content(contract, invoice.copy(isProtected = true, thumbnailPath = null)))

        composeRule.onNode(hasClickAction() and hasContentDescription("Protected document")).assertExists()
    }

    @Test
    fun `a protected document most recently opened takes the card, with its lock in its description`() {
        val protected = contract.copy(isProtected = true, thumbnailPath = null, thumbnailOutdated = true)

        show(content(protected, invoice))
        composeRule.waitForIdle()

        composeRule.onNode(hasClickAction() and hasContentDescription("$RESUME_CONTRACT, protected document"))
            .assertIsDisplayed()
            .performClick()
        assertThat(openedDocuments).containsExactly(contract.uri)
        // Asked for the invoice only: a protected document is never drawn.
        assertThat(outdatedThumbnails).containsExactly(invoice.documentId)
    }

    @Test
    fun `a document without lock has none`() {
        show(content(contract, invoice))

        composeRule.onNodeWithContentDescription("Protected document", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("protected document", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the lock is described in French`() {
        show(content(contract.copy(isProtected = true, thumbnailPath = null), invoice.copy(isProtected = true)))

        composeRule.onNode(hasClickAction() and hasContentDescription("Document protégé")).assertExists()
        composeRule.onNodeWithContentDescription(
            "Reprendre la lecture\u00A0: Contrat de location, page 3 sur 8, document protégé",
        ).assertExists()
    }

    @Test
    fun `a row without thumbnail asks for it and shows a placeholder`() {
        show(content(contract, invoice))

        composeRule.waitForIdle()

        assertThat(outdatedThumbnails).containsExactly(invoice.documentId)
        composeRule.onNodeWithContentDescription("First page of Facture octobre 2026", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `a very long title stays within the card`() {
        val long = contract.copy(
            name = "Contrat de location meublée — annexes, état des lieux et inventaire ".repeat(3),
        )

        show(content(long, invoice))

        composeRule.onNodeWithText("Continue").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More actions for ${long.name}").assertIsDisplayed()
        composeRule.onNodeWithText("Facture octobre 2026").assertIsDisplayed()
    }

    @Test
    fun `each row has a menu button named after its document, with a 48 dp touch target`() {
        show(content(contract, invoice))

        composeRule.onNodeWithContentDescription("More actions for Contrat de location")
            .assertHasClickAction()
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
        composeRule.onNodeWithContentDescription("More actions for Facture octobre 2026").assertIsDisplayed()
    }

    @Test
    fun `the row menu removes the document from the recents`() {
        show(content(contract, invoice))

        composeRule.onNodeWithContentDescription("More actions for Facture octobre 2026").performClick()
        composeRule.onNodeWithText("Remove from recents").assertIsDisplayed().performClick()

        assertThat(removed).containsExactly(invoice.documentId)
        assertThat(openedDocuments).isEmpty()
        composeRule.onNodeWithText("Remove from recents").assertDoesNotExist()
    }

    @Test
    fun `the menu of the resume card removes its document`() {
        show(content(contract, invoice))

        composeRule.onNodeWithContentDescription("More actions for Contrat de location")
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
            .performClick()
        composeRule.onNodeWithText("Remove from recents").performClick()

        assertThat(removed).containsExactly(contract.documentId)
        assertThat(openedDocuments).isEmpty()
    }

    @Test
    fun `opening the row menu does not open the document`() {
        show(content(contract))

        composeRule.onNodeWithContentDescription("More actions for Contrat de location").performClick()

        assertThat(openedDocuments).isEmpty()
        assertThat(removed).isEmpty()
    }

    @Test
    fun `a pending removal offers to undo it`() {
        show(content(invoice), pendingRemoval = removal)

        composeRule.onNodeWithText("“Contrat de location” removed from recents").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertThat(undone).containsExactly(removal)
        assertThat(confirmed).isEmpty()
    }

    @Test
    fun `a removal not undone is confirmed when its message times out`() {
        show(LibraryUiState.Empty, pendingRemoval = removal)
        composeRule.onNodeWithText("“Contrat de location” removed from recents").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(MESSAGE_TIMEOUT_MS)
        composeRule.waitForIdle()

        assertThat(confirmed).containsExactly(removal)
        assertThat(undone).isEmpty()
        composeRule.onNodeWithText("“Contrat de location” removed from recents").assertDoesNotExist()
    }

    @Test
    fun `without pending removal no message is shown`() {
        show(content(contract))

        composeRule.onNodeWithText("Undo").assertDoesNotExist()
        assertThat(confirmed).isEmpty()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the row menu and the undo message are translated in French`() {
        show(content(contract), pendingRemoval = removal.copy(name = "Facture"))

        composeRule.onNodeWithText("« Facture » retiré des récents").assertIsDisplayed()
        composeRule.onNodeWithText("Annuler").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Plus d’actions pour Contrat de location").performClick()
        composeRule.onNodeWithText("Retirer des récents").assertIsDisplayed()
    }

    private val removal = PendingRemoval(contract.documentId, contract.name, Instant.parse("2026-11-09T20:41:00Z"))

    private companion object {
        /** Longer than a long snackbar (10 s). */
        const val MESSAGE_TIMEOUT_MS = 11_000L

        /** Description of the resume card of [contract]. */
        const val RESUME_CONTRACT = "Resume reading Contrat de location, page 3 of 8"

        /** The card holds its 138 dp page and 16 dp of padding above and below. */
        val CARD_MIN_HEIGHT = 170.dp
    }
}
