// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.recents

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecentDocumentsUseCasesTest {
    /** Minimal in-memory repository: the use cases only forward to it, or read one entry. */
    private class InMemoryRecents(initial: List<RecentDocument> = emptyList()) : RecentDocumentsRepository {
        override val recentDocuments: StateFlow<List<RecentDocument>> = MutableStateFlow(initial)
        val openings = mutableListOf<DocumentOpening>()
        val pages = mutableListOf<Pair<String, Int>>()
        val thumbnails = mutableListOf<Pair<String, Boolean>>()

        override suspend fun findByUri(uri: String): RecentDocument? =
            recentDocuments.value.firstOrNull { it.uri == uri }

        override suspend fun recordOpening(opening: DocumentOpening): RecentDocument? {
            openings += opening
            return entry(opening.uri, lastPage = opening.page)
        }

        override suspend fun saveLastPage(uri: String, page: Int) {
            pages += uri to page
        }

        override suspend fun updateThumbnail(documentId: String, onlyIfOutdated: Boolean): Boolean {
            thumbnails += documentId to onlyIfOutdated
            return true
        }
    }

    @Test
    fun `observing returns the stored list`() = runTest {
        val list = listOf(entry(URI, lastPage = 2))

        assertThat(ObserveRecentDocumentsUseCase(InMemoryRecents(list))().first()).isEqualTo(list)
    }

    @Test
    fun `recording forwards the opening and returns the entry`() = runTest {
        val repository = InMemoryRecents()
        val opening = DocumentOpening(URI, pageCount = 8, page = 2, fallbackName = "contrat")

        val recorded = RecordDocumentOpeningUseCase(repository)(opening)

        assertThat(repository.openings).containsExactly(opening)
        assertThat(recorded?.lastPage).isEqualTo(2)
    }

    @Test
    fun `thumbnails are drawn again unless only missing ones are asked for`() = runTest {
        val repository = InMemoryRecents()
        val update = UpdateThumbnailUseCase(repository)

        update("a")
        update("b", onlyIfOutdated = true)

        assertThat(repository.thumbnails).containsExactly("a" to false, "b" to true).inOrder()
    }

    @Test
    fun `saving a negative page stores the first page`() = runTest {
        val repository = InMemoryRecents()
        val save = SaveLastPageUseCase(repository)

        save(URI, 4)
        save(URI, -3)

        assertThat(repository.pages).containsExactly(URI to 4, URI to 0).inOrder()
    }

    @Test
    fun `an explicit page wins over the saved page`() = runTest {
        val getStartPage = GetStartPageUseCase(InMemoryRecents(listOf(entry(URI, lastPage = 5))))

        assertThat(getStartPage(URI, requestedPage = 1, pageCount = 10)).isEqualTo(1)
    }

    @Test
    fun `without an explicit page the saved page is used`() = runTest {
        val getStartPage = GetStartPageUseCase(InMemoryRecents(listOf(entry(URI, lastPage = 5))))

        assertThat(getStartPage(URI, requestedPage = null, pageCount = 10)).isEqualTo(5)
    }

    @Test
    fun `an unknown document starts on the first page`() = runTest {
        val getStartPage = GetStartPageUseCase(InMemoryRecents(listOf(entry(URI, lastPage = 5))))

        assertThat(getStartPage("content://other/doc", requestedPage = null, pageCount = 10)).isEqualTo(0)
    }

    @Test
    fun `a saved page beyond a document that got shorter opens its last page`() = runTest {
        val getStartPage = GetStartPageUseCase(InMemoryRecents(listOf(entry(URI, lastPage = 40))))

        assertThat(getStartPage(URI, requestedPage = null, pageCount = 12)).isEqualTo(11)
    }

    @Test
    fun `requested pages are clamped to the document`() = runTest {
        val getStartPage = GetStartPageUseCase(InMemoryRecents())

        assertThat(getStartPage(URI, requestedPage = 99, pageCount = 3)).isEqualTo(2)
        assertThat(getStartPage(URI, requestedPage = -1, pageCount = 3)).isEqualTo(0)
        assertThat(getStartPage(URI, requestedPage = 4, pageCount = 0)).isEqualTo(0)
    }

    @Test
    fun `the identifier is stable, opaque and specific to the URI`() {
        val id = documentIdFor(URI)

        assertThat(id).isEqualTo(documentIdFor(URI))
        assertThat(id).matches("[0-9a-f]{32}")
        assertThat(id).doesNotContain("contrat")
        assertThat(documentIdFor("$URI/2")).isNotEqualTo(id)
    }

    @Test
    fun `the identifier is the start of the SHA-256 digest`() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertThat(documentIdFor("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223")
    }

    @Test
    fun `a thumbnail is outdated when missing or drawn before the last opening`() {
        val opened = Instant.parse("2026-11-09T20:41:00Z")
        val drawn = entry(URI, lastPage = 0).copy(thumbnailPath = "/t/a.png", thumbnailDrawnAt = opened)

        assertThat(drawn.isThumbnailOutdated).isFalse()
        assertThat(drawn.copy(thumbnailDrawnAt = opened.plusSeconds(5)).isThumbnailOutdated).isFalse()
        assertThat(drawn.copy(thumbnailDrawnAt = opened.minusSeconds(5)).isThumbnailOutdated).isTrue()
        assertThat(drawn.copy(thumbnailDrawnAt = null).isThumbnailOutdated).isTrue()
        assertThat(drawn.copy(thumbnailPath = null).isThumbnailOutdated).isTrue()
    }

    @Test
    fun `the list keeps at most fifty documents`() {
        assertThat(RecentDocument.MAX_COUNT).isEqualTo(50)
    }

    private companion object {
        const val URI = "content://com.example.files/document/contrat"

        fun entry(uri: String, lastPage: Int) = RecentDocument(
            documentId = documentIdFor(uri),
            uri = uri,
            displayName = "Contrat",
            sizeBytes = 1_024L,
            pageCount = 8,
            lastOpenedAt = Instant.parse("2026-11-09T20:41:00Z"),
            lastPage = lastPage,
            thumbnailPath = null,
        )
    }
}
