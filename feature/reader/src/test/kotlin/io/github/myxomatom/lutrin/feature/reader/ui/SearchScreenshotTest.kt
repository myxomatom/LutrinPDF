// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchExcerpt
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SearchPageResult
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import io.github.myxomatom.lutrin.feature.reader.search.HitRef
import io.github.myxomatom.lutrin.feature.reader.search.SearchResults
import io.github.myxomatom.lutrin.feature.reader.search.SearchState
import io.github.myxomatom.lutrin.feature.reader.search.SearchStatus
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot references of the search in the reader (V1): results, none, both themes, large font, French. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class SearchScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val document = FakePdfDocument(List(MOCKUP_PAGES) { A4 })

    /** Three occurrences on the first page, like the mockup; the second one is the current one. */
    private val found: SearchState
        get() {
            val hits = listOf(150f, 240f, 330f).mapIndexed { index, top ->
                SearchHit(
                    0,
                    listOf(PdfQuad.of(PdfRect(120f + index * 80f, top, 230f + index * 80f, top + 16f))),
                    SearchExcerpt("le preneur peut demander la ", "résiliation", " avec un préavis de"),
                )
            }
            val results = (1 until MOCKUP_PAGES).fold(SearchResults.Empty + SearchPageResult(0, hits)) {
                    results,
                    page,
                ->
                results + SearchPageResult(page, emptyList())
            }
            return SearchState(
                isOpen = true,
                query = "résiliation",
                results = results,
                current = HitRef(0, 1),
                status = SearchStatus.Done,
                pageCount = MOCKUP_PAGES,
            )
        }

    private val noResult =
        SearchState(isOpen = true, query = "résilliation", status = SearchStatus.Done, pageCount = MOCKUP_PAGES)

    private fun capture(name: String, search: SearchState, darkTheme: Boolean = false) {
        val state = ReaderUiState.Ready(
            "Contrat de location",
            document.pageSizes,
            PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
        )
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) {
                ReaderScreen(state, onBack = {}, search = ReaderSearch(state = search))
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun searchLight() = capture("reader_search_light", found)

    @Test
    fun searchDark() = capture("reader_search_dark", found, darkTheme = true)

    @Test
    @Config(fontScale = 2f)
    fun searchLightFont200() = capture("reader_search_light_font200", found)

    @Test
    @Config(qualifiers = "+fr")
    fun searchLightFrench() = capture("reader_search_light_fr", found)

    @Test
    @Config(qualifiers = "+fr")
    fun searchNoResultFrench() = capture("reader_search_none_light_fr", noResult)

    @Test
    fun searchNoResultDark() = capture("reader_search_none_dark", noResult, darkTheme = true)

    private companion object {
        const val MOCKUP_PAGES = 5
    }
}
