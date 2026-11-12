// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderOutline
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.flattenOutline
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The table of contents (V2, F06) in the reader: the button of the top bar, the drawer, its
 * entries, and where they lead, in both scroll modes.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class OutlineDrawerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(
        title: String,
        page: Int?,
        y: Float? = null,
        uri: String? = null,
        kids: List<OutlineItem> = emptyList(),
    ) = OutlineItem(title, page?.let { PdfDestination(it, y = y) }, uri, kids)

    /** The table of the mock-up V2, read on page 3 (« 3. Loyer et charges »). */
    private val contract =
        listOf(
            entry("1. Objet du contrat", page = 0),
            entry(
                "2. Durée du bail",
                page = 1,
                kids = listOf(
                    entry("2.1 Date de prise d’effet", page = 1, y = 100f),
                    entry("2.2 Renouvellement", page = 1),
                ),
            ),
            entry("3. Loyer et charges", page = 2, kids = listOf(entry("3.1 Montant", page = 2))),
            entry("4. Résiliation", page = 4),
            entry("Annexes", page = 7),
        )

    private val document = FakePdfDocument(List(PAGES) { A4 })
    private val externals = mutableListOf<String>()
    private lateinit var current: () -> ReaderUiState.Ready

    /**
     * Shows the reader wired like the ViewModel: the page and the outline come back in the state.
     * [outline] is flattened here like the ViewModel does.
     */
    private fun show(
        outline: List<OutlineItem>,
        page: Int = 2,
        mode: ScrollMode = ScrollMode.CONTINUOUS,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        val flat = ReaderOutline(flattenOutline(outline, PAGES))
        var state by mutableStateOf(
            ReaderUiState.Ready(
                title = "Contrat de location",
                pageSizes = document.pageSizes,
                renderer = PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
                scrollMode = mode,
                currentPage = page,
                outline = flat.withCurrentSection(page),
            ),
        )
        current = { state }
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                LutrinTheme(dynamicColor = false) {
                    ReaderScreen(
                        state = state,
                        onBack = {},
                        onPageChange = {
                            state = state.copy(currentPage = it, outline = state.outline.withCurrentSection(it))
                        },
                        onOutlineOpen = {
                            val opened = state.outline.withCurrentSection(state.currentPage).revealingCurrentSection()
                            state = state.copy(outline = opened)
                        },
                        onOutlineToggle = { state = state.copy(outline = state.outline.toggling(it)) },
                        onExternalOutlineEntry = { externals += it },
                    )
                }
            }
        }
    }

    private fun openOutline() {
        composeRule.onNodeWithContentDescription("Table of contents").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `a document without outline has no table of contents button`() {
        show(emptyList())

        composeRule.onNodeWithContentDescription("More options").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Table of contents").assertDoesNotExist()
    }

    @Test
    fun `the button opens the drawer with the title, the document and its page count`() {
        show(contract)

        openOutline()

        composeRule.onNodeWithText("Table of contents").assertIsDisplayed()
        composeRule.onNodeWithText("Contrat de location · 8 pages").assertIsDisplayed()
        composeRule.onNodeWithText("1. Objet du contrat").assertIsDisplayed()
        composeRule.onNodeWithText("Annexes").assertIsDisplayed()
    }

    @Test
    fun `the drawer is not composed while it is closed`() {
        show(contract)

        composeRule.onNodeWithTag(OUTLINE_LIST_TAG).assertDoesNotExist()
    }

    @Test
    fun `the current section is expanded and highlighted`() {
        show(contract, page = 2)

        openOutline()

        // « 3.1 » starts on the same page, after « 3. »: it is the section being read.
        composeRule.onNodeWithText("3.1 Montant").assertIsSelected()
        composeRule.onNodeWithText("3. Loyer et charges").assertIsDisplayed().assertIsNotSelected()
        composeRule.onNodeWithText("1. Objet du contrat").assertIsNotSelected()
        // Not on the way to the current section: still collapsed.
        composeRule.onNodeWithText("2.1 Date de prise d’effet").assertDoesNotExist()
    }

    @Test
    fun `the path to a deep current section is expanded`() {
        show(contract, page = 1)

        openOutline()

        composeRule.onNodeWithText("2. Durée du bail").assertIsDisplayed()
        composeRule.onNodeWithText("2.2 Renouvellement").assertIsSelected()
        composeRule.onNodeWithText("2.1 Date de prise d’effet").assertIsDisplayed()
    }

    @Test
    fun `a chevron shows and hides the sub-sections and announces its state`() {
        show(contract, page = 0)
        openOutline()
        val toggle = composeRule.onNodeWithContentDescription("Sub-sections of 2. Durée du bail")

        toggle.assertHasClickAction().assert(hasState("Collapsed"))
        composeRule.onNodeWithText("2.1 Date de prise d’effet").assertDoesNotExist()
        toggle.performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2.1 Date de prise d’effet").assertIsDisplayed()
        toggle.assert(hasState("Expanded"))
        toggle.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2.1 Date de prise d’effet").assertDoesNotExist()
        toggle.assert(hasState("Collapsed"))
    }

    @Test
    fun `an entry without sub-sections has no chevron`() {
        show(contract, page = 0)

        openOutline()

        composeRule.onNodeWithContentDescription("Sub-sections of 1. Objet du contrat").assertDoesNotExist()
    }

    @Test
    fun `an entry goes to its page in continuous scrolling and closes the drawer`() {
        show(contract, page = 0)
        openOutline()

        composeRule.onNodeWithText("4. Résiliation").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(OUTLINE_LIST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("5 / 8").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Document, page 5 of 8").assertIsDisplayed()
    }

    @Test
    fun `an entry goes to its page page by page and closes the drawer`() {
        show(contract, page = 0, mode = ScrollMode.PAGED)
        openOutline()

        composeRule.onNodeWithText("Annexes").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(OUTLINE_LIST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("8 / 8").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Document, page 8 of 8").assertIsDisplayed()
    }

    @Test
    fun `an entry with a position stays on its page`() {
        show(contract, page = 0)
        openOutline()
        composeRule.onNodeWithContentDescription("Sub-sections of 2. Durée du bail").performClick()

        composeRule.onNodeWithText("2.1 Date de prise d’effet").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2 / 8").assertIsDisplayed()
    }

    @Test
    fun `the close button closes the drawer without moving`() {
        show(contract, page = 3)
        openOutline()

        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(OUTLINE_LIST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("4 / 8").assertIsDisplayed()
    }

    @Test
    fun `back closes the drawer`() {
        show(contract, page = 3)
        openOutline()

        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(OUTLINE_LIST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Contrat de location").assertIsDisplayed()
    }

    @Test
    fun `the drawer opens again on the new current section`() {
        show(contract, page = 0)
        openOutline()
        composeRule.onNodeWithText("4. Résiliation").performClick()
        composeRule.waitForIdle()

        openOutline()

        composeRule.onNodeWithText("4. Résiliation").assertIsSelected()
    }

    @Test
    fun `an external entry is marked, reports its address and keeps the drawer open`() {
        show(
            listOf(
                entry("Site du bailleur", page = null, uri = "https://example.org/bail"),
                entry("Chapitre", page = 0),
            ),
            page = 0,
        )
        openOutline()

        composeRule.onNodeWithContentDescription(
            "Site du bailleur, external link",
        ).assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertThat(externals).containsExactly("https://example.org/bail")
        composeRule.onNodeWithTag(OUTLINE_LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("1 / 8").assertIsDisplayed()
    }

    @Test
    fun `an entry without destination does nothing`() {
        show(listOf(entry("Titre seul", page = null), entry("Chapitre", page = 3)), page = 0)
        openOutline()

        composeRule.onNodeWithText("Titre seul").assertHasNoClickAction().assertIsDisplayed()
        composeRule.onNodeWithText("Chapitre").assertHasClickAction()
        assertThat(externals).isEmpty()
    }

    @Test
    fun `a title without destination but with sub-sections shows them when tapped`() {
        show(listOf(entry("Partie I", page = null, kids = listOf(entry("Chapitre 1", page = 3)))), page = 0)
        openOutline()
        composeRule.onNodeWithText("Chapitre 1").assertDoesNotExist()

        composeRule.onNodeWithText("Partie I").assertHasClickAction().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Chapitre 1").assertIsDisplayed()
        composeRule.onNodeWithText("1 / 8").assertIsDisplayed()
    }

    @Test
    fun `an entry with a blank title still has a name`() {
        show(listOf(entry(" ", page = 0)))
        openOutline()

        composeRule.onNodeWithText("Untitled section").assertIsDisplayed()
    }

    @Test
    fun `TalkBack reads the title, the target page, the level and the current section`() {
        show(contract, page = 1)
        openOutline()

        composeRule.onNodeWithContentDescription("2.1 Date de prise d’effet, page 2")
            .assert(hasState("Level 2"))
            .assertIsNotSelected()
        composeRule.onNodeWithContentDescription("2.2 Renouvellement, page 2")
            .assert(hasState("Level 2"))
            .assertIsSelected()
        composeRule.onNodeWithContentDescription("2. Durée du bail, page 2").assert(hasState("Level 1"))
        composeRule.onNodeWithContentDescription("Sub-sections of 2. Durée du bail").assert(hasState("Expanded"))
    }

    @Test
    fun `entries and buttons have a touch target of at least 48 dp`() {
        show(contract, page = 0)
        openOutline()

        composeRule.onNodeWithContentDescription("1. Objet du contrat, page 1").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Annexes, page 8").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Sub-sections of 2. Durée du bail")
            .assertTouchHeightIsEqualTo(48.dp)
            .assertTouchWidthIsEqualTo(48.dp)
        composeRule.onNodeWithContentDescription(
            "Close",
        ).assertTouchHeightIsEqualTo(48.dp).assertTouchWidthIsEqualTo(48.dp)
    }

    @Test
    @Config(fontScale = 2f)
    fun `at a font size of 200 percent the targets keep their size`() {
        show(contract, page = 0)
        openOutline()

        val toggle = composeRule.onNodeWithContentDescription("Sub-sections of 2. Durée du bail")
        toggle.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        composeRule.onNodeWithContentDescription("Close").assertTouchWidthIsEqualTo(48.dp)
    }

    @Test
    fun `in a right-to-left layout the drawer slides in from the right`() {
        show(contract, page = 0, direction = LayoutDirection.Rtl)

        openOutline()

        val title = composeRule.onNodeWithText("Table of contents").fetchSemanticsNode()
        val screenWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        assertThat(title.boundsInRoot.center.x).isGreaterThan(screenWidth - DRAWER_WIDTH_PX)
    }

    @Test
    fun `in a left-to-right layout the drawer slides in from the left`() {
        show(contract, page = 0)

        openOutline()

        val title = composeRule.onNodeWithText("Table of contents").fetchSemanticsNode()
        assertThat(title.boundsInRoot.center.x).isLessThan(DRAWER_WIDTH_PX)
    }

    @Test
    fun `a very long table composes only the entries in view and opens on the current section`() {
        val chapters = List(CHAPTERS) { entry("Chapitre ${it + 1}", page = it * PAGES / CHAPTERS) }
        show(chapters, page = 4)

        openOutline()

        // The chapters spread over the 8 pages; the current section is the last one on page 5.
        composeRule.onNodeWithText("Chapitre 1").assertDoesNotExist()
        composeRule.onNodeWithText("Chapitre $CHAPTERS").assertDoesNotExist()
        composeRule.onNodeWithTag(OUTLINE_LIST_TAG).assertIsDisplayed()
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = "+fr")
    fun `the table of contents is translated into French`() {
        show(contract, page = 1)

        composeRule.onNodeWithContentDescription("Table des matières").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Contrat de location · 8 pages").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2.2 Renouvellement, page 2").assert(hasState("Niveau 2"))
        composeRule.onNodeWithContentDescription("Sous-sections de 2. Durée du bail").assert(hasState("Déplié"))
        composeRule.onNodeWithContentDescription("Fermer").assertIsDisplayed()
    }

    private fun hasState(expected: String) = SemanticsMatcher("state description is $expected") {
        it.config.getOrNull(SemanticsProperties.StateDescription) == expected
    }

    private companion object {
        const val PAGES = 8
        const val CHAPTERS = 3_750

        /** The drawer is 320 dp wide, at 2 px per dp (xhdpi). */
        const val DRAWER_WIDTH_PX = 640f
    }
}
