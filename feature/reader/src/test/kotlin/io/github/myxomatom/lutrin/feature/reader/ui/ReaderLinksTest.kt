// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.GetDocumentNameUseCase
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Links of the reader (F09, V7, ADR 0022), through [ReaderRoute] and a real [ReaderViewModel]
 * on a fake document of five A4 pages.
 */
@RunWith(AndroidJUnit4::class)
class ReaderLinksTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()

    // Two links stacked on page 1, in PDF points; the taps land 5 points from the edge between
    // them, so that a tap read a few pixels too high or too low hits the other link.
    private val toPage3 = PdfLink.Internal(PdfRect(100f, 100f, 400f, 160f), PdfDestination(2, y = 300f))
    private val external = PdfLink.External(PdfRect(100f, 160f, 400f, 220f), "https://example.org/conditions")
    private val refused = PdfLink.External(PdfRect(100f, 300f, 400f, 360f), "javascript:alert(1)")
    private val document =
        FakePdfDocument(pageSizes = List(5) { A4 }).apply { links = mapOf(0 to listOf(toPage3, external, refused)) }

    private fun showReader(scrollMode: ScrollMode = ScrollMode.CONTINUOUS) {
        val settings = FakeSettingsRepository()
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
        composeRule.waitUntil { documentNode(1).fetchSemanticsNodeOrNull() != null }
    }

    private fun SemanticsNodeInteraction.fetchSemanticsNodeOrNull() = runCatching { fetchSemanticsNode() }.getOrNull()

    private fun documentNode(page: Int) = composeRule.onNodeWithContentDescription("Document, page $page of 5")

    /**
     * Taps the point ([x], [y]) of the first page, in PDF points, in continuous scroll at zoom 1:
     * the page is the width of the area minus 16 dp on each side, 8 dp below its top.
     */
    private fun tapContinuous(x: Float, y: Float) {
        documentNode(1).performTouchInput {
            val scale = (width - 2 * 16.dp.toPx()) / A4.width
            click(Offset(16.dp.toPx() + x * scale, 8.dp.toPx() + y * scale))
        }
        settleTap()
    }

    private fun settleTap() {
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MS)
        composeRule.waitForIdle()
    }

    private fun startedActivity(): Intent? = shadowOf(application).nextStartedActivity

    @Test
    fun `an internal link goes to its page, and « go back » returns to the page left`() {
        showReader()

        tapContinuous(250f, 155f)

        documentNode(3).assertIsDisplayed()
        composeRule.onNodeWithText("You were on page 1.").assertIsDisplayed()
        composeRule.onNodeWithText("Go back").performClick()
        composeRule.waitForIdle()
        documentNode(1).assertIsDisplayed()
    }

    @Test
    fun `an external link asks for confirmation, then « open » hands it to another app`() {
        showReader()

        tapContinuous(250f, 165f)

        composeRule.onNodeWithText("Open an external link?").assertIsDisplayed()
        composeRule.onNodeWithText("https://example.org/conditions").assertIsDisplayed()
        assertThat(startedActivity()).isNull()
        composeRule.onNodeWithText("Open").performClick()
        composeRule.waitForIdle()

        val intent = checkNotNull(startedActivity())
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.categories).containsExactly(Intent.CATEGORY_BROWSABLE)
        assertThat(intent.dataString).isEqualTo("https://example.org/conditions")
        composeRule.onNodeWithText("Open an external link?").assertDoesNotExist()
    }

    @Test
    fun `« copy link » copies the link and opens nothing`() {
        showReader()
        tapContinuous(250f, 165f)

        composeRule.onNodeWithText("Copy link").performClick()
        composeRule.waitForIdle()

        val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertThat(clipboard.primaryClip?.getItemAt(0)?.text).isEqualTo("https://example.org/conditions")
        assertThat(startedActivity()).isNull()
        composeRule.onNodeWithText("Open an external link?").assertDoesNotExist()
    }

    @Test
    fun `« cancel » closes the dialog and opens nothing`() {
        showReader()
        tapContinuous(250f, 165f)

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertThat(startedActivity()).isNull()
        composeRule.onNodeWithText("Open an external link?").assertDoesNotExist()
        // The tap on the link did not switch to full screen either.
        composeRule.onNodeWithText("1 / 5").assertIsDisplayed()
    }

    @Test
    fun `a link with a refused scheme is only reported`() {
        showReader()

        tapContinuous(250f, 330f)

        composeRule.onNodeWithText("This link cannot be opened.").assertIsDisplayed()
        composeRule.onNodeWithText("Open an external link?").assertDoesNotExist()
        assertThat(startedActivity()).isNull()
    }

    @Test
    fun `without an app for the link, a message says so`() {
        shadowOf(application).checkActivities(true)
        showReader()
        tapContinuous(250f, 165f)

        composeRule.onNodeWithText("Open").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No app can open this link.").assertIsDisplayed()
    }

    @Test
    fun `a tap away from the links still switches to full screen`() {
        showReader()

        tapContinuous(500f, 400f)

        composeRule.onNodeWithText("Page 1 / 5").assertIsDisplayed()
        composeRule.onNodeWithText("Open an external link?").assertDoesNotExist()
    }

    @Test
    fun `page by page, a tap on an internal link goes to its page`() {
        showReader(ScrollMode.PAGED)

        documentNode(1).performTouchInput { tapPaged(250f, 155f) }
        settleTap()

        documentNode(3).assertIsDisplayed()
        composeRule.onNodeWithText("You were on page 1.").assertIsDisplayed()
    }

    /**
     * Taps the point ([x], [y]) of the page shown page by page at zoom 1: the page fits whole,
     * 16 dp from the edges, in the area above the floating bar, and is centred in it.
     */
    private fun TouchInjectionScope.tapPaged(x: Float, y: Float) {
        val margin = 16.dp.toPx()
        val areaHeight = height - floatingBarClearancePx()
        val scale = minOf((width - 2 * margin) / A4.width, (areaHeight - 2 * margin) / A4.height)
        val left = (width - A4.width * scale) / 2
        val top = (areaHeight - A4.height * scale) / 2
        click(Offset(left + x * scale, top + y * scale))
    }

    /** Height kept free for the floating bar: no system insets under Robolectric. */
    private fun TouchInjectionScope.floatingBarClearancePx(): Float = FLOATING_BAR_CLEARANCE.toPx()

    @Test
    fun `TalkBack offers one action per link of the current page`() {
        showReader()

        val actions = documentNode(1).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()

        assertThat(actions.map { it.label }).containsExactly(
            "Link: page 3",
            "Link: https://example.org/conditions",
            "Link: javascript:alert(1)",
        ).inOrder()
        composeRule.runOnUiThread { actions.first().action() }
        composeRule.waitForIdle()
        documentNode(3).assertIsDisplayed()
    }

    @Test
    fun `the document area has no link action on a page without links`() {
        document.links = emptyMap()
        showReader()

        val config = documentNode(1).fetchSemanticsNode().config

        assertThat(config.getOrNull(SemanticsActions.CustomActions)).isNull()
        assertThat(config.getOrNull(SemanticsProperties.ContentDescription)).containsExactly("Document, page 1 of 5")
    }

    private companion object {
        const val DOUBLE_TAP_WAIT_MS = 500L

        /** Margin, height and margin of the floating bar ([floatingPageBarClearance]). */
        val FLOATING_BAR_CLEARANCE = 16.dp + 64.dp + 16.dp
    }
}
