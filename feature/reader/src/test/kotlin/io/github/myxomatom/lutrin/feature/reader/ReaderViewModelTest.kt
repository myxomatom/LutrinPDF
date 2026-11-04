// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val uri = "content://com.example.provider/document/contrat%20de%20location.pdf"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(engine: FakePdfEngine): ReaderViewModel =
        ReaderViewModel(SavedStateHandle(mapOf(READER_URI_ARG to uri)), engine, dispatcher).also { advanceUntilIdle() }

    @Test
    fun `starts loading and opens the document from its URI`() = runTest(dispatcher) {
        val engine = FakePdfEngine()
        val viewModel = ReaderViewModel(SavedStateHandle(mapOf(READER_URI_ARG to uri)), engine, dispatcher)

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
}
