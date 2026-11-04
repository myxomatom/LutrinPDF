// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import kotlin.reflect.KClass
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val document = FakePdfDocument(pageSizes = listOf(PageSize(600f, 800f), PageSize(800f, 600f)))
    private val engine = FakePdfEngine(Result.success(document))
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(uri: String? = URI, title: String = TITLE): ReaderViewModel {
        val args = SavedStateHandle(
            buildMap {
                uri?.let { put(ReaderDestination.ARG_URI, it) }
                put(ReaderDestination.ARG_TITLE, title)
            },
        )
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return ReaderViewModel(args, engine, PageBitmapCache(CACHE_BYTES)) as T
            }
        }
        return ViewModelProvider.create(store, factory)["$uri|$title", ReaderViewModel::class]
    }

    @Test
    fun `shows the pages of the opened document`() {
        val state = viewModel().uiState.value

        assertThat(state).isEqualTo(ReaderUiState.Content(TITLE, listOf(PageSize(600f, 800f), PageSize(800f, 600f))))
        assertThat((engine.openedSources.single() as DocumentSource.FromUri).uri.toString()).isEqualTo(URI)
    }

    @Test
    fun `is loading until the engine answers`() {
        engine.gate = CompletableDeferred()

        val viewModel = viewModel()

        assertThat(viewModel.uiState.value).isEqualTo(ReaderUiState.Loading(TITLE))
        engine.gate?.complete(Unit)
        assertThat(viewModel.uiState.value).isInstanceOf(ReaderUiState.Content::class.java)
    }

    @Test
    fun `reports a missing uri as not accessible without calling the engine`() {
        val state = viewModel(uri = null).uiState.value

        assertThat(state).isEqualTo(ReaderUiState.Error(TITLE, ReaderError.NOT_ACCESSIBLE))
        assertThat(engine.openedSources).isEmpty()
    }

    @Test
    fun `reports a corrupt document as unreadable`() {
        engine.result = Result.failure(PdfError.Unreadable())

        assertThat(viewModel().uiState.value).isEqualTo(ReaderUiState.Error(TITLE, ReaderError.UNREADABLE))
    }

    @Test
    fun `reports a revoked permission as not accessible`() {
        engine.result = Result.failure(PdfError.NotAccessible())

        assertThat(viewModel().uiState.value).isEqualTo(ReaderUiState.Error(TITLE, ReaderError.NOT_ACCESSIBLE))
    }

    @Test
    fun `reports an encrypted document as password protected`() {
        engine.result = Result.failure(PdfError.PasswordRequired())

        assertThat(viewModel().uiState.value).isEqualTo(ReaderUiState.Error(TITLE, ReaderError.PASSWORD_PROTECTED))
    }

    @Test
    fun `maps every engine error to a message`() {
        assertThat(PdfError.IncorrectPassword().toReaderError()).isEqualTo(ReaderError.PASSWORD_PROTECTED)
        assertThat(PdfError.UnsupportedFormat().toReaderError()).isEqualTo(ReaderError.UNREADABLE)
        assertThat(PdfError.Closed().toReaderError()).isEqualTo(ReaderError.UNREADABLE)
        assertThat(IllegalStateException().toReaderError()).isEqualTo(ReaderError.UNREADABLE)
    }

    @Test
    fun `renders a page at the requested width and caches it`() = runTest {
        val viewModel = viewModel()

        val first = viewModel.loadPage(0, WIDTH_PX)
        val second = viewModel.loadPage(0, WIDTH_PX)

        assertThat(first?.width).isEqualTo(WIDTH_PX)
        assertThat(first?.height).isEqualTo(WIDTH_PX * 800 / 600)
        assertThat(second).isNotNull()
        assertThat(document.renderCount).isEqualTo(1)
    }

    @Test
    fun `renders again at another width`() = runTest {
        val viewModel = viewModel()

        viewModel.loadPage(1, WIDTH_PX)
        viewModel.loadPage(1, WIDTH_PX * 2)

        assertThat(document.renderCount).isEqualTo(2)
    }

    @Test
    fun `leaves a page blank when it cannot be rendered`() = runTest {
        document.renderFailure = PdfError.Unreadable()

        assertThat(viewModel().loadPage(0, WIDTH_PX)).isNull()
    }

    @Test
    fun `returns no page outside the document or before it is open`() = runTest {
        assertThat(viewModel().loadPage(2, WIDTH_PX)).isNull()
        assertThat(viewModel(uri = null, title = "other").loadPage(0, WIDTH_PX)).isNull()
    }

    @Test
    fun `closes the document when the screen goes away`() {
        viewModel()

        store.clear()

        assertThat(document.isClosed).isTrue()
    }

    private companion object {
        const val URI = "content://documents/document/42"
        const val TITLE = "contrat.pdf"
        const val WIDTH_PX = 300
        const val CACHE_BYTES = 8 * 1024 * 1024
    }
}
