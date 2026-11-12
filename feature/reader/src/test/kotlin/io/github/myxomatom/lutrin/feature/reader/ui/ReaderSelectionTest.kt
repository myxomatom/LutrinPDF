// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.GetDocumentNameUseCase
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SelectionMode
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import io.github.myxomatom.lutrin.core.domain.recents.GetStartPageUseCase
import io.github.myxomatom.lutrin.core.domain.recents.IsRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecordDocumentOpeningUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RemoveRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.SaveLastPageUseCase
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.domain.settings.SetScrollModeUseCase
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakeDocumentNameResolver
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import io.github.myxomatom.lutrin.core.testing.FakeRecentDocumentsRepository
import io.github.myxomatom.lutrin.core.testing.FakeSettingsRepository
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.READER_URI_ARG
import io.github.myxomatom.lutrin.feature.reader.ReaderViewModel
import io.github.myxomatom.lutrin.feature.reader.ReadingHistory
import io.github.myxomatom.lutrin.feature.reader.selection.SelectionEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Text selection in the reader (F08, ADR 0024), through [ReaderRoute] and a real [ReaderViewModel]
 * on a fake document: long press, handles, action bar, copy permission, and the gestures of the
 * other features that must keep working.
 */
@RunWith(AndroidJUnit4::class)
class ReaderSelectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val settings = FakeSettingsRepository()
    private val word = selection("bail", left = 100f, right = 160f)
    private val longer = selection("bail de location", left = 100f, right = 300f)
    private val wholePage = selection("Contrat\nbail de location", left = 72f, right = 500f)
    private val toPage3 = PdfLink.Internal(PdfRect(100f, 400f, 400f, 440f), PdfDestination(2))

    private val document =
        FakePdfDocument(pageSizes = List(5) { A4 }).apply {
            textAreas = mapOf(0 to listOf(PdfRect(72f, 190f, 500f, 220f)))
            selections = mapOf(0 to word)
            links = mapOf(0 to listOf(toPage3))
        }

    private fun selection(text: String, left: Float, right: Float) = TextSelection(
        pageIndex = 0,
        quads = listOf(PdfQuad.of(PdfRect(left, 200f, right, 214f))),
        text = text,
        start = PdfPoint(left, 214f),
        end = PdfPoint(right, 214f),
    )

    private fun showReader(scrollMode: ScrollMode = ScrollMode.CONTINUOUS, of: String = "of") {
        runBlocking { settings.setScrollMode(scrollMode) }
        val recents = FakeRecentDocumentsRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val viewModel =
            ReaderViewModel(
                SavedStateHandle(mapOf(READER_URI_ARG to "content://com.example.provider/document/a.pdf")),
                FakePdfEngine(Result.success(document)),
                Dispatchers.Unconfined,
                ObserveSettingsUseCase(settings),
                SetScrollModeUseCase(settings),
                ReadingHistory(
                    GetStartPageUseCase(recents),
                    RecordDocumentOpeningUseCase(recents),
                    SaveLastPageUseCase(recents),
                    IsRecentDocumentUseCase(recents),
                    RemoveRecentDocumentUseCase(recents),
                    GetDocumentNameUseCase(recents, FakeDocumentNameResolver()),
                    scope,
                ),
            )
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) { ReaderRoute(onBack = {}, viewModel = viewModel) }
        }
        composeRule.waitUntil {
            composeRule.onNodeWithContentDescription("Document, page 1 $of 5").fetchSemanticsNodeOrNull() != null
        }
    }

    private fun SemanticsNodeInteraction.fetchSemanticsNodeOrNull() = runCatching { fetchSemanticsNode() }.getOrNull()

    private fun documentNode(page: Int) = composeRule.onNodeWithContentDescription("Document, page $page of 5")

    /** The point ([x], [y]) of the first page, in continuous scroll at zoom 1 (see `ReaderLinksTest`). */
    private fun TouchInjectionScope.continuous(x: Float, y: Float): Offset {
        val scale = (width - 2 * 16.dp.toPx()) / A4.width
        return Offset(16.dp.toPx() + x * scale, 8.dp.toPx() + y * scale)
    }

    /** The point ([x], [y]) of the page shown page by page at zoom 1 (see `ReaderLinksTest`). */
    private fun TouchInjectionScope.paged(x: Float, y: Float): Offset {
        val margin = 16.dp.toPx()
        val areaHeight = height - (16.dp + 64.dp + 16.dp).toPx()
        val scale = minOf((width - 2 * margin) / A4.width, (areaHeight - 2 * margin) / A4.height)
        return Offset((width - A4.width * scale) / 2 + x * scale, (areaHeight - A4.height * scale) / 2 + y * scale)
    }

    private fun longPressWord() {
        documentNode(1).performTouchInput { longClick(continuous(120f, 207f)) }
        composeRule.waitForIdle()
    }

    private fun tap(x: Float, y: Float) {
        documentNode(1).performTouchInput { click(continuous(x, y)) }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MS)
        composeRule.waitForIdle()
    }

    private fun copied(): String? = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

    private fun handle(label: String) = composeRule.onNodeWithContentDescription(label)

    private fun assertSelectionShown() {
        handle(START_HANDLE).assertExists()
        handle(END_HANDLE).assertExists()
        composeRule.onNodeWithText("Copy").assertIsDisplayed()
        composeRule.onNodeWithText("Select all").assertIsDisplayed()
    }

    private fun assertNoSelection() {
        handle(START_HANDLE).assertDoesNotExist()
        handle(END_HANDLE).assertDoesNotExist()
        composeRule.onNodeWithText("Copy").assertDoesNotExist()
    }

    @Test
    fun `a long press on a word selects it, and copy puts it on the clipboard`() {
        showReader()

        longPressWord()

        assertSelectionShown()
        assertThat(document.selectionRequests.single().mode).isEqualTo(SelectionMode.Words)
        composeRule.onNodeWithText("Copy").performClick()
        composeRule.waitForIdle()
        assertThat(copied()).isEqualTo("bail")
        assertNoSelection()
        // From Android 13 the system confirms the copy itself.
        composeRule.onNodeWithText("Text copied").assertDoesNotExist()
    }

    @Test
    fun `dragging the end handle extends the selection before copying it`() {
        showReader()
        longPressWord()
        document.selections = mapOf(0 to longer)

        handle(END_HANDLE).performTouchInput {
            down(center)
            moveBy(Offset(150f, 0f))
            up()
        }
        composeRule.waitForIdle()

        val drag = document.selectionRequests.last()
        assertThat(drag.mode).isEqualTo(SelectionMode.Characters)
        assertThat(drag.start).isEqualTo(word.start)
        assertThat(drag.end.x).isGreaterThan(word.end?.x ?: 0f)
        composeRule.onNodeWithText("Copy").performClick()
        composeRule.waitForIdle()
        assertThat(copied()).isEqualTo("bail de location")
    }

    @Test
    fun `select all takes the whole page`() {
        showReader()
        longPressWord()
        document.selections = mapOf(0 to wholePage)

        composeRule.onNodeWithText("Select all").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Copy").performClick()
        composeRule.waitForIdle()

        assertThat(document.selectionRequests.map { it.mode }).contains(SelectionMode.Lines)
        assertThat(copied()).isEqualTo("Contrat\nbail de location")
    }

    @Test
    fun `a tap elsewhere closes the selection and keeps the controls`() {
        showReader()
        longPressWord()

        tap(300f, 600f)

        assertNoSelection()
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test
    fun `back closes the selection before leaving`() {
        showReader()
        longPressWord()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertNoSelection()
        documentNode(1).assertIsDisplayed()
    }

    @Test
    fun `a forbidden copy says why, then the setting allows it`() {
        document.canCopyText = false
        showReader()
        longPressWord()

        composeRule.onNodeWithText("Copy").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("The author of this document forbids copying its text").assertIsDisplayed()
        assertThat(copied()).isNull()
        assertSelectionShown()

        runBlocking { settings.setIgnoreCopyRestriction(true) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Copy").performClick()
        composeRule.waitForIdle()

        assertThat(copied()).isEqualTo("bail")
    }

    @Test
    fun `a long press on a scanned page selects nothing and acts as a tap`() {
        document.textAreas = emptyMap()
        document.selections = emptyMap()
        showReader()

        longPressWord()

        assertNoSelection()
        // As before the selection existed: the press switched to full screen.
        composeRule.onNodeWithContentDescription("Search").assertDoesNotExist()
    }

    @Test
    fun `with a selection, a tap on a link closes it, and the next tap follows the link`() {
        showReader()
        longPressWord()

        tap(250f, 420f)
        assertNoSelection()
        documentNode(1).assertIsDisplayed()

        tap(250f, 420f)
        documentNode(3).assertIsDisplayed()
    }

    @Test
    fun `opening the search closes the selection, and the search still works`() {
        document.searchHits = { query -> if (query == "bail") listOf(SearchHit(0, word.quads)) else emptyList() }
        showReader()
        longPressWord()

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.waitForIdle()
        assertNoSelection()
        composeRule.onNode(hasSetTextAction()).performTextInput("bail")
        // Validated at once: the delay after the typing runs on the main looper of the ViewModel.
        composeRule.onNode(hasSetTextAction()).performImeAction()
        composeRule.waitForIdle()

        assertThat(document.searches.map { it.query }).contains("bail")
    }

    @Test
    fun `page by page, a long press selects a word, and another page closes the selection`() {
        showReader(ScrollMode.PAGED)

        documentNode(1).performTouchInput { longClick(paged(120f, 207f)) }
        composeRule.waitForIdle()
        assertSelectionShown()

        goToPage3()

        documentNode(3).assertIsDisplayed()
        assertNoSelection()
    }

    @Test
    fun `in continuous scroll, another page closes the selection`() {
        showReader()
        longPressWord()

        goToPage3()

        assertNoSelection()
    }

    /** Follows the link of page 1 with its TalkBack action: a change of page. */
    private fun goToPage3() {
        val actions = documentNode(1).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        composeRule.runOnUiThread { actions.first { it.label == "Link: page 3" }.action() }
        composeRule.waitForIdle()
    }

    @Test
    fun `TalkBack copies the text of the page with an action of the document`() {
        document.selections = mapOf(0 to wholePage)
        showReader()

        copyPageAction()

        assertThat(copied()).isEqualTo("Contrat\nbail de location")
        assertNoSelection()
    }

    @Test
    fun `the TalkBack copy obeys the copy permission`() {
        document.canCopyText = false
        showReader()

        copyPageAction()

        assertThat(copied()).isNull()
        composeRule.onNodeWithText("The author of this document forbids copying its text").assertIsDisplayed()
    }

    @Test
    fun `the TalkBack copy of a page without text says so`() {
        document.selections = emptyMap()
        showReader()

        copyPageAction()

        assertThat(copied()).isNull()
        composeRule.onNodeWithText("No text on this page").assertIsDisplayed()
    }

    private fun copyPageAction() {
        val actions = documentNode(1).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        val copyPage = actions.single { it.label == "Copy the text of the page" }
        composeRule.runOnUiThread { copyPage.action() }
        composeRule.waitForIdle()
    }

    @Test
    fun `the handles and the actions are 48 dp targets, named for TalkBack`() {
        showReader()
        longPressWord()

        handle(START_HANDLE).assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        handle(END_HANDLE).assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        val minimum = with(composeRule.density) { 48.dp.toPx() }
        listOf("Copy", "Select all").forEach { label ->
            val bounds = composeRule.onNodeWithText(label).fetchSemanticsNode().touchBoundsInRoot
            assertThat(bounds.height).isAtLeast(minimum)
            assertThat(bounds.width).isAtLeast(minimum)
        }
    }

    @Test
    fun `before Android 13, the reader confirms the copy itself`() {
        // Robolectric runs Android 16 only here: the version is given to the handler.
        composeRule.setContent {
            val snackbar = remember { SnackbarHostState() }
            LutrinTheme(dynamicColor = false) {
                SelectionEffectsHandler(flowOf(SelectionEffect.Copy("bail", false)), snackbar, systemConfirmsCopy = false)
                SnackbarHost(snackbar)
            }
        }
        composeRule.waitForIdle()

        assertThat(copied()).isEqualTo("bail")
        composeRule.onNodeWithText("Text copied").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the selection is translated into French`() {
        document.canCopyText = false
        showReader(of = "sur")
        composeRule.onNodeWithContentDescription("Document, page 1 sur 5").performTouchInput {
            longClick(continuous(120f, 207f))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Début de la sélection").assertExists()
        composeRule.onNodeWithContentDescription("Fin de la sélection").assertExists()
        composeRule.onNodeWithText("Tout sélectionner").assertIsDisplayed()
        composeRule.onNodeWithText("Copier").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("L’auteur de ce document interdit la copie du texte").assertIsDisplayed()
    }

    private companion object {
        const val DOUBLE_TAP_WAIT_MS = 500L
        const val START_HANDLE = "Start of the selection"
        const val END_HANDLE = "End of the selection"
    }
}
