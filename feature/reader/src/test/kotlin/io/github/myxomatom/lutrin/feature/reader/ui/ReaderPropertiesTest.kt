// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.DocumentProperties
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import io.github.myxomatom.lutrin.feature.reader.share.ShareOutcome
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The « More options » menu (V6, F15, F16) and the properties sheet (V5, ADR 0027): the items of
 * the menu, what the sheet shows, a missing value, and what « Partager » and « Imprimer » do.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp")
class ReaderPropertiesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val full =
        DocumentProperties(
            fileName = "contrat_location_2026.pdf",
            title = "Contrat de location",
            author = "A. Auteur",
            pageCount = 8,
            sizeBytes = 421_888,
            pdfVersion = "1.7",
            createdAt = Instant.parse("2026-09-12T08:14:00Z"),
            modifiedAt = Instant.parse("2026-09-14T14:02:00Z"),
        )
    private val bare = DocumentProperties(null, null, null, 1, null, null, null, null)

    private fun ready(canPrint: Boolean = true): ReaderUiState.Ready {
        val document = FakePdfDocument()
        return ReaderUiState.Ready(
            "Contrat de location",
            document.pageSizes,
            PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
            canPrint = canPrint,
        )
    }

    private fun show(
        state: ReaderUiState = ready(),
        properties: ReaderProperties = ReaderProperties(),
        onShare: () -> ShareOutcome = { ShareOutcome.STARTED },
        onPrint: () -> Boolean = { true },
    ) {
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                ReaderScreen(state, onBack = {}, onPrint = onPrint, onShare = onShare, properties = properties)
            }
        }
    }

    private fun showContent(properties: DocumentProperties, canPrint: Boolean = true, locale: Locale = Locale.US) {
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                ReaderPropertiesContent(
                    properties,
                    canPrint,
                    onShare = {},
                    onPrint = {},
                    locale = locale,
                    zone = ZoneId.of("UTC"),
                )
            }
        }
    }

    private fun openMenu() {
        composeRule.onNodeWithContentDescription("More options").performClick()
    }

    @Test
    fun `the more options menu offers the display options, sharing and the properties`() {
        show()

        openMenu()

        composeRule.onNodeWithText("Display").assertIsDisplayed()
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Properties").assertIsDisplayed()
    }

    @Test
    fun `the properties item asks for the properties and closes the menu`() {
        var asked = 0
        show(properties = ReaderProperties(onOpen = { asked++ }))

        openMenu()
        composeRule.onNodeWithText("Properties").performClick()

        assertThat(asked).isEqualTo(1)
        composeRule.onNodeWithText("Share").assertDoesNotExist()
    }

    @Test
    fun `the share item opens the chooser and says nothing when it opens`() {
        var shared = 0
        show(
            onShare = {
                shared++
                ShareOutcome.STARTED
            },
        )

        openMenu()
        composeRule.onNodeWithText("Share").performClick()

        assertThat(shared).isEqualTo(1)
        composeRule.onNodeWithText("No app can receive this file").assertDoesNotExist()
        composeRule.onNodeWithText("This file cannot be shared").assertDoesNotExist()
    }

    @Test
    fun `sharing says so when no app can receive the file`() {
        show(onShare = { ShareOutcome.NO_APP })

        openMenu()
        composeRule.onNodeWithText("Share").performClick()

        composeRule.onNodeWithText("No app can receive this file").assertIsDisplayed()
    }

    @Test
    fun `sharing says so when the file cannot be handed over`() {
        show(onShare = { ShareOutcome.NOT_SHAREABLE })

        openMenu()
        composeRule.onNodeWithText("Share").performClick()

        composeRule.onNodeWithText("This file cannot be shared").assertIsDisplayed()
    }

    @Test
    fun `the sheet is closed while there are no properties`() {
        show()

        composeRule.onNodeWithText("Created").assertDoesNotExist()
    }

    @Test
    fun `the sheet shows every property of the document`() {
        show(properties = ReaderProperties(value = full))

        composeRule.onNodeWithText("Properties").assertIsDisplayed()
        composeRule.onNodeWithText("contrat_location_2026.pdf").assertIsDisplayed()
        composeRule.onAllNodesWithText("Contrat de location").assertCountEquals(2) // top bar and row
        composeRule.onNodeWithText("A. Auteur").assertIsDisplayed()
        composeRule.onNodeWithText("8").assertIsDisplayed()
        composeRule.onNodeWithText("1.7").assertIsDisplayed()
        listOf("File", "Title", "Author", "Pages", "Size", "PDF version", "Created", "Modified").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun `a missing value reads not provided`() {
        showContent(bare)

        // File, title, author, size, version, created and modified are all unknown.
        composeRule.onAllNodesWithText("Not provided").assertCountEquals(7)
        composeRule.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun `dates follow the language and the time zone of the user`() {
        showContent(full, locale = Locale.FRANCE)

        composeRule.onNodeWithText("12 sept. 2026", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("14 sept. 2026", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the date is formatted in the zone and the language asked`() {
        val date = Instant.parse("2026-09-12T08:14:00Z")

        val paris = formatPropertyDate(date, Locale.FRANCE, ZoneId.of("Europe/Paris"))
        val utc = formatPropertyDate(date, Locale.FRANCE, ZoneId.of("UTC"))

        assertThat(paris).contains("10:14")
        assertThat(utc).contains("08:14")
        assertThat(paris).contains("12")
        assertThat(paris).contains("2026")
    }

    @Test
    fun `share and print are reachable from the sheet, and close it first`() {
        var shared = 0
        var printed = 0
        var dismissed = 0
        show(
            properties = ReaderProperties(value = full, onDismiss = { dismissed++ }),
            onShare = {
                shared++
                ShareOutcome.STARTED
            },
            onPrint = {
                printed++
                true
            },
        )

        composeRule.onNodeWithText("Share").assertHasClickAction()
        composeRule.onNodeWithText("Share").performClick()
        composeRule.onNodeWithText("Print").performClick()

        assertThat(shared).isEqualTo(1)
        assertThat(printed).isEqualTo(1)
        assertThat(dismissed).isEqualTo(2)
    }

    @Test
    fun `a forbidden print is explained and does not print`() {
        var printed = 0
        show(
            state = ready(canPrint = false),
            properties = ReaderProperties(value = full),
            onPrint = {
                printed++
                true
            },
        )

        composeRule.onNodeWithText("Print").performClick()

        assertThat(printed).isEqualTo(0)
        composeRule.onNodeWithText("The author of this document forbids printing it").assertIsDisplayed()
    }

    @Test
    fun `the action buttons are at least 48 dp high`() {
        showContent(full)

        composeRule.onNodeWithText("Share").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Print").assertHeightIsAtLeast(48.dp)
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp")
    fun `the sheet and the menu are translated into French`() {
        show(properties = ReaderProperties(value = bare))

        composeRule.onNodeWithText("Propriétés").assertIsDisplayed()
        composeRule.onNodeWithText("Fichier").assertIsDisplayed()
        composeRule.onNodeWithText("Version PDF").assertIsDisplayed()
        composeRule.onNodeWithText("Créé le").assertIsDisplayed()
        composeRule.onNodeWithText("Modifié le").assertIsDisplayed()
        composeRule.onNodeWithText("Partager").assertIsDisplayed()
        composeRule.onNodeWithText("Imprimer").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp")
    fun `the missing values and the menu read in French`() {
        showContent(bare)
        composeRule.onAllNodesWithText("Non renseigné").assertCountEquals(7)
    }
}
