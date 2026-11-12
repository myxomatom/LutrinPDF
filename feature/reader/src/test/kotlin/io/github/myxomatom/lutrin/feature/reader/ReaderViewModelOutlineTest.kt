// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.GetDocumentNameUseCase
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.recents.GetStartPageUseCase
import io.github.myxomatom.lutrin.core.domain.recents.IsRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecordDocumentOpeningUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RemoveRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.SaveLastPageUseCase
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetScrollModeUseCase
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakeDocumentNameResolver
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import io.github.myxomatom.lutrin.core.testing.FakeRecentDocumentsRepository
import io.github.myxomatom.lutrin.core.testing.FakeSettingsRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
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

/** The table of contents of [ReaderViewModel] (F06, ADR 0021). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderViewModelOutlineTest {
    private val dispatcher = StandardTestDispatcher()
    private val uri = "content://com.example.provider/document/contrat%20de%20location.pdf"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Counts what is run through it, to see that the outline is read off the main dispatcher. */
    private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        val dispatched = AtomicInteger()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatched.incrementAndGet()
            delegate.dispatch(context, block)
        }
    }

    private fun TestScope.newViewModel(document: PdfDocument, work: CoroutineDispatcher = dispatcher): ReaderViewModel {
        val recents = FakeRecentDocumentsRepository()
        val settings = FakeSettingsRepository()
        return ReaderViewModel(
            SavedStateHandle(mapOf(READER_URI_ARG to uri)),
            FakePdfEngine(Result.success(document)),
            work,
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
        )
    }

    private fun TestScope.open(outline: List<OutlineItem>, pageCount: Int = 8): ReaderViewModel {
        val document = FakePdfDocument(pageSizes = List(pageCount) { A4 }).apply { this.outline = outline }
        return newViewModel(document).also { advanceUntilIdle() }
    }

    private val ReaderViewModel.ready: ReaderUiState.Ready get() = uiState.value as ReaderUiState.Ready

    private fun entry(
        title: String,
        page: Int?,
        y: Float? = null,
        uri: String? = null,
        kids: List<OutlineItem> = emptyList(),
    ) = OutlineItem(title, page?.let { PdfDestination(it, y = y) }, uri, kids)

    /** Three levels: « 2. Durée » (page 2) holds « 2.1 » and « 2.2 », which holds « 2.2.1 ». */
    private val nested =
        listOf(
            entry("1. Objet", page = 0),
            entry(
                "2. Durée",
                page = 1,
                kids =
                    listOf(
                        entry("2.1 Date", page = 1, y = 300f),
                        entry("2.2 Renouvellement", page = 2, kids = listOf(entry("2.2.1 Tacite", page = 3))),
                    ),
            ),
            entry("3. Loyer", page = 5),
        )

    @Test
    fun `a document without outline has an empty one`() = runTest(dispatcher) {
        val viewModel = open(emptyList())

        assertThat(viewModel.ready.outline.isEmpty).isTrue()
        assertThat(viewModel.ready.outline.currentSection).isNull()
    }

    @Test
    fun `flattens a three level outline`() = runTest(dispatcher) {
        val outline = open(nested).ready.outline

        assertThat(outline.entries.map { it.title })
            .containsExactly("1. Objet", "2. Durée", "2.1 Date", "2.2 Renouvellement", "2.2.1 Tacite", "3. Loyer")
            .inOrder()
        assertThat(outline.entries.map { it.level }).containsExactly(0, 0, 1, 1, 2, 0).inOrder()
        assertThat(outline.entries[2].target).isEqualTo(OutlineTarget.Page(1, 300f))
    }

    @Test
    fun `an entry without destination is listed and leads nowhere`() = runTest(dispatcher) {
        val outline = open(listOf(entry("Titre seul", page = null), entry("Chapitre", page = 2))).ready.outline

        assertThat(outline.entries.map { it.title }).containsExactly("Titre seul", "Chapitre").inOrder()
        assertThat(outline.entries.first().target).isNull()
    }

    @Test
    fun `an external entry carries its address and the document stays open`() = runTest(dispatcher) {
        val viewModel = open(listOf(entry("Site", page = null, uri = "https://example.org/")))

        assertThat(viewModel.ready.outline.entries.single().target)
            .isEqualTo(OutlineTarget.External("https://example.org/"))
        viewModel.onExternalOutlineEntry("https://example.org/")
        advanceUntilIdle()

        // The address waits for its confirmation in V7 (ADR 0022); nothing is opened yet.
        assertThat(viewModel.linkController.externalLink.value).isEqualTo("https://example.org/")
        assertThat(viewModel.uiState.value).isInstanceOf(ReaderUiState.Ready::class.java)
        assertThat(viewModel.ready.outline.entries).hasSize(1)
        assertThat(viewModel.ready.currentPage).isEqualTo(0)
    }

    @Test
    fun `an external entry with a refused scheme is reported, never confirmed`() = runTest(dispatcher) {
        val viewModel = open(listOf(entry("Script", page = null, uri = "javascript:alert(1)")))

        viewModel.linkController.effects.test {
            viewModel.onExternalOutlineEntry("javascript:alert(1)")

            assertThat(awaitItem()).isEqualTo(LinkEffect.LinkRefused)
        }
        assertThat(viewModel.linkController.externalLink.value).isNull()
    }

    @Test
    fun `the current section follows the page`() = runTest(dispatcher) {
        val viewModel = open(nested)

        assertThat(viewModel.ready.outline.currentSection).isEqualTo(0)

        viewModel.onPageChange(1)
        advanceUntilIdle()
        assertThat(viewModel.ready.outline.currentSection).isEqualTo(2)

        viewModel.onPageChange(4)
        advanceUntilIdle()
        assertThat(viewModel.ready.outline.currentSection).isEqualTo(4)
        assertThat(viewModel.ready.outline.sectionTitleAt(7)).isEqualTo("3. Loyer")
    }

    @Test
    fun `opening the outline expands the path to the current section`() = runTest(dispatcher) {
        val viewModel = open(nested)
        viewModel.onPageChange(3)
        advanceUntilIdle()

        viewModel.onOutlineOpened()
        advanceUntilIdle()

        assertThat(viewModel.ready.outline.expanded).containsExactly(1, 3)
    }

    @Test
    fun `opening the outline keeps the sections the user expanded`() = runTest(dispatcher) {
        val viewModel = open(nested)
        viewModel.onOutlineToggle(1)

        viewModel.onOutlineOpened()
        advanceUntilIdle()

        // On page 1 (« 1. Objet »), nothing else needs to open; « 2. Durée » stays as it was.
        assertThat(viewModel.ready.outline.expanded).containsExactly(1)
    }

    @Test
    fun `toggling expands then collapses an entry`() = runTest(dispatcher) {
        val viewModel = open(nested)

        viewModel.onOutlineToggle(1)
        advanceUntilIdle()
        assertThat(viewModel.ready.outline.expanded).containsExactly(1)

        viewModel.onOutlineToggle(1)
        advanceUntilIdle()
        assertThat(viewModel.ready.outline.expanded).isEmpty()
    }

    @Test
    fun `toggling a leaf does nothing`() = runTest(dispatcher) {
        val viewModel = open(nested)

        viewModel.onOutlineToggle(0)
        advanceUntilIdle()

        assertThat(viewModel.ready.outline.expanded).isEmpty()
    }

    @Test
    fun `an outline that cannot be read leaves the document open without one`() = runTest(dispatcher) {
        val fake = FakePdfDocument()
        val broken =
            object : PdfDocument by fake {
                override suspend fun outline() = throw PdfError.Unreadable()
            }
        val viewModel = newViewModel(broken).also { advanceUntilIdle() }

        assertThat(viewModel.uiState.value).isInstanceOf(ReaderUiState.Ready::class.java)
        assertThat(viewModel.ready.outline.isEmpty).isTrue()
    }

    @Test
    fun `the outline is read through the injected dispatcher`() = runTest(dispatcher) {
        val work = CountingDispatcher(dispatcher)

        newViewModel(FakePdfDocument().apply { outline = nested }, work).also { advanceUntilIdle() }

        assertThat(work.dispatched.get()).isGreaterThan(0)
    }

    @Test
    fun `a destination past the last page is not a place to go to`() = runTest(dispatcher) {
        val outline = open(listOf(entry("Trop loin", page = 40)), pageCount = 3).ready.outline

        assertThat(outline.entries.single().target).isNull()
    }
}
