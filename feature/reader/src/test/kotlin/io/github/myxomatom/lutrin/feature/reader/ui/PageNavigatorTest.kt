// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `PageNavigator.goTo` with a destination in the page, in both scroll modes (ADR 0011). */
@RunWith(AndroidJUnit4::class)
class PageNavigatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pageSizes = List(PAGES) { PageSize(WIDTH, HEIGHT) }
    private lateinit var navigator: PageNavigator
    private lateinit var scope: CoroutineScope

    private fun show(mode: ScrollMode) {
        val document = FakePdfDocument(pageSizes = pageSizes)
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                navigator = rememberPageNavigator(mode, initialPage = 0, pageSizes = pageSizes)
                scope = rememberCoroutineScope()
                val renderer = PageRenderer(document, pageSizes, Dispatchers.Unconfined)
                when (val nav = navigator) {
                    is ContinuousNavigator ->
                        PdfPagesView(pageSizes, renderer, nav.listState, 1f, {}, {}, contentDescription = "Document")

                    is PagedNavigator ->
                        PdfPagerView(pageSizes, renderer, nav.pagerState, 1f, {}, {}, contentDescription = "Document")
                }
            }
        }
    }

    private fun goTo(page: Int, yOffset: Float? = null) {
        composeRule.runOnUiThread {
            scope.launch { if (yOffset == null) navigator.goTo(page) else navigator.goTo(page, yOffset) }
        }
        composeRule.waitForIdle()
    }

    private val listState: LazyListState get() = (navigator as ContinuousNavigator).listState

    private fun pageHeightPx(page: Int): Int = listState.layoutInfo.visibleItemsInfo.first { it.index == page }.size

    @Test
    fun `continuous, goTo without offset puts the top of the page at the top`() {
        show(ScrollMode.CONTINUOUS)

        goTo(page = 2)

        assertThat(listState.firstVisibleItemIndex).isEqualTo(2)
        assertThat(listState.firstVisibleItemScrollOffset).isEqualTo(0)
    }

    @Test
    fun `continuous, the offset in points scrolls the page by the same share of its height`() {
        show(ScrollMode.CONTINUOUS)

        goTo(page = 2, yOffset = HEIGHT / 2)

        assertThat(listState.firstVisibleItemIndex).isEqualTo(2)
        assertThat(listState.firstVisibleItemScrollOffset.toFloat()).isWithin(1f).of(pageHeightPx(2) / 2f)
        assertThat(navigator.currentPage).isEqualTo(2)
    }

    @Test
    fun `continuous, an offset of a quarter of the page scrolls a quarter of it`() {
        show(ScrollMode.CONTINUOUS)

        goTo(page = 1, yOffset = HEIGHT / 4)

        assertThat(listState.firstVisibleItemScrollOffset.toFloat()).isWithin(1f).of(pageHeightPx(1) / 4f)
    }

    @Test
    fun `continuous, an offset past the bottom of the page stays on that page`() {
        show(ScrollMode.CONTINUOUS)

        goTo(page = 1, yOffset = HEIGHT * 10)

        assertThat(listState.firstVisibleItemIndex).isEqualTo(1)
        assertThat(listState.firstVisibleItemScrollOffset).isAtMost(pageHeightPx(1))
    }

    @Test
    fun `continuous, a negative offset is the top of the page`() {
        show(ScrollMode.CONTINUOUS)

        goTo(page = 1, yOffset = -50f)

        assertThat(listState.firstVisibleItemIndex).isEqualTo(1)
        assertThat(listState.firstVisibleItemScrollOffset).isEqualTo(0)
    }

    @Test
    fun `continuous, going to another destination replaces the first one`() {
        show(ScrollMode.CONTINUOUS)
        goTo(page = 3, yOffset = HEIGHT / 2)

        goTo(page = 1, yOffset = HEIGHT / 4)

        assertThat(listState.firstVisibleItemIndex).isEqualTo(1)
        assertThat(listState.firstVisibleItemScrollOffset.toFloat()).isWithin(1f).of(pageHeightPx(1) / 4f)
    }

    @Test
    fun `page by page, goTo shows the page whatever the offset`() {
        show(ScrollMode.PAGED)

        goTo(page = 3, yOffset = HEIGHT / 2)

        assertThat(navigator.currentPage).isEqualTo(3)
        goTo(page = 1)
        assertThat(navigator.currentPage).isEqualTo(1)
    }

    private companion object {
        const val PAGES = 5
        const val WIDTH = 600f
        const val HEIGHT = 800f
    }
}
