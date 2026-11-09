// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.recents

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.data.db.LutrinDatabase
import io.github.myxomatom.lutrin.core.domain.recents.DocumentOpening
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocument
import io.github.myxomatom.lutrin.core.domain.recents.documentIdFor
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRecentDocumentsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** Records the permissions taken and given back; [persistable] decides what `keep` answers. */
    private class FakeAccess : DocumentAccess {
        val kept = CopyOnWriteArrayList<String>()
        val released = CopyOnWriteArrayList<String>()
        var persistable = true
        var details = DocumentDetails(displayName = "Contrat de location.pdf", sizeBytes = 2_048)

        override suspend fun details(uri: String): DocumentDetails = details

        override suspend fun keep(uri: String): Boolean = persistable.also { if (it) kept += uri }

        override suspend fun release(uri: String) {
            released += uri
        }
    }

    /** Writes a few bytes, or fails for the URIs in [unreadable]. */
    private class FakeRenderer : ThumbnailRenderer {
        val unreadable = mutableSetOf<String>()
        val rendered = CopyOnWriteArrayList<String>()

        override suspend fun render(uri: String, destination: File): Boolean {
            if (uri in unreadable) return false
            destination.parentFile?.mkdirs()
            destination.writeBytes(byteArrayOf(1, 2, 3))
            rendered += uri
            return true
        }
    }

    /** A clock that tests move forward by hand. */
    private class MutableClock(var now: Instant = Instant.parse("2026-11-09T20:41:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now

        fun advance(seconds: Long = 60) {
            now = now.plus(Duration.ofSeconds(seconds))
        }
    }

    private val access = FakeAccess()
    private val renderer = FakeRenderer()
    private val clock = MutableClock()
    private var database: LutrinDatabase? = null
    private val thumbnailDirectory get() = File(folder.root, "thumbnails")

    @After
    fun tearDown() {
        database?.close()
    }

    private fun TestScope.repository(): RoomRecentDocumentsRepository {
        val db = inMemoryDatabase().also { database = it }
        return RoomRecentDocumentsRepository(
            dao = db.recentDocumentDao(),
            access = access,
            thumbnails = ThumbnailStore(thumbnailDirectory),
            renderer = renderer,
            clock = clock,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    private fun opening(id: Int, page: Int = 0, pageCount: Int = 8) =
        DocumentOpening(uri(id), pageCount = pageCount, page = page, fallbackName = "document$id.pdf")

    @Test
    fun `starts with an empty list`() = runTest {
        assertThat(repository().recentDocuments.first()).isEmpty()
    }

    @Test
    fun `records an opening with the provider's name and size`() = runTest {
        val repository = repository()

        val recorded = repository.recordOpening(opening(1, page = 2))

        val expected =
            RecentDocument(
                documentId = documentIdFor(uri(1)),
                uri = uri(1),
                displayName = "Contrat de location",
                sizeBytes = 2_048,
                pageCount = 8,
                lastOpenedAt = clock.now,
                lastPage = 2,
                thumbnailPath = null,
            )
        assertThat(recorded).isEqualTo(expected)
        assertThat(repository.recentDocuments.first()).containsExactly(expected)
    }

    @Test
    fun `falls back to the reader's name when the provider gives none`() = runTest {
        val repository = repository()
        access.details = DocumentDetails()

        val recorded = repository.recordOpening(opening(1))

        assertThat(recorded?.displayName).isEqualTo("document1")
        assertThat(recorded?.sizeBytes).isNull()
    }

    @Test
    fun `keeps the read permission of a recorded document`() = runTest {
        val repository = repository()

        repository.recordOpening(opening(1))

        assertThat(access.kept).containsExactly(uri(1))
    }

    @Test
    fun `records a document whose permission cannot be persisted`() = runTest {
        val repository = repository()
        access.persistable = false

        repository.recordOpening(opening(1))

        assertThat(repository.recentDocuments.first().map { it.uri }).containsExactly(uri(1))
        assertThat(access.kept).isEmpty()
    }

    @Test
    fun `lists the most recently opened first`() = runTest {
        val repository = repository()
        repository.recordOpening(opening(1))
        clock.advance()
        repository.recordOpening(opening(2))
        clock.advance()
        repository.recordOpening(opening(3))

        assertThat(repository.recentDocuments.first().map { it.uri }).containsExactly(uri(3), uri(2), uri(1)).inOrder()
    }

    @Test
    fun `opening the same URI again moves it to the top without a duplicate`() = runTest {
        val repository = repository()
        repository.recordOpening(opening(1))
        clock.advance()
        repository.recordOpening(opening(2))
        clock.advance()

        repository.recordOpening(opening(1, page = 5, pageCount = 9))

        val list = repository.recentDocuments.first()
        assertThat(list.map { it.uri }).containsExactly(uri(1), uri(2)).inOrder()
        assertThat(list.first().lastPage).isEqualTo(5)
        assertThat(list.first().pageCount).isEqualTo(9)
        assertThat(list.first().lastOpenedAt).isEqualTo(clock.now)
    }

    @Test
    fun `keeps at most fifty documents and releases the oldest`() = runTest {
        val repository = repository()
        repeat(RecentDocument.MAX_COUNT) {
            repository.recordOpening(opening(it))
            clock.advance()
        }
        repository.updateThumbnail(documentIdFor(uri(0)), onlyIfMissing = false)
        val oldestThumbnail = repository.findByUri(uri(0))?.thumbnailPath
        assertThat(oldestThumbnail).isNotNull()

        repository.recordOpening(opening(RecentDocument.MAX_COUNT))

        val list = repository.recentDocuments.first()
        assertThat(list).hasSize(RecentDocument.MAX_COUNT)
        assertThat(list.first().uri).isEqualTo(uri(RecentDocument.MAX_COUNT))
        assertThat(list.map { it.uri }).doesNotContain(uri(0))
        assertThat(access.released).containsExactly(uri(0))
        assertThat(oldestThumbnail?.let(::File)?.exists()).isFalse()
    }

    @Test
    fun `saves the last page of a recorded document`() = runTest {
        val repository = repository()
        repository.recordOpening(opening(1))

        repository.saveLastPage(uri(1), 6)

        assertThat(repository.findByUri(uri(1))?.lastPage).isEqualTo(6)
    }

    @Test
    fun `saving the page of an unknown document changes nothing`() = runTest {
        val repository = repository()

        repository.saveLastPage(uri(1), 6)

        assertThat(repository.recentDocuments.first()).isEmpty()
    }

    @Test
    fun `a recorded page is clamped to the document`() = runTest {
        val repository = repository()

        assertThat(repository.recordOpening(opening(1, page = 12, pageCount = 4))?.lastPage).isEqualTo(3)
        assertThat(repository.recordOpening(opening(2, page = -2, pageCount = 4))?.lastPage).isEqualTo(0)
    }

    @Test
    fun `draws a thumbnail and shows its path`() = runTest {
        val repository = repository()
        repository.recordOpening(opening(1))

        val drawn = repository.updateThumbnail(documentIdFor(uri(1)), onlyIfMissing = false)

        val path = repository.findByUri(uri(1))?.thumbnailPath
        assertThat(drawn).isTrue()
        assertThat(path).isNotNull()
        assertThat(File(path.orEmpty()).parentFile).isEqualTo(thumbnailDirectory)
        assertThat(File(path.orEmpty()).exists()).isTrue()
    }

    @Test
    fun `drawing again replaces the previous thumbnail file`() = runTest {
        val repository = repository()
        val id = documentIdFor(uri(1))
        repository.recordOpening(opening(1))
        repository.updateThumbnail(id, onlyIfMissing = false)
        val first = repository.findByUri(uri(1))?.thumbnailPath.orEmpty()
        clock.advance()

        repository.updateThumbnail(id, onlyIfMissing = false)

        val second = repository.findByUri(uri(1))?.thumbnailPath.orEmpty()
        assertThat(second).isNotEqualTo(first)
        assertThat(File(first).exists()).isFalse()
        assertThat(File(second).exists()).isTrue()
    }

    @Test
    fun `an existing thumbnail is kept when only missing ones are drawn`() = runTest {
        val repository = repository()
        val id = documentIdFor(uri(1))
        repository.recordOpening(opening(1))
        repository.updateThumbnail(id, onlyIfMissing = false)
        renderer.rendered.clear()

        assertThat(repository.updateThumbnail(id, onlyIfMissing = true)).isTrue()
        assertThat(renderer.rendered).isEmpty()
    }

    @Test
    fun `a missing thumbnail file is drawn again`() = runTest {
        val repository = repository()
        val id = documentIdFor(uri(1))
        repository.recordOpening(opening(1))
        repository.updateThumbnail(id, onlyIfMissing = false)
        File(repository.findByUri(uri(1))?.thumbnailPath.orEmpty()).delete()
        renderer.rendered.clear()

        repository.recentDocuments.test {
            assertThat(awaitItem().single().thumbnailPath).isNull()
            clock.advance()
            assertThat(repository.updateThumbnail(id, onlyIfMissing = true)).isTrue()
            assertThat(awaitItem().single().thumbnailPath).isNotNull()
        }
        assertThat(renderer.rendered).containsExactly(uri(1))
    }

    @Test
    fun `a document that cannot be read gets no thumbnail`() = runTest {
        val repository = repository()
        repository.recordOpening(opening(1))
        renderer.unreadable += uri(1)

        assertThat(repository.updateThumbnail(documentIdFor(uri(1)), onlyIfMissing = true)).isFalse()
        assertThat(repository.findByUri(uri(1))?.thumbnailPath).isNull()
    }

    @Test
    fun `no thumbnail is drawn for an entry that is not in the list`() = runTest {
        val repository = repository()

        assertThat(repository.updateThumbnail("unknown", onlyIfMissing = false)).isFalse()
        assertThat(renderer.rendered).isEmpty()
    }

    @Test
    fun `display names lose their PDF extension only`() {
        assertThat(displayName("Contrat.PDF", "x")).isEqualTo("Contrat")
        assertThat(displayName("notes.txt", "x")).isEqualTo("notes.txt")
        assertThat(displayName(" ", "fallback.pdf")).isEqualTo("fallback")
        assertThat(displayName(".pdf", "fallback")).isEqualTo("fallback")
        assertThat(displayName(null, ".pdf")).isEqualTo(".pdf")
    }

    private companion object {
        fun uri(id: Int) = "content://com.example.files/document/$id"
    }
}
