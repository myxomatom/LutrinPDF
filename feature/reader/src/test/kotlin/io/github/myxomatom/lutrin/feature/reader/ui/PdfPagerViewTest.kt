// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.render.DOUBLE_TAP_ZOOM
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfPagerViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var document = FakePdfDocument()
    private var zoom by mutableFloatStateOf(1f)
    private lateinit var pagerState: PagerState
    private lateinit var scope: CoroutineScope

    private fun show(pageSizes: List<PageSize> = List(3) { A4 }, initialPage: Int = 0) {
        document = FakePdfDocument(pageSizes)
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                pagerState = rememberPagerState(initialPage) { pageSizes.size }
                scope = rememberCoroutineScope()
                PdfPagerView(
                    pageSizes = pageSizes,
                    renderer = PageRenderer(document, pageSizes, Dispatchers.Unconfined),
                    pagerState = pagerState,
                    zoom = zoom,
                    onZoomChange = { zoom = it },
                    onTap = {},
                    contentDescription = DOCUMENT,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun swipe(left: Boolean) {
        composeRule.onNodeWithContentDescription(DOCUMENT).performTouchInput { if (left) swipeLeft() else swipeRight() }
        composeRule.waitForIdle()
    }

    private fun pinchOut() {
        composeRule.onNodeWithContentDescription(DOCUMENT).performTouchInput {
            pinch(
                center - Offset(50f, 0f),
                center - Offset(100f, 0f),
                center + Offset(50f, 0f),
                center + Offset(100f, 0f),
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a swipe to the left shows the next page and to the right the previous one`() {
        show()

        swipe(left = true)
        assertThat(pagerState.currentPage).isEqualTo(1)

        swipe(left = true)
        assertThat(pagerState.currentPage).isEqualTo(2)

        swipe(left = false)
        assertThat(pagerState.currentPage).isEqualTo(1)
    }

    @Test
    fun `swiping stops at the first and the last page`() {
        show(initialPage = 2)

        swipe(left = true)
        assertThat(pagerState.currentPage).isEqualTo(2)

        composeRule.runOnIdle { scope.launch { pagerState.scrollToPage(0) } }
        composeRule.waitForIdle()
        swipe(left = false)
        assertThat(pagerState.currentPage).isEqualTo(0)
    }

    @Test
    fun `a one-page document stays on its page`() {
        show(pageSizes = listOf(A4))

        swipe(left = true)
        swipe(left = false)

        assertThat(pagerState.currentPage).isEqualTo(0)
    }

    @Test
    fun `the whole page fits in the screen and only the page on screen is rendered`() {
        // A tall, narrow page: fitting its width would overflow the screen.
        show(pageSizes = List(3) { PageSize(100f, 1000f) })
        val screenHeight = composeRule.onRoot().fetchSemanticsNode().size.height

        val render = document.renders.single()
        assertThat(render.pageIndex).isEqualTo(0)
        assertThat(render.height).isLessThan(screenHeight)
    }

    @Test
    fun `a pinch zooms within the page`() {
        show()

        pinchOut()

        assertThat(zoom).isWithin(TOLERANCE).of(2f)
        assertThat(pagerState.currentPage).isEqualTo(0)
    }

    @Test
    fun `while zoomed in a swipe pans the page instead of changing page`() {
        show()
        pinchOut()

        swipe(left = true)

        assertThat(pagerState.currentPage).isEqualTo(0)
        assertThat(zoom).isWithin(TOLERANCE).of(2f)
    }

    @Test
    fun `a double tap zooms in and a second one fits the page again`() {
        show()

        composeRule.onNodeWithContentDescription(DOCUMENT).performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        assertThat(zoom).isEqualTo(DOUBLE_TAP_ZOOM)

        composeRule.onNodeWithContentDescription(DOCUMENT).performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        assertThat(zoom).isEqualTo(1f)

        swipe(left = true)
        assertThat(pagerState.currentPage).isEqualTo(1)
    }

    @Test
    fun `zoomed in, the visible part around the tapped point is rendered sharp`() {
        show()

        composeRule.onNodeWithContentDescription(DOCUMENT).performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        // The sharp rendering waits for the gestures to settle (ADR 0008).
        composeRule.mainClock.advanceTimeBy(DETAIL_WAIT_MS)
        composeRule.waitForIdle()

        // The detail rendering covers the middle of the page, not its top-left corner: the
        // double tap kept the tapped point under the finger.
        val detail = document.renders.last()
        assertThat(detail.pageIndex).isEqualTo(0)
        assertThat(detail.scale).isGreaterThan(document.renders.first().scale)
        assertThat(detail.region.left).isGreaterThan(0f)
        assertThat(detail.region.top).isGreaterThan(0f)
        assertThat(detail.region.right).isLessThan(A4.width)
        assertThat(detail.region.bottom).isLessThan(A4.height)
    }

    @Test
    fun `reaching another page resets the zoom`() {
        show()
        pinchOut()

        composeRule.runOnIdle { scope.launch { pagerState.scrollToPage(2) } }
        composeRule.waitForIdle()

        assertThat(pagerState.currentPage).isEqualTo(2)
        assertThat(zoom).isEqualTo(1f)
    }

    private companion object {
        const val DOCUMENT = "Document"

        /** Longer than the delay of the detail rendering after the last gesture. */
        const val DETAIL_WAIT_MS = 500L

        /** Touch injection moves the fingers in steps, so the measured ratio is approximate. */
        const val TOLERANCE = 0.15f
    }
}
