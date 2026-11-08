// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.domain.settings.SetScrollModeUseCase
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import io.github.myxomatom.lutrin.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val uri = "content://com.example.provider/document/contrat%20de%20location.pdf"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(
        engine: FakePdfEngine,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        page: Int? = null,
    ) = ReaderViewModel(
        SavedStateHandle(listOfNotNull(READER_URI_ARG to uri, page?.let { READER_PAGE_ARG to it }).toMap()),
        engine,
        dispatcher,
        ObserveSettingsUseCase(settings),
        SetScrollModeUseCase(settings),
    )

    private fun TestScope.viewModel(
        engine: FakePdfEngine,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): ReaderViewModel = newViewModel(engine, settings).also { advanceUntilIdle() }

    private fun TestScope.openDocument(
        pageCount: Int = 3,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): ReaderViewModel = viewModel(
        FakePdfEngine(
            Result.success(
                FakePdfDocument(
                    pageSizes = List(pageCount) {
                        A4
                    },
                ),
            ),
        ),
        settings,
    )

    private val ReaderViewModel.ready: ReaderUiState.Ready get() = uiState.value as ReaderUiState.Ready

    @Test
    fun `starts loading and opens the document from its URI`() = runTest(dispatcher) {
        val engine = FakePdfEngine()
        val viewModel = newViewModel(engine)

        assertThat(viewModel.uiState.value).isEqualTo(ReaderUiState.Loading)
        advanceUntilIdle()

        val source = engine.openedSources.single() as DocumentSource.FromUri
        assertThat(source.uri.toString()).isEqualTo(uri)
        assertThat(viewModel.uiState.value).isInstanceOf(ReaderUiState.Ready::class.java)
    }

    @Test
    fun `ready state lists the page sizes and the title`() = runTest(dispatcher) {
        val document = FakePdfDocument(pageSizes = listOf(A4, A4), title = "Contrat")

        val state = viewModel(FakePdfEngine(Result.success(document))).uiState.value as ReaderUiState.Ready

        assertThat(state.title).isEqualTo("Contrat")
        assertThat(state.pageSizes).containsExactly(A4, A4)
    }

    @Test
    fun `falls back to the file name when the document has no title`() = runTest(dispatcher) {
        val document = FakePdfDocument(title = null)

        val state = viewModel(FakePdfEngine(Result.success(document))).uiState.value as ReaderUiState.Ready

        assertThat(state.title).isEqualTo("contrat de location.pdf")
    }

    @Test
    fun `maps every engine error to a reader error`() = runTest(dispatcher) {
        val cases =
            mapOf(
                PdfError.PasswordRequired() to ReaderError.PASSWORD_REQUIRED,
                PdfError.IncorrectPassword() to ReaderError.PASSWORD_REQUIRED,
                PdfError.UnsupportedFormat() to ReaderError.UNSUPPORTED_FORMAT,
                PdfError.NotAccessible() to ReaderError.NOT_ACCESSIBLE,
                PdfError.Unreadable() to ReaderError.UNREADABLE,
                IllegalStateException("unexpected") to ReaderError.UNREADABLE,
            )
        cases.forEach { (error, expected) ->
            val state = viewModel(FakePdfEngine(Result.failure(error))).uiState.value

            assertThat(state).isEqualTo(ReaderUiState.Error(expected))
        }
    }

    @Test
    fun `closes the document when the reader goes away`() = runTest(dispatcher) {
        val document = FakePdfDocument()
        val store = ViewModelStore()
        store.put("reader", viewModel(FakePdfEngine(Result.success(document))))

        store.clear()

        assertThat(document.isClosed).isTrue()
    }

    @Test
    fun `display name decodes the last path segment`() {
        assertThat(
            displayName("content://provider/tree/primary%3ADocs%2Frapport%20final.pdf"),
        ).isEqualTo("rapport final.pdf")
        assertThat(displayName("file:///sdcard/a.pdf")).isEqualTo("a.pdf")
    }

    @Test
    fun `starts on the first page, scrolling continuously by default`() = runTest(dispatcher) {
        val state = openDocument().ready

        assertThat(state.scrollMode).isEqualTo(ScrollMode.CONTINUOUS)
        assertThat(state.currentPage).isEqualTo(0)
    }

    @Test
    fun `exposes the stored scroll mode`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository(AppSettings(scrollMode = ScrollMode.PAGED))

        assertThat(openDocument(settings = settings).ready.scrollMode).isEqualTo(ScrollMode.PAGED)
    }

    @Test
    fun `choosing a scroll mode stores it and shows it`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = openDocument(settings = settings)

        viewModel.onScrollModeChange(ScrollMode.PAGED)
        advanceUntilIdle()

        assertThat(settings.settings.value.scrollMode).isEqualTo(ScrollMode.PAGED)
        assertThat(viewModel.ready.scrollMode).isEqualTo(ScrollMode.PAGED)
    }

    @Test
    fun `switching scroll mode keeps the current page`() = runTest(dispatcher) {
        val viewModel = openDocument(pageCount = 5)

        viewModel.onPageChange(3)
        viewModel.onScrollModeChange(ScrollMode.PAGED)
        advanceUntilIdle()

        assertThat(viewModel.ready.scrollMode).isEqualTo(ScrollMode.PAGED)
        assertThat(viewModel.ready.currentPage).isEqualTo(3)

        viewModel.onScrollModeChange(ScrollMode.CONTINUOUS)
        advanceUntilIdle()

        assertThat(viewModel.ready.currentPage).isEqualTo(3)
    }

    @Test
    fun `switching scroll mode on the last page stays on it`() = runTest(dispatcher) {
        val viewModel = openDocument(pageCount = 3)

        viewModel.onPageChange(2)
        viewModel.onScrollModeChange(ScrollMode.PAGED)
        advanceUntilIdle()

        assertThat(viewModel.ready.currentPage).isEqualTo(2)
    }

    @Test
    fun `a reported page is clamped to the document`() = runTest(dispatcher) {
        val viewModel = openDocument(pageCount = 3)

        viewModel.onPageChange(7)
        advanceUntilIdle()
        assertThat(viewModel.ready.currentPage).isEqualTo(2)

        viewModel.onPageChange(-1)
        advanceUntilIdle()
        assertThat(viewModel.ready.currentPage).isEqualTo(0)
    }

    @Test
    fun `a one-page document always stays on its page`() = runTest(dispatcher) {
        val viewModel = openDocument(pageCount = 1)

        viewModel.onPageChange(1)
        viewModel.onScrollModeChange(ScrollMode.PAGED)
        advanceUntilIdle()

        assertThat(viewModel.ready.currentPage).isEqualTo(0)
        assertThat(viewModel.ready.scrollMode).isEqualTo(ScrollMode.PAGED)
    }

    @Test
    fun `a page reported before the document is open is ignored`() = runTest(dispatcher) {
        val viewModel = newViewModel(FakePdfEngine())

        viewModel.onPageChange(2)
        advanceUntilIdle()

        assertThat(viewModel.ready.currentPage).isEqualTo(0)
    }

    @Test
    fun `an error stays an error whatever the scroll mode`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(FakePdfEngine(Result.failure(PdfError.Unreadable())), settings)

        viewModel.onScrollModeChange(ScrollMode.PAGED)
        viewModel.onPageChange(1)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(ReaderUiState.Error(ReaderError.UNREADABLE))
        assertThat(settings.settings.value.scrollMode).isEqualTo(ScrollMode.PAGED)
    }

    private fun TestScope.openAtPage(page: Int?, pageCount: Int = 5): ReaderViewModel =
        newViewModel(FakePdfEngine(Result.success(FakePdfDocument(List(pageCount) { A4 }))), page = page)
            .also { advanceUntilIdle() }

    @Test
    fun `opens at the page given by the navigation`() = runTest(dispatcher) {
        assertThat(openAtPage(page = 3).ready.currentPage).isEqualTo(3)
    }

    @Test
    fun `opens at the first page without a page argument`() = runTest(dispatcher) {
        assertThat(openAtPage(page = null).ready.currentPage).isEqualTo(0)
    }

    @Test
    fun `a page beyond the document opens the last page`() = runTest(dispatcher) {
        assertThat(openAtPage(page = 5, pageCount = 5).ready.currentPage).isEqualTo(4)
        assertThat(openAtPage(page = Int.MAX_VALUE, pageCount = 5).ready.currentPage).isEqualTo(4)
    }

    @Test
    fun `a negative page opens the first page`() = runTest(dispatcher) {
        assertThat(openAtPage(page = -3).ready.currentPage).isEqualTo(0)
    }

    @Test
    fun `the first ready state already shows the requested page`() = runTest(dispatcher) {
        val viewModel = newViewModel(FakePdfEngine(Result.success(FakePdfDocument(List(5) { A4 }))), page = 2)
        val states = mutableListOf<ReaderUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { states += it } }

        advanceUntilIdle()

        assertThat(states.filterIsInstance<ReaderUiState.Ready>().map { it.currentPage }).containsExactly(2)
    }
}
