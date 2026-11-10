// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The floating bar of the reader (E2, ADR 0018): the thin slider in both states of the page
 * preview option, its preview card and its accessibility.
 */
@RunWith(AndroidJUnit4::class)
class FloatingPageBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun ready(
        document: PdfDocument = FakePdfDocument(),
        pageSizes: List<PageSize> = (document as? FakePdfDocument)?.pageSizes ?: List(document.pageCount) { A4 },
        pagePreview: Boolean = true,
        scrollMode: ScrollMode = ScrollMode.CONTINUOUS,
    ) = ReaderUiState.Ready(
        title = "Contrat de location",
        pageSizes = pageSizes,
        renderer = PageRenderer(document, pageSizes, Dispatchers.Unconfined),
        scrollMode = scrollMode,
        pagePreview = pagePreview,
    )

    /** Shows the reader wired like the ViewModel; returns the pages reported by the screen. */
    private fun show(initial: ReaderUiState.Ready): List<Int> {
        val reported = mutableListOf<Int>()
        var state by mutableStateOf(initial)
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                ReaderScreen(
                    state = state,
                    onBack = {},
                    onPageChange = {
                        reported += it
                        state = state.copy(currentPage = it)
                    },
                    onScrollModeChange = { state = state.copy(scrollMode = it) },
                )
            }
        }
        return reported
    }

    private val slider get() = composeRule.onNodeWithContentDescription("Go to page")

    private fun assertDocumentOn(page: Int, pageCount: Int = 3) {
        composeRule.onNodeWithContentDescription("Document, page $page of $pageCount").assertIsDisplayed()
    }

    @Test
    fun `with the preview on, dragging previews the page and leaves the document where it is`() {
        show(ready())

        slider.performTouchInput {
            down(trackStart)
            slideTo(trackEnd)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PAGE_PREVIEW_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Page 3").assertIsDisplayed()
        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
        slider.assert(hasStateDescription("Page 3 of 3"))
        assertDocumentOn(page = 1)
    }

    @Test
    fun `with the preview on, the release goes to the page aimed at`() {
        val reported = show(ready())

        slider.performTouchInput {
            down(trackStart)
            slideTo(trackEnd)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PAGE_PREVIEW_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
        assertDocumentOn(page = 3)
        assertThat(reported.last()).isEqualTo(2)
    }

    @Test
    fun `with the preview on, a tap on the track goes to the page at once`() {
        show(ready())

        slider.performTouchInput { click(trackEnd) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PAGE_PREVIEW_TAG).assertDoesNotExist()
        assertDocumentOn(page = 3)
    }

    @Test
    fun `a cancelled drag stays on the starting page`() {
        show(ready())

        slider.performTouchInput {
            down(trackStart)
            slideTo(trackEnd)
            cancel()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PAGE_PREVIEW_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
        assertDocumentOn(page = 1)
    }

    @Test
    fun `releasing on the starting page stays on it`() {
        show(ready())

        slider.performTouchInput {
            down(trackStart)
            slideTo(trackEnd)
            slideTo(trackStart)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
        assertDocumentOn(page = 1)
    }

    @Test
    fun `with the preview off, the document follows the slider while it moves`() {
        show(ready(pagePreview = false))

        slider.performTouchInput {
            down(trackStart)
            slideTo(trackEnd)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PAGE_PREVIEW_TAG).assertDoesNotExist()
        assertDocumentOn(page = 3)
        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
        slider.performTouchInput { up() }
    }

    @Test
    fun `page by page, the preview goes to the page on release`() {
        show(ready(scrollMode = ScrollMode.PAGED))

        slider.performTouchInput {
            down(trackStart)
            slideTo(center)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Page 2").assertIsDisplayed()
        assertDocumentOn(page = 1)

        slider.performTouchInput { up() }
        composeRule.waitForIdle()

        assertDocumentOn(page = 2)
        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()
    }

    @Test
    fun `the slider reads its label and the page, and TalkBack moves it, preview on or off`() {
        var preview by mutableStateOf(true)
        var state by mutableStateOf(ready())
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                ReaderScreen(state.copy(pagePreview = preview), onBack = {}, onPageChange = {
                    state = state.copy(currentPage = it)
                })
            }
        }

        slider
            .assert(hasStateDescription("Page 1 of 3"))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(
                        0f,
                        0f..2f,
                    ),
                ),
            )
            .assertHeightIsAtLeast(LutrinDimens.TouchTargetMin)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        composeRule.waitForIdle()
        assertDocumentOn(page = 3)
        slider.assert(hasStateDescription("Page 3 of 3"))

        preview = false
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        composeRule.waitForIdle()
        assertDocumentOn(page = 2)
    }

    @Test
    fun `a very large document reaches its first and last pages`() {
        val document = FakePdfDocument(List(LARGE_DOCUMENT_PAGES) { A4 })
        show(ready(document))

        slider.performTouchInput {
            down(trackStart)
            // Beyond the end of the track: the slider stops on the last page.
            slideTo(centerRight)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("$LARGE_DOCUMENT_PAGES / $LARGE_DOCUMENT_PAGES").assertIsDisplayed()
        slider.assert(hasStateDescription("Page $LARGE_DOCUMENT_PAGES of $LARGE_DOCUMENT_PAGES"))

        slider.performTouchInput {
            down(trackEnd)
            slideTo(centerLeft)
            up()
        }
        composeRule.waitForIdle()
        assertDocumentOn(page = 1, pageCount = LARGE_DOCUMENT_PAGES)
        composeRule.onNodeWithText("1 / $LARGE_DOCUMENT_PAGES").assertIsDisplayed()
    }

    @Test
    fun `at both ends of the slider the preview card stays inside the bar`() {
        show(ready(FakePdfDocument(List(LARGE_DOCUMENT_PAGES) { A4 })))
        val bar = composeRule.onNodeWithContentDescription("Go to page").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

        slider.performTouchInput {
            down(trackStart)
            slideTo(centerRight)
        }
        composeRule.waitForIdle()
        val atEnd = composeRule.onNodeWithTag(PAGE_PREVIEW_TAG).fetchSemanticsNode().boundsInRoot
        slider.performTouchInput { slideTo(centerLeft) }
        composeRule.waitForIdle()
        val atStart = composeRule.onNodeWithTag(PAGE_PREVIEW_TAG).fetchSemanticsNode().boundsInRoot
        slider.performTouchInput { cancel() }

        assertThat(atEnd.right).isAtMost(root.right)
        assertThat(atEnd.left).isGreaterThan(atStart.left)
        assertThat(atStart.left).isAtLeast(root.left)
        assertThat(atEnd.bottom).isAtMost(bar.top)
        assertThat(atStart.top).isAtLeast(root.top)
    }

    @Test
    fun `a one-page document has the bar without a slider`() {
        show(ready(FakePdfDocument(listOf(A4))))

        composeRule.onNodeWithText("1 / 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Fit to width").assertIsDisplayed()
        slider.assertDoesNotExist()
    }

    @Test
    fun `the bar and its slider are hidden in full screen`() {
        show(ready())

        composeRule.onNodeWithContentDescription("Document, page 1 of 3").performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MS)

        slider.assertDoesNotExist()
        composeRule.onNodeWithText("1 / 3").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Fit to width").assertDoesNotExist()
        composeRule.onNodeWithText("Page 1 / 3").assertIsDisplayed()
    }

    @Test
    fun `the preview renders the page aimed at and cancels the rendering of a page left`() {
        val document = GatedPdfDocument(FakePdfDocument(List(PREVIEW_PAGES) { A4 }))
        show(ready(document, pageSizes = List(PREVIEW_PAGES) { A4 }))

        slider.performTouchInput {
            down(trackStart)
            slideTo(center)
        }
        composeRule.waitUntil { document.waiting.isNotEmpty() }
        val aimedFirst = document.waiting.last()
        slider.performTouchInput { slideTo(trackEnd) }
        composeRule.waitUntil { document.waiting.contains(PREVIEW_PAGES - 1) }

        // Only the page aimed at last is still rendering: every page left was cancelled.
        assertThat(document.cancelled).contains(aimedFirst)
        assertThat(document.cancelled).containsExactlyElementsIn(document.waiting.filter { it != PREVIEW_PAGES - 1 })
        composeRule.onNodeWithText("Page $PREVIEW_PAGES").assertIsDisplayed()
        document.gate.complete(Unit)
        composeRule.waitUntil { document.rendered.contains(PREVIEW_PAGES - 1) }
        assertThat(document.rendered).containsExactly(PREVIEW_PAGES - 1)
        slider.performTouchInput { up() }
    }

    /** Centre of the thumb on the first page: the track starts half a thumb from the edge. */
    private val TouchInjectionScope.trackStart: Offset get() = centerLeft + Offset(EDGE_INSET_PX, 0f)

    /** Centre of the thumb on the last page. */
    private val TouchInjectionScope.trackEnd: Offset get() = centerRight - Offset(EDGE_INSET_PX, 0f)

    /** Drags the pointer already down to [target], in small moves like a finger. */
    private fun TouchInjectionScope.slideTo(target: Offset) {
        val start = currentPosition() ?: return
        val step = (target - start) / DRAG_STEPS.toFloat()
        repeat(DRAG_STEPS) { moveBy(step) }
    }

    /**
     * Wraps a document so that its renderings of the slider preview wait for [gate]: the test sees
     * which page is being rendered, and which renderings were cancelled. The pages of the reader
     * itself (wider than the preview) render at once.
     */
    private class GatedPdfDocument(private val document: FakePdfDocument) : PdfDocument by document {
        val gate = CompletableDeferred<Unit>()
        val waiting: MutableList<Int> = CopyOnWriteArrayList()
        val cancelled: MutableList<Int> = CopyOnWriteArrayList()
        val rendered: MutableList<Int> = CopyOnWriteArrayList()

        override suspend fun renderPage(index: Int, target: Bitmap, region: PdfRect, scale: Float, nightMode: Boolean) {
            if (target.width < PREVIEW_MAX_WIDTH_PX) {
                waiting += index
                try {
                    gate.await()
                } catch (cancellation: CancellationException) {
                    cancelled += index
                    throw cancellation
                }
                rendered += index
            }
            document.renderPage(index, target, region, scale, nightMode)
        }
    }

    private companion object {
        /** Moves of one drag: the slider ignores a single jump across the track (touch slop). */
        const val DRAG_STEPS = 20

        /** Half the 20 dp thumb at the default mdpi density of Robolectric, in pixels. */
        const val EDGE_INSET_PX = 10f

        const val DOUBLE_TAP_WAIT_MS = 500L
        const val LARGE_DOCUMENT_PAGES = 600
        const val PREVIEW_PAGES = 9

        /** The preview is 128 dp wide, 128 px at the default mdpi density; pages are 288 px wide. */
        const val PREVIEW_MAX_WIDTH_PX = 200

        fun hasStateDescription(value: String) =
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
    }
}
