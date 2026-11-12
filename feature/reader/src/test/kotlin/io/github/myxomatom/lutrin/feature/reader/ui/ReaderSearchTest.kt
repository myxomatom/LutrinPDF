// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchExcerpt
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import io.github.myxomatom.lutrin.feature.reader.search.SearchController
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The search in the reader (F07, V1, ADR 0023), wired to a real [SearchController]. */
@RunWith(AndroidJUnit4::class)
class ReaderSearchTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val document = FakePdfDocument(List(PAGE_COUNT) { A4 })

    private fun hit(page: Int, top: Float, before: String = "le preneur peut demander la ") = SearchHit(
        page,
        listOf(PdfQuad.of(PdfRect(72f, top, 160f, top + 14f))),
        SearchExcerpt(before, "résiliation", " avec un préavis de"),
    )

    private fun givenHits(vararg hits: SearchHit) {
        document.searchHits = { query -> if (query == QUERY) hits.toList() else emptyList() }
    }

    private fun show(scrollMode: ScrollMode = ScrollMode.CONTINUOUS) {
        val state = ReaderUiState.Ready(
            "Contrat de location",
            document.pageSizes,
            PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
            scrollMode,
        )
        composeRule.setContent {
            // Runs on the clock of the test, like the scope of the ViewModel on the main thread.
            val scope = rememberCoroutineScope()
            val controller = remember { SearchController(scope, Dispatchers.Unconfined, { document }, { 0 }) }
            val searchState by controller.state.collectAsState()
            LutrinTheme(dynamicColor = false) {
                ReaderScreen(state, onBack = {}, search = readerSearch(controller, searchState))
            }
        }
    }

    /** Opens the search and types [query], then waits for the end of the typing. */
    private fun search(query: String = QUERY) {
        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(query)
        composeRule.mainClock.advanceTimeBy(SEARCH_WAIT_MS)
        composeRule.waitForIdle()
    }

    private fun counter() = composeRule.onNodeWithTag(SEARCH_COUNTER_TAG)

    @Test
    fun `typing searches the document and shows the count and the excerpt of the first result`() {
        givenHits(hit(1, 300f), hit(2, 100f))
        show()

        search()

        counter().assertTextEquals("1 / 2")
        composeRule.onNodeWithText("Page 2").assertIsDisplayed()
        composeRule.onNodeWithText("… le preneur peut demander la résiliation avec un préavis de …").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Document, page 2 of 3").assertIsDisplayed()
        // The search bar replaces the top bar, the excerpt the floating bar.
        composeRule.onNodeWithText("Contrat de location").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Page 1 of 3").assertDoesNotExist()
    }

    @Test
    fun `nothing is searched before the typing stops`() {
        givenHits(hit(1, 300f))
        show()
        composeRule.onNodeWithContentDescription("Search").performClick()

        composeRule.onNode(hasSetTextAction()).performTextInput(QUERY)
        composeRule.mainClock.advanceTimeBy(SEARCH_WAIT_MS / 2)
        composeRule.waitForIdle()

        assertThat(document.searches).isEmpty()
    }

    @Test
    fun `validating the field searches at once`() {
        givenHits(hit(1, 300f))
        show()
        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(QUERY)

        composeRule.onNode(hasSetTextAction()).performImeAction()
        composeRule.waitForIdle()

        counter().assertTextEquals("1 / 1")
    }

    @Test
    fun `next and previous move through the results and wrap around`() {
        givenHits(hit(0, 100f), hit(1, 300f), hit(2, 500f))
        show()
        search()
        counter().assertTextEquals("1 / 3")

        composeRule.onNodeWithContentDescription("Next result").performClick()
        counter().assertTextEquals("2 / 3")
        composeRule.onNodeWithText("Page 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next result").performClick()
        composeRule.onNodeWithContentDescription("Next result").performClick()
        counter().assertTextEquals("1 / 3")
        composeRule.onNodeWithContentDescription("Previous result").performClick()

        counter().assertTextEquals("3 / 3")
        composeRule.onNodeWithContentDescription("Document, page 3 of 3").assertIsDisplayed()
    }

    @Test
    fun `page by page, the current result changes the page`() {
        givenHits(hit(0, 100f), hit(2, 500f))
        show(ScrollMode.PAGED)
        search()
        composeRule.onNodeWithContentDescription("Document, page 1 of 3").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Next result").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Document, page 3 of 3").assertIsDisplayed()
        composeRule.onNodeWithText("Page 3").assertIsDisplayed()
    }

    @Test
    fun `back closes the search and brings the top bar back`() {
        givenHits(hit(1, 300f))
        show()
        search()
        // Hides the keyboard, which would take the first back.
        composeRule.onNode(hasSetTextAction()).performImeAction()

        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Contrat de location").assertIsDisplayed()
        composeRule.onNodeWithText("Page 2").assertDoesNotExist()
        counter().assertDoesNotExist()
    }

    @Test
    fun `the arrow closes the search and opening it again starts empty`() {
        givenHits(hit(1, 300f))
        show()
        search()

        composeRule.onNodeWithContentDescription("Close search").performClick()
        composeRule.onNodeWithContentDescription("Search").performClick()

        composeRule.onNodeWithText("Search in the document").assertIsDisplayed()
        counter().assertTextEquals("")
        composeRule.onNodeWithContentDescription("Next result").assertIsNotEnabled()
    }

    @Test
    fun `the options are off by default and search again when toggled`() {
        givenHits(hit(1, 300f))
        show()
        search()
        composeRule.onNodeWithText("Match case").assertIsNotSelected()
        composeRule.onNodeWithText("Match accents").assertIsNotSelected()

        composeRule.onNodeWithText("Match case").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Match case").assertIsSelected()
        assertThat(document.searches.map { it.options.matchCase }).containsExactly(false, true).inOrder()
    }

    @Test
    fun `a search without result says so, with a word about scanned documents`() {
        givenHits()
        show()

        search()

        composeRule.onNodeWithText("No results").assertIsDisplayed()
        composeRule.onNodeWithText("A scanned document without text cannot be searched.").assertIsDisplayed()
        counter().assertTextEquals("0 / 0")
        composeRule.onNodeWithContentDescription("Next result").assertIsNotEnabled()
    }

    @Test
    fun `an empty field clears the results`() {
        givenHits(hit(1, 300f))
        show()
        search()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("")
        composeRule.waitForIdle()

        counter().assertTextEquals("")
        composeRule.onNodeWithText("Page 2").assertDoesNotExist()
    }

    @Test
    fun `TalkBack announces the count once, then the result reached`() {
        givenHits(hit(0, 100f), hit(2, 500f))
        show()
        search()

        counter()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("2 results")))
        composeRule.onNodeWithContentDescription("Next result").performClick()

        counter().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Result 2 of 2, page 3")),
        )
    }

    @Test
    fun `the controls of the search bar are at least 48 dp`() {
        givenHits(hit(1, 300f))
        show()
        search()

        listOf("Close search", "Previous result", "Next result").forEach { label ->
            composeRule.onNodeWithContentDescription(label)
                .assertIsEnabled()
                .assertTouchWidthIsEqualTo(48.dp)
                .assertTouchHeightIsEqualTo(48.dp)
        }
        composeRule.onNodeWithText("Match case").assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    @Config(qualifiers = "+fr")
    fun `the search is translated into French`() {
        givenHits(hit(1, 300f), hit(2, 100f))
        show()
        composeRule.onNodeWithContentDescription("Rechercher").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(QUERY)
        composeRule.mainClock.advanceTimeBy(SEARCH_WAIT_MS)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Respecter la casse").assertIsDisplayed()
        composeRule.onNodeWithText("Respecter les accents").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Résultat suivant").assertIsDisplayed()
        counter().assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("2 résultats")))
    }

    private companion object {
        const val PAGE_COUNT = 3
        const val QUERY = "résiliation"

        /** Beyond the 400 ms of the debounce. */
        const val SEARCH_WAIT_MS = 500L
    }
}
