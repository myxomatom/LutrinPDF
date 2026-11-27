// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.myxomatom.lutrin.core.domain.document.DocumentProperties
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchExcerpt
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SearchPageResult
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.assertControlsAreAccessible
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderError
import io.github.myxomatom.lutrin.feature.reader.ReaderOutline
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.flattenOutline
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import io.github.myxomatom.lutrin.feature.reader.search.HitRef
import io.github.myxomatom.lutrin.feature.reader.search.SearchResults
import io.github.myxomatom.lutrin.feature.reader.search.SearchState
import io.github.myxomatom.lutrin.feature.reader.search.SearchStatus
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The reader (E2) and its screens (V1, V2, V4 to V8) against the accessibility rules of `AGENTS.md`
 * §11 (F18): every control has a name for TalkBack and a touch target of 48 dp, also at 200 %.
 * The checks of the Accessibility Test Framework run on a device (`app/src/androidTest`); this
 * test runs on every `check` and measures every state of the reader.
 */
@RunWith(AndroidJUnit4::class)
// Tall, so that the sheets show every row: the audit measures the controls where they are.
@Config(qualifiers = "w360dp-h1600dp-xhdpi")
class ReaderAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val document = FakePdfDocument(List(PAGES) { A4 })

    private fun ready(scrollMode: ScrollMode = ScrollMode.CONTINUOUS, outline: ReaderOutline = ReaderOutline()) =
        ReaderUiState.Ready(
            title = "Contrat de location",
            pageSizes = document.pageSizes,
            renderer = PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
            scrollMode = scrollMode,
            currentPage = CURRENT_PAGE,
            outline = outline,
        )

    private fun showContent(content: @Composable () -> Unit) {
        composeRule.setContent { LutrinTheme(dynamicColor = false) { content() } }
        composeRule.waitForIdle()
    }

    private fun show(
        state: ReaderUiState = ready(),
        search: ReaderSearch = ReaderSearch(),
        selection: ReaderSelection = ReaderSelection(),
        links: ReaderLinks = ReaderLinks(),
        properties: ReaderProperties = ReaderProperties(),
    ) = showContent {
        ReaderScreen(state, onBack = {}, search = search, selection = selection, links = links, properties = properties)
    }

    private fun openMenu(entry: String? = null) {
        composeRule.onNodeWithContentDescription("More options").performClick()
        entry?.let { composeRule.onNodeWithText(it).performClick() }
        composeRule.waitForIdle()
    }

    @Test
    fun `the reader has named controls of 48 dp`() {
        show()

        composeRule.assertControlsAreAccessible()
    }

    @Test
    @Config(fontScale = 2f)
    fun `the reader stays accessible at 200 percent`() {
        show()

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the reader page by page is accessible`() {
        show(ready(ScrollMode.PAGED))

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the menu of the reader is accessible`() {
        show()
        openMenu()

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the display options are accessible`() {
        show()
        openMenu("Display")

        composeRule.onNodeWithText("Document colours").assertExists()
        composeRule.assertControlsAreAccessible()
    }

    @Test
    @Config(fontScale = 2f)
    fun `the display options are accessible at 200 percent`() {
        show()
        openMenu("Display")

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the properties are accessible`() {
        val properties = DocumentProperties(
            fileName = "contrat_location_2026.pdf",
            title = "Contrat de location",
            author = "Alice Martin",
            pageCount = PAGES,
            sizeBytes = 421_888,
            pdfVersion = "1.7",
            createdAt = Instant.parse("2026-09-12T10:14:00Z"),
            modifiedAt = Instant.parse("2026-09-14T16:02:00Z"),
        )
        show(properties = ReaderProperties(value = properties))

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the search bar with results is accessible`() {
        val hits = listOf(150f, 240f).map { top ->
            SearchHit(
                0,
                listOf(PdfQuad.of(PdfRect(120f, top, 230f, top + 16f))),
                SearchExcerpt("le preneur peut demander la ", "résiliation", " avec un préavis de"),
            )
        }
        val results = (1 until PAGES).fold(SearchResults.Empty + SearchPageResult(0, hits)) { all, page ->
            all + SearchPageResult(page, emptyList())
        }
        val search = SearchState(
            isOpen = true,
            query = "résiliation",
            results = results,
            current = HitRef(0, 1),
            status = SearchStatus.Done,
            pageCount = PAGES,
        )
        show(search = ReaderSearch(state = search))

        composeRule.onNode(hasSetTextAction()).assertExists()
        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the search bar without result is accessible`() {
        val search = SearchState(isOpen = true, query = "xyz", status = SearchStatus.Done, pageCount = PAGES)
        show(search = ReaderSearch(state = search))

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the table of contents is accessible`() {
        val entries = listOf(
            OutlineItem("1. Objet du contrat", PdfDestination(0), null, emptyList()),
            OutlineItem(
                "2. Durée du bail",
                PdfDestination(1),
                null,
                listOf(OutlineItem("2.1 Date de prise d’effet", PdfDestination(1), null, emptyList())),
            ),
            OutlineItem("Informations en ligne", null, "https://example.org/bail", emptyList()),
        )
        val state = ready(outline = ReaderOutline(flattenOutline(entries, PAGES), expanded = setOf(1)))
        showContent {
            OutlineDrawer(
                drawerState = rememberDrawerState(DrawerValue.Open),
                outline = state.outline,
                documentTitle = state.title,
                pageCount = state.pageSizes.size,
                onToggle = {},
                onEntryClick = {},
                onClose = {},
            ) {
                ReaderScreen(state, onBack = {})
            }
        }

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the selection with its handles and its actions is accessible`() {
        val selection = TextSelection(
            0,
            listOf(PdfQuad.of(PdfRect(180f, 300f, 520f, 316f)), PdfQuad.of(PdfRect(72f, 320f, 300f, 336f))),
            "bail\nde location",
            PdfPoint(180f, 316f),
            PdfPoint(300f, 336f),
        )
        show(selection = ReaderSelection(selection = selection, canCopy = true))

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the dialog to go to a page is accessible`() {
        show()
        composeRule.onNodeWithContentDescription("Page ${CURRENT_PAGE + 1} of $PAGES").performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction()).assertExists()
        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the dialog of an external link is accessible`() {
        show(links = ReaderLinks(externalLink = "https://exemple.org/conditions-generales"))

        composeRule.onNodeWithText("Open an external link?").assertExists()
        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the password dialog is accessible`() {
        show(ReaderUiState.PasswordRequired("Relevé de compte"))

        composeRule.onNode(hasSetTextAction()).assertExists()
        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the password dialog after a wrong password is accessible`() {
        show(ReaderUiState.PasswordRequired("Relevé de compte", isIncorrect = true))

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the error of a document is accessible`() {
        show(ReaderUiState.Error(ReaderError.UNREADABLE))

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the error of a recent document, with its removal, is accessible`() {
        show(ReaderUiState.Error(ReaderError.NOT_ACCESSIBLE, inRecents = true))

        composeRule.assertControlsAreAccessible()
    }

    private companion object {
        const val PAGES = 8
        const val CURRENT_PAGE = 4
    }
}
