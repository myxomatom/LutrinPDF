// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.GetDocumentNameUseCase
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.recents.GetStartPageUseCase
import io.github.myxomatom.lutrin.core.domain.recents.IsRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecordDocumentOpeningUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RemoveRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.SaveLastPageUseCase
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetScrollModeUseCase
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakeDocumentNameResolver
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import io.github.myxomatom.lutrin.core.testing.FakeRecentDocumentsRepository
import io.github.myxomatom.lutrin.core.testing.FakeSettingsRepository
import io.github.myxomatom.lutrin.feature.reader.search.SearchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Search of [ReaderViewModel] (F07, ADR 0023): the controller is wired to the document and the page shown. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderViewModelSearchTest {
    private val dispatcher = StandardTestDispatcher()
    private val area = PdfQuad.of(PdfRect(10f, 10f, 100f, 30f))
    private val document = FakePdfDocument(pageSizes = List(5) { A4 })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.openViewModel(): ReaderViewModel {
        val recents = FakeRecentDocumentsRepository()
        val settings = FakeSettingsRepository()
        return ReaderViewModel(
            SavedStateHandle(mapOf(READER_URI_ARG to "content://com.example.provider/document/a.pdf")),
            FakePdfEngine(Result.success(document)),
            dispatcher,
            ObserveSettingsUseCase(settings),
            SetScrollModeUseCase(settings),
            ReadingHistory(
                GetStartPageUseCase(recents),
                RecordDocumentOpeningUseCase(recents),
                SaveLastPageUseCase(recents),
                IsRecentDocumentUseCase(recents),
                RemoveRecentDocumentUseCase(recents),
                GetDocumentNameUseCase(recents, FakeDocumentNameResolver()),
                this,
            ),
        ).also { advanceUntilIdle() }
    }

    @Test
    fun `a search starts from the page shown and finds the occurrences of the document`() = runTest(dispatcher) {
        val hits = listOf(SearchHit(1, listOf(area)), SearchHit(4, listOf(area)))
        document.searchHits = { query -> if (query == "bail") hits else emptyList() }
        val viewModel = openViewModel()
        viewModel.onPageChange(3)
        val search = viewModel.searchController

        search.onOpen()
        search.onQueryChange("bail")
        search.onSubmit()
        advanceUntilIdle()

        assertThat(document.searches.single().startPage).isEqualTo(3)
        with(search.state.value) {
            assertThat(status).isEqualTo(SearchStatus.Done)
            assertThat(results.hits.map { it.pageIndex }).containsExactly(1, 4).inOrder()
            assertThat(pageCount).isEqualTo(5)
            // The first occurrence after the page shown.
            assertThat(currentHit?.pageIndex).isEqualTo(4)
        }
    }

    @Test
    fun `a search after the document closed fails without crashing`() = runTest(dispatcher) {
        val viewModel = openViewModel()
        document.close()

        viewModel.searchController.onQueryChange("bail")
        advanceUntilIdle()

        assertThat(viewModel.searchController.state.value.status).isEqualTo(SearchStatus.Failed)
    }
}
