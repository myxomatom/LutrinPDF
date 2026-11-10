// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.recents.DocumentOpening
import io.github.myxomatom.lutrin.core.domain.recents.ObserveRecentDocumentsUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocument
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocumentsRepository
import io.github.myxomatom.lutrin.core.domain.recents.RemoveRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.UpdateThumbnailUseCase
import io.github.myxomatom.lutrin.core.domain.recents.documentIdFor
import io.github.myxomatom.lutrin.core.testing.FAKE_NOW
import io.github.myxomatom.lutrin.core.testing.FakeRecentDocumentsRepository
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
// Real image decoding, so that an unreadable file really gives no picture.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(FAKE_NOW, ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // The test scope stands for the application scope: advanceUntilIdle runs the removals.
    private fun TestScope.viewModel(repository: RecentDocumentsRepository) = LibraryViewModel(
        ObserveRecentDocumentsUseCase(repository),
        UpdateThumbnailUseCase(repository),
        RemoveRecentDocumentUseCase(repository),
        ThumbnailLoader(StandardTestDispatcher(testScheduler)),
        clock,
        this,
    )

    private fun document(
        id: Int,
        openedAt: Instant = FAKE_NOW,
        thumbnail: String? = null,
        pageCount: Int = 8,
        lastPage: Int = 2,
    ) = RecentDocument(
        documentId = documentIdFor(uri(id)),
        uri = uri(id),
        displayName = "Document $id",
        sizeBytes = null,
        pageCount = pageCount,
        lastOpenedAt = openedAt,
        lastPage = lastPage,
        thumbnailPath = thumbnail,
    )

    /** Every document shown, the one of the resume card first. */
    private val LibraryUiState.Content.documents get() = listOf(resume) + others

    @Test
    fun `starts loading, then shows the empty state without recent documents`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRecentDocumentsRepository())

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(LibraryUiState.Loading)
            assertThat(awaitItem()).isEqualTo(LibraryUiState.Empty)
        }
    }

    @Test
    fun `the most recent document goes to the resume card, the others to the list in order`() = runTest(dispatcher) {
        val repository =
            FakeRecentDocumentsRepository(listOf(document(3, thumbnail = "/thumbs/3.png"), document(2), document(1)))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            skipItems(1)
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.resume.uri).isEqualTo(uri(3))
            assertThat(content.others.map { it.uri }).containsExactly(uri(2), uri(1)).inOrder()
            assertThat(content.resume)
                .isEqualTo(
                    RecentDocumentItem(
                        documentId = documentIdFor(uri(3)),
                        uri = uri(3),
                        name = "Document 3",
                        pageCount = 8,
                        lastPage = 2,
                        openedOn = OpenedOn.Today(LocalTime.of(20, 41)),
                        thumbnailPath = "/thumbs/3.png",
                        // Never drawn since the opening (no date): to draw again.
                        thumbnailOutdated = true,
                        isReadingInProgress = true,
                    ),
                )
        }
    }

    @Test
    fun `follows the changes of the recent list`() = runTest(dispatcher) {
        val repository = FakeRecentDocumentsRepository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            skipItems(2)
            repository.recordOpening(DocumentOpening(uri(1), pageCount = 3, page = 0, fallbackName = "Contrat"))
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.documents.single().name).isEqualTo("Contrat")
        }
    }

    @Test
    fun `a list that cannot be read shows the error state`() = runTest(dispatcher) {
        val failing =
            object : RecentDocumentsRepository by FakeRecentDocumentsRepository() {
                override val recentDocuments: Flow<List<RecentDocument>> = flow { throw IOException("disk") }
            }

        viewModel(failing).uiState.test {
            skipItems(1)
            assertThat(awaitItem()).isEqualTo(LibraryUiState.Error)
        }
    }

    @Test
    fun `an outdated thumbnail is asked for once per opening`() = runTest(dispatcher) {
        val repository = FakeRecentDocumentsRepository(listOf(document(1)))
        val viewModel = viewModel(repository)
        val id = documentIdFor(uri(1))
        val request = FakeRecentDocumentsRepository.ThumbnailRequest(id, onlyIfOutdated = true)

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onThumbnailOutdated(id)
            viewModel.onThumbnailOutdated(id)
            advanceUntilIdle()
            assertThat(repository.thumbnailRequests).containsExactly(request)

            repository.clock = Clock.offset(clock, Duration.ofMinutes(5))
            repository.recordOpening(DocumentOpening(uri(1), pageCount = 8, page = 4, fallbackName = "Document 1"))
            skipItems(1)
            viewModel.onThumbnailOutdated(id)
            advanceUntilIdle()
            assertThat(repository.thumbnailRequests).containsExactly(request, request)
        }
    }

    @Test
    fun `an up-to-date thumbnail is not outdated`() = runTest(dispatcher) {
        val drawn = document(1, thumbnail = "/thumbs/1.png").copy(thumbnailDrawnAt = FAKE_NOW)

        viewModel(FakeRecentDocumentsRepository(listOf(drawn))).uiState.test {
            skipItems(1)
            assertThat((awaitItem() as LibraryUiState.Content).documents.single().thumbnailOutdated).isFalse()
        }
    }

    @Test
    fun `a protected document is shown with its lock and needs no thumbnail`() = runTest(dispatcher) {
        val protected = document(1).copy(isProtected = true)

        viewModel(FakeRecentDocumentsRepository(listOf(protected))).uiState.test {
            skipItems(1)
            val item = (awaitItem() as LibraryUiState.Content).documents.single()
            assertThat(item.isProtected).isTrue()
            assertThat(item.thumbnailOutdated).isFalse()
            assertThat(item.thumbnailPath).isNull()
        }
    }

    @Test
    fun `a thumbnail file that cannot be read gives no picture`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRecentDocumentsRepository())

        assertThat(viewModel.loadThumbnail("/no/such/file.png", IntSize(208, 272))).isNull()
    }

    @Test
    fun `opening dates are relative to today in the clock's time zone`() {
        val paris = ZoneId.of("Europe/Paris")
        val now = Clock.fixed(Instant.parse("2026-11-09T23:30:00Z"), paris) // 10 November, 00:30 in Paris
        fun at(text: String) = openedOn(Instant.parse(text), now)

        assertThat(at("2026-11-09T23:10:00Z")).isEqualTo(OpenedOn.Today(LocalTime.of(0, 10)))
        assertThat(at("2026-11-09T20:41:00Z")).isEqualTo(OpenedOn.Yesterday)
        assertThat(at("2026-11-08T23:01:00Z")).isEqualTo(OpenedOn.Yesterday)
        assertThat(at("2026-11-08T22:59:00Z")).isEqualTo(OpenedOn.ThisYear(LocalDate.of(2026, 11, 8)))
        assertThat(at("2026-10-28T10:00:00Z")).isEqualTo(OpenedOn.ThisYear(LocalDate.of(2026, 10, 28)))
        assertThat(at("2025-12-31T10:00:00Z")).isEqualTo(OpenedOn.OtherYear(LocalDate.of(2025, 12, 31)))
        // A clock set back: a date "in the future" is written in full.
        assertThat(openedOn(now.instant().plus(Duration.ofDays(3)), now))
            .isEqualTo(OpenedOn.OtherYear(LocalDate.of(2026, 11, 13)))
    }

    @Test
    fun `progress goes from the first page to the whole document`() {
        val item = RecentDocumentItem("id", uri(1), "Doc", pageCount = 4, lastPage = 0, OpenedOn.Yesterday, null)

        assertThat(item.progress).isEqualTo(0.25f)
        assertThat(item.copy(lastPage = 3).progress).isEqualTo(1f)
    }

    @Test
    fun `a single document takes the card and leaves the list empty`() = runTest(dispatcher) {
        viewModel(FakeRecentDocumentsRepository(listOf(document(1)))).uiState.test {
            skipItems(1)
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.resume.uri).isEqualTo(uri(1))
            assertThat(content.others).isEmpty()
        }
    }

    @Test
    fun `a protected document most recently opened still takes the card`() = runTest(dispatcher) {
        val protected = document(2).copy(isProtected = true)

        viewModel(FakeRecentDocumentsRepository(listOf(protected, document(1)))).uiState.test {
            skipItems(1)
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.resume.uri).isEqualTo(uri(2))
            assertThat(content.resume.isProtected).isTrue()
            assertThat(content.others.map { it.uri }).containsExactly(uri(1))
        }
    }

    @Test
    fun `only a reading stopped before the last page is in progress`() = runTest(dispatcher) {
        val documents =
            listOf(
                document(4, pageCount = 8, lastPage = 0),
                document(3, pageCount = 8, lastPage = 5),
                document(2, pageCount = 8, lastPage = 7),
                document(1, pageCount = 1, lastPage = 0),
            )

        viewModel(FakeRecentDocumentsRepository(documents)).uiState.test {
            skipItems(1)
            val content = awaitItem() as LibraryUiState.Content
            // First page of several, middle: in progress. Last page, single page: not.
            assertThat(content.documents.map { it.isReadingInProgress }).containsExactly(true, true, false, false)
                .inOrder()
        }
    }

    @Test
    fun `removing the document of the card brings up the next one, and undoing puts it back`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRecentDocumentsRepository(listOf(document(3), document(2), document(1))))

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onRemove(documentIdFor(uri(3)))

            val removed = awaitItem() as LibraryUiState.Content
            assertThat(removed.resume.uri).isEqualTo(uri(2))
            assertThat(removed.others.map { it.uri }).containsExactly(uri(1))

            viewModel.onUndoRemoval(removal(3))
            val restored = awaitItem() as LibraryUiState.Content
            assertThat(restored.resume.uri).isEqualTo(uri(3))
            assertThat(restored.others.map { it.uri }).containsExactly(uri(2), uri(1)).inOrder()
        }
    }

    @Test
    fun `removing a document of the list keeps the card`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRecentDocumentsRepository(listOf(document(2), document(1))))

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onRemove(documentIdFor(uri(1)))

            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.resume.uri).isEqualTo(uri(2))
            assertThat(content.others).isEmpty()
        }
    }

    private companion object {
        fun uri(id: Int) = "content://com.example.files/document/$id"
    }

    @Test
    fun `removing hides the row at once and offers to undo it`() = runTest(dispatcher) {
        val repository = FakeRecentDocumentsRepository(listOf(document(2), document(1)))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onRemove(documentIdFor(uri(2)))

            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.documents.map { it.uri }).containsExactly(uri(1))
            assertThat(viewModel.pendingRemoval.value).isEqualTo(removal(2))
            advanceUntilIdle()
            assertThat(repository.removals).isEmpty()
            assertThat(repository.recentDocuments.value).hasSize(2)
        }
    }

    @Test
    fun `undoing brings the row back and removes nothing`() = runTest(dispatcher) {
        val repository = FakeRecentDocumentsRepository(listOf(document(2), document(1)))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onRemove(documentIdFor(uri(2)))
            skipItems(1)

            viewModel.onUndoRemoval(removal(2))

            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.documents.map { it.uri }).containsExactly(uri(2), uri(1)).inOrder()
            assertThat(viewModel.pendingRemoval.value).isNull()
            advanceUntilIdle()
            assertThat(repository.removals).isEmpty()
        }
    }

    @Test
    fun `confirming removes the entry as it was when removed`() = runTest(dispatcher) {
        val repository = FakeRecentDocumentsRepository(listOf(document(2), document(1)))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onRemove(documentIdFor(uri(2)))
            skipItems(1)

            viewModel.onConfirmRemoval(removal(2))
            advanceUntilIdle()

            assertThat(repository.removals).containsExactly(documentIdFor(uri(2)) to FAKE_NOW)
            assertThat(repository.recentDocuments.value.map { it.uri }).containsExactly(uri(1))
            assertThat(viewModel.pendingRemoval.value).isNull()
            // The row stays hidden: no flash of the removed row.
            expectNoEvents()
        }
    }

    @Test
    fun `removing the last row shows the empty state`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRecentDocumentsRepository(listOf(document(1))))

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onRemove(documentIdFor(uri(1)))

            assertThat(awaitItem()).isEqualTo(LibraryUiState.Empty)
        }
    }

    @Test
    fun `a second removal confirms the first one`() = runTest(dispatcher) {
        val repository = FakeRecentDocumentsRepository(listOf(document(3), document(2), document(1)))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onRemove(documentIdFor(uri(3)))
            viewModel.onRemove(documentIdFor(uri(2)))
            advanceUntilIdle()

            assertThat(repository.removals).containsExactly(documentIdFor(uri(3)) to FAKE_NOW)
            assertThat(viewModel.pendingRemoval.value).isEqualTo(removal(2))
            // The first message, cancelled by the second one, confirms nothing more.
            viewModel.onConfirmRemoval(removal(3))
            viewModel.onUndoRemoval(removal(3))
            advanceUntilIdle()
            assertThat(repository.removals).hasSize(1)
            assertThat(viewModel.pendingRemoval.value).isEqualTo(removal(2))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing a row that is not shown does nothing`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRecentDocumentsRepository(listOf(document(1))))

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onRemove("unknown")

            assertThat(viewModel.pendingRemoval.value).isNull()
            expectNoEvents()
        }
    }

    @Test
    fun `a document opened again during the undo delay shows again and is kept`() = runTest(dispatcher) {
        val repository = FakeRecentDocumentsRepository(listOf(document(1)))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onRemove(documentIdFor(uri(1)))
            assertThat(awaitItem()).isEqualTo(LibraryUiState.Empty)

            repository.clock = Clock.offset(clock, Duration.ofMinutes(1))
            repository.recordOpening(DocumentOpening(uri(1), pageCount = 8, page = 0, fallbackName = "Document 1"))
            assertThat((awaitItem() as LibraryUiState.Content).documents.single().uri).isEqualTo(uri(1))

            viewModel.onConfirmRemoval(removal(1))
            advanceUntilIdle()
            assertThat(repository.recentDocuments.value.map { it.uri }).containsExactly(uri(1))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a removal still pending is confirmed when the view model is cleared`() = runTest(dispatcher) {
        val repository = FakeRecentDocumentsRepository(listOf(document(1)))
        val store = ViewModelStore()
        val created = viewModel(repository)
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = created as T
                },
            )[LibraryViewModel::class.java]

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onRemove(documentIdFor(uri(1)))
            skipItems(1)
            cancelAndIgnoreRemainingEvents()
        }
        store.clear()
        advanceUntilIdle()

        assertThat(repository.removals).containsExactly(documentIdFor(uri(1)) to FAKE_NOW)
        assertThat(repository.recentDocuments.value).isEmpty()
    }

    private fun removal(id: Int) = PendingRemoval(documentIdFor(uri(id)), "Document $id", FAKE_NOW)
}
