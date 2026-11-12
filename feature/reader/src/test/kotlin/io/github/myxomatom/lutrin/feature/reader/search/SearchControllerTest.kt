// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.MAX_SEARCH_HITS
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** [SearchController]: debounce, progress, cancellation, navigation (F07, ADR 0023). */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchControllerTest {
    private val dispatcher = StandardTestDispatcher()
    private val document = FakePdfDocument(pageSizes = List(PAGE_COUNT) { A4 })
    private var shownPage = 0

    private fun hit(page: Int, top: Float = 100f) =
        SearchHit(page, listOf(PdfQuad.of(PdfRect(10f, top, 80f, top + 12f))))

    private fun TestScope.controller(open: Boolean = true) =
        SearchController(this, dispatcher, document = { document }, currentPage = { shownPage }).also {
            if (open) it.onOpen()
        }

    private fun givenHits(vararg hits: SearchHit, query: String = QUERY) {
        document.searchHits = { if (it == query) hits.toList() else emptyList() }
    }

    @Test
    fun `typing searches only once the typing stops`() = runTest(dispatcher) {
        givenHits(hit(1))
        val search = controller()

        search.onQueryChange("lu")
        advanceTimeBy(SEARCH_DEBOUNCE - 1.milliseconds)
        search.onQueryChange(QUERY)
        advanceTimeBy(SEARCH_DEBOUNCE - 1.milliseconds)
        runCurrent()
        assertThat(document.searches).isEmpty()

        advanceUntilIdle()

        assertThat(document.searches.map { it.query }).containsExactly(QUERY)
        assertThat(search.state.value.results.size).isEqualTo(1)
        assertThat(search.state.value.status).isEqualTo(SearchStatus.Done)
    }

    @Test
    fun `validating the field searches at once`() = runTest(dispatcher) {
        givenHits(hit(1))
        val search = controller()

        search.onQueryChange(QUERY)
        search.onSubmit()
        runCurrent()

        assertThat(document.searches).hasSize(1)
        advanceUntilIdle()
        assertThat(document.searches).hasSize(1)
    }

    @Test
    fun `validating a search that already ran does not search again`() = runTest(dispatcher) {
        givenHits(hit(1))
        val search = controller()
        search.onQueryChange(QUERY)
        advanceUntilIdle()

        search.onSubmit()
        advanceUntilIdle()

        assertThat(document.searches).hasSize(1)
    }

    @Test
    fun `results arrive page by page from the page shown and the first one is shown at once`() = runTest(dispatcher) {
        document.searchPageDelay = PAGE_DELAY
        givenHits(hit(1), hit(3, top = 50f), hit(3, top = 200f))
        shownPage = 2
        val search = controller()

        search.effects.test {
            search.onQueryChange(QUERY)
            search.onSubmit()
            // Pages 2 then 3 searched.
            advanceTimeBy(PAGE_DELAY * 2)
            runCurrent()

            assertThat(document.searches.single().startPage).isEqualTo(2)
            val reveal = awaitItem() as SearchEffect.Reveal
            assertThat(reveal.hit).isEqualTo(hit(3, top = 50f))
            assertThat(reveal.byUser).isFalse()
            with(search.state.value) {
                assertThat(status).isEqualTo(SearchStatus.Searching)
                assertThat(results.searchedPages).isEqualTo(2)
                assertThat(progress).isEqualTo(2f / PAGE_COUNT)
                assertThat(current).isEqualTo(HitRef(3, 0))
                assertThat(currentIndex).isEqualTo(0)
            }

            advanceUntilIdle()

            // Page 1, searched after wrapping around, comes first in document order.
            with(search.state.value) {
                assertThat(status).isEqualTo(SearchStatus.Done)
                assertThat(results.hits).containsExactly(hit(1), hit(3, top = 50f), hit(3, top = 200f)).inOrder()
                assertThat(current).isEqualTo(HitRef(3, 0))
                assertThat(currentIndex).isEqualTo(1)
            }
            expectNoEvents()
        }
    }

    @Test
    fun `a new query cancels the search running`() = runTest(dispatcher) {
        document.searchPageDelay = PAGE_DELAY
        document.searchHits = { query -> listOf(hit(if (query == QUERY) 4 else 0)) }
        val search = controller()
        search.onQueryChange("autre")
        search.onSubmit()
        advanceTimeBy(PAGE_DELAY / 2)

        search.onQueryChange(QUERY)
        search.onSubmit()
        advanceUntilIdle()

        assertThat(document.searches.map { it.query }).containsExactly("autre", QUERY).inOrder()
        assertThat(search.state.value.results.hits).containsExactly(hit(4))
    }

    @Test
    fun `a search without any occurrence ends with no result and nothing to show`() = runTest(dispatcher) {
        givenHits()
        val search = controller()

        search.effects.test {
            search.onQueryChange(QUERY)
            advanceUntilIdle()

            expectNoEvents()
        }
        with(search.state.value) {
            assertThat(status).isEqualTo(SearchStatus.Done)
            assertThat(results.isEmpty()).isTrue()
            assertThat(results.searchedPages).isEqualTo(PAGE_COUNT)
            assertThat(current).isNull()
            assertThat(currentIndex).isEqualTo(-1)
        }
    }

    @Test
    fun `a search stops at the most results it returns`() = runTest(dispatcher) {
        givenHits(*Array(MAX_SEARCH_HITS + 5) { hit(it % PAGE_COUNT, top = it.toFloat()) })
        val search = controller()

        search.onQueryChange(QUERY)
        advanceUntilIdle()

        with(search.state.value) {
            assertThat(results.size).isEqualTo(MAX_SEARCH_HITS)
            assertThat(results.isLimitReached).isTrue()
            assertThat(status).isEqualTo(SearchStatus.Done)
        }
    }

    @Test
    fun `next and previous go through the results and wrap around`() = runTest(dispatcher) {
        givenHits(hit(0), hit(2), hit(4))
        val search = controller()
        search.onQueryChange(QUERY)
        advanceUntilIdle()

        search.effects.test {
            // The first occurrence found, shown as soon as the search found it.
            assertThat(awaitItem()).isEqualTo(SearchEffect.Reveal(hit(0), byUser = false))
            search.onNext()
            assertThat(search.state.value.currentIndex).isEqualTo(1)
            assertThat(awaitItem()).isEqualTo(SearchEffect.Reveal(hit(2), byUser = true))
            search.onNext()
            search.onNext()
            assertThat(search.state.value.current).isEqualTo(HitRef(0, 0))
            assertThat(awaitItem()).isEqualTo(SearchEffect.Reveal(hit(4), byUser = true))
            assertThat(awaitItem()).isEqualTo(SearchEffect.Reveal(hit(0), byUser = true))

            search.onPrevious()

            assertThat(search.state.value.current).isEqualTo(HitRef(4, 0))
            assertThat(awaitItem()).isEqualTo(SearchEffect.Reveal(hit(4), byUser = true))
        }
    }

    @Test
    fun `next and previous do nothing without any result`() = runTest(dispatcher) {
        val search = controller()

        search.effects.test {
            search.onNext()
            search.onPrevious()

            expectNoEvents()
        }
        assertThat(search.state.value.current).isNull()
    }

    @Test
    fun `changing an option searches again at once with it`() = runTest(dispatcher) {
        givenHits(hit(1))
        val search = controller()
        search.onQueryChange(QUERY)
        advanceUntilIdle()

        search.onMatchCaseChange(true)
        runCurrent()
        search.onMatchAccentsChange(true)
        advanceUntilIdle()

        assertThat(document.searches.map { it.options })
            .containsExactly(
                SearchOptions(),
                SearchOptions(matchCase = true),
                SearchOptions(matchCase = true, matchAccents = true),
            ).inOrder()
        assertThat(search.state.value.matchCase).isTrue()
        assertThat(search.state.value.matchAccents).isTrue()
    }

    @Test
    fun `changing an option with an empty field searches nothing`() = runTest(dispatcher) {
        val search = controller()

        search.onMatchCaseChange(true)
        advanceUntilIdle()

        assertThat(document.searches).isEmpty()
        assertThat(search.state.value.matchCase).isTrue()
    }

    @Test
    fun `an empty query clears the results at once`() = runTest(dispatcher) {
        givenHits(hit(1))
        val search = controller()
        search.onQueryChange(QUERY)
        advanceUntilIdle()

        search.onQueryChange("  ")

        with(search.state.value) {
            assertThat(results.isEmpty()).isTrue()
            assertThat(current).isNull()
            assertThat(status).isEqualTo(SearchStatus.Idle)
        }
        advanceUntilIdle()
        assertThat(document.searches).hasSize(1)
    }

    @Test
    fun `closing cancels the search, clears it and keeps the options`() = runTest(dispatcher) {
        document.searchPageDelay = PAGE_DELAY
        givenHits(hit(0), hit(4))
        val search = controller()
        search.onMatchAccentsChange(true)
        search.onQueryChange(QUERY)
        search.onSubmit()
        advanceTimeBy(PAGE_DELAY * 2)

        search.onClose()
        advanceUntilIdle()

        assertThat(search.state.value)
            .isEqualTo(SearchState(isOpen = false, matchAccents = true))
    }

    @Test
    fun `a search on a closed document fails and keeps what it found`() = runTest(dispatcher) {
        document.searchPageDelay = PAGE_DELAY
        givenHits(hit(0), hit(4))
        val search = controller()
        search.onQueryChange(QUERY)
        search.onSubmit()
        advanceTimeBy(PAGE_DELAY * 2)

        document.close()
        advanceUntilIdle()

        with(search.state.value) {
            assertThat(status).isEqualTo(SearchStatus.Failed)
            assertThat(results.hits).containsExactly(hit(0))
        }
    }

    @Test
    fun `validating the field after a failure searches again`() = runTest(dispatcher) {
        document.close()
        val search = controller()
        search.onQueryChange(QUERY)
        advanceUntilIdle()
        assertThat(search.state.value.status).isEqualTo(SearchStatus.Failed)

        search.onSubmit()
        advanceUntilIdle()

        assertThat(search.state.value.status).isEqualTo(SearchStatus.Failed)
        assertThat(search.state.value.results.isEmpty()).isTrue()
    }

    @Test
    fun `the page shown is clamped to the document before searching`() = runTest(dispatcher) {
        givenHits(hit(1))
        shownPage = PAGE_COUNT + 3
        val search = controller()

        search.onQueryChange(QUERY)
        advanceUntilIdle()

        assertThat(document.searches.single().startPage).isEqualTo(PAGE_COUNT - 1)
    }

    private companion object {
        const val PAGE_COUNT = 5
        const val QUERY = "lutrin"
        val PAGE_DELAY = 10.milliseconds
    }
}
