// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.recents

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.data.db.LutrinDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/** The `recent_documents` table, in an in-memory Room database under Robolectric. */
@RunWith(AndroidJUnit4::class)
class RecentDocumentDaoTest {
    private var database: LutrinDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    private fun TestScope.dao(): RecentDocumentDao = inMemoryDatabase().also { database = it }.recentDocumentDao()

    @Test
    fun `an empty table lists nothing`() = runTest {
        val dao = dao()

        assertThat(dao.findBeyond(0)).isEmpty()
        assertThat(dao.findById("missing")).isNull()
        assertThat(dao.findByUri("content://missing")).isNull()
    }

    @Test
    fun `lists the most recently opened first`() = runTest {
        val dao = dao()
        dao.upsert(entity("a", openedAt = 1_000))
        dao.upsert(entity("b", openedAt = 3_000))
        dao.upsert(entity("c", openedAt = 2_000))

        dao.observeAll().test {
            assertThat(awaitItem().map { it.documentId }).containsExactly("b", "c", "a").inOrder()
        }
    }

    @Test
    fun `equal dates keep a stable order`() = runTest {
        val dao = dao()
        dao.upsert(entity("b", openedAt = 1_000))
        dao.upsert(entity("a", openedAt = 1_000))

        assertThat(dao.findBeyond(0).map { it.documentId }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `upsert replaces the entry with the same identifier`() = runTest {
        val dao = dao()
        dao.upsert(entity("a", openedAt = 1_000, lastPage = 0))

        dao.upsert(entity("a", openedAt = 5_000, lastPage = 7))

        assertThat(dao.findBeyond(0)).containsExactly(entity("a", openedAt = 5_000, lastPage = 7))
    }

    @Test
    fun `finds an entry by identifier and by URI`() = runTest {
        val dao = dao()
        dao.upsert(entity("a"))

        assertThat(dao.findById("a")).isEqualTo(entity("a"))
        assertThat(dao.findByUri(uri("a"))).isEqualTo(entity("a"))
    }

    @Test
    fun `lists the entries beyond the most recent ones`() = runTest {
        val dao = dao()
        (1..5).forEach { dao.upsert(entity("d$it", openedAt = it * 1_000L)) }

        assertThat(dao.findBeyond(3).map { it.documentId }).containsExactly("d2", "d1").inOrder()
        assertThat(dao.findBeyond(5)).isEmpty()
        // What clearing the list goes through: every entry, most recent first.
        assertThat(dao.findBeyond(0).map { it.documentId }).containsExactly("d5", "d4", "d3", "d2", "d1").inOrder()
    }

    @Test
    fun `deleting an unknown entry changes nothing`() = runTest {
        val dao = dao()
        dao.upsert(entity("a"))

        dao.delete("unknown")

        assertThat(dao.findBeyond(0).map { it.documentId }).containsExactly("a")
    }

    @Test
    fun `deletes an entry`() = runTest {
        val dao = dao()
        dao.upsert(entity("a"))
        dao.upsert(entity("b"))

        dao.delete("a")

        assertThat(dao.findBeyond(0).map { it.documentId }).containsExactly("b")
    }

    @Test
    fun `updates the page of a known URI only`() = runTest {
        val dao = dao()
        dao.upsert(entity("a"))

        assertThat(dao.updateLastPage(uri("a"), 4)).isEqualTo(1)
        assertThat(dao.updateLastPage("content://unknown", 4)).isEqualTo(0)
        assertThat(dao.findById("a")?.lastPage).isEqualTo(4)
    }

    @Test
    fun `updates the thumbnail of a known entry only`() = runTest {
        val dao = dao()
        dao.upsert(entity("a"))

        assertThat(dao.updateThumbnail("a", "a-1.png", drawnAt = 1)).isEqualTo(1)
        assertThat(dao.updateThumbnail("gone", "gone-1.png", drawnAt = 1)).isEqualTo(0)
        assertThat(dao.findById("a")?.thumbnailFile).isEqualTo("a-1.png")
        assertThat(dao.findById("a")?.thumbnailDrawnAt).isEqualTo(1)
    }

    @Test
    fun `emits again when the table changes`() = runTest {
        val dao = dao()

        dao.observeAll().test {
            assertThat(awaitItem()).isEmpty()
            dao.upsert(entity("a"))
            assertThat(awaitItem().map { it.documentId }).containsExactly("a")
            dao.updateLastPage(uri("a"), 2)
            assertThat(awaitItem().single().lastPage).isEqualTo(2)
        }
    }

    @Test
    fun `keeps unknown sizes and thumbnails as null`() = runTest {
        val dao = dao()
        dao.upsert(entity("a").copy(sizeBytes = null, thumbnailFile = null, thumbnailDrawnAt = null))

        val stored = dao.findById("a")

        assertThat(stored?.sizeBytes).isNull()
        assertThat(stored?.thumbnailFile).isNull()
        assertThat(stored?.thumbnailDrawnAt).isNull()
    }

    @Test
    fun `stores whether an entry is protected`() = runTest {
        val dao = dao()
        dao.upsert(entity("a").copy(isProtected = true))
        dao.upsert(entity("b"))

        assertThat(dao.findById("a")?.isProtected).isTrue()
        assertThat(dao.findById("b")?.isProtected).isFalse()
    }

    @Test
    fun `a protected entry gets no thumbnail`() = runTest {
        val dao = dao()
        dao.upsert(entity("a").copy(isProtected = true))

        val changed = dao.updateThumbnail("a", "a-1.png", drawnAt = 1)

        assertThat(changed).isEqualTo(0)
        assertThat(dao.findById("a")?.thumbnailFile).isNull()
    }
}

/** An in-memory database whose queries run on the test scheduler. */
internal fun TestScope.inMemoryDatabase(): LutrinDatabase = Room
    .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LutrinDatabase::class.java)
    .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
    .build()

private fun uri(id: String) = "content://com.example.files/document/$id"

private fun entity(id: String, openedAt: Long = 1_000, lastPage: Int = 0) = RecentDocumentEntity(
    documentId = id,
    uri = uri(id),
    displayName = "Document $id",
    sizeBytes = 2_048,
    pageCount = 8,
    lastOpenedAt = openedAt,
    lastPage = lastPage,
    thumbnailFile = null,
    thumbnailDrawnAt = null,
)
