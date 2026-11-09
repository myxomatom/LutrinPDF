// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.recents.DocumentOpening
import io.github.myxomatom.lutrin.core.domain.recents.ObserveRecentDocumentsUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocument
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocumentsRepository
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

    private fun TestScope.viewModel(repository: RecentDocumentsRepository) = LibraryViewModel(
        ObserveRecentDocumentsUseCase(repository),
        UpdateThumbnailUseCase(repository),
        ThumbnailLoader(StandardTestDispatcher(testScheduler)),
        clock,
    )

    private fun document(id: Int, openedAt: Instant = FAKE_NOW, thumbnail: String? = null) = RecentDocument(
        documentId = documentIdFor(uri(id)),
        uri = uri(id),
        displayName = "Document $id",
        sizeBytes = null,
        pageCount = 8,
        lastOpenedAt = openedAt,
        lastPage = 2,
        thumbnailPath = thumbnail,
    )

    @Test
    fun `starts loading, then shows the empty state without recent documents`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRecentDocumentsRepository())

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(LibraryUiState.Loading)
            assertThat(awaitItem()).isEqualTo(LibraryUiState.Empty)
        }
    }

    @Test
    fun `lists the recent documents in the repository order`() = runTest(dispatcher) {
        val repository = FakeRecentDocumentsRepository(listOf(document(2, thumbnail = "/thumbs/2.png"), document(1)))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            skipItems(1)
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.documents.map { it.uri }).containsExactly(uri(2), uri(1)).inOrder()
            assertThat(content.documents.first())
                .isEqualTo(
                    RecentDocumentItem(
                        documentId = documentIdFor(uri(2)),
                        uri = uri(2),
                        name = "Document 2",
                        pageCount = 8,
                        lastPage = 2,
                        openedOn = OpenedOn.Today(LocalTime.of(20, 41)),
                        thumbnailPath = "/thumbs/2.png",
                        // Never drawn since the opening (no date): to draw again.
                        thumbnailOutdated = true,
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
    fun `a thumbnail file that cannot be read gives no picture`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRecentDocumentsRepository())

        assertThat(viewModel.loadThumbnail("/no/such/file.png")).isNull()
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

    private companion object {
        fun uri(id: Int) = "content://com.example.files/document/$id"
    }
}
