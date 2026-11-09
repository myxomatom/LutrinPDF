// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.recents.DocumentOpening
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocument
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakeRecentDocumentsRepositoryTest {
    private fun opening(id: Int, page: Int = 0) = DocumentOpening("content://files/$id", 8, page, "doc$id")

    @Test
    fun `records openings most recent first, without duplicates, within the limit`() = runTest {
        val repository = FakeRecentDocumentsRepository()

        (0..RecentDocument.MAX_COUNT).forEach { repository.recordOpening(opening(it)) }
        repository.recordOpening(opening(3, page = 4))

        val list = repository.recentDocuments.value
        assertThat(list).hasSize(RecentDocument.MAX_COUNT)
        assertThat(list.first().uri).isEqualTo("content://files/3")
        assertThat(list.first().lastPage).isEqualTo(4)
        assertThat(list.first().lastOpenedAt).isEqualTo(FAKE_NOW)
        assertThat(list.map { it.uri }).doesNotContain("content://files/0")
    }

    @Test
    fun `a protected opening is flagged and drops the thumbnail`() = runTest {
        val drawn = checkNotNull(FakeRecentDocumentsRepository().recordOpening(opening(1)))
        val repository = FakeRecentDocumentsRepository(listOf(drawn.copy(thumbnailPath = "/t/1.png")))

        val entry = repository.recordOpening(opening(1).copy(isProtected = true))

        assertThat(entry?.isProtected).isTrue()
        assertThat(entry?.thumbnailPath).isNull()
        assertThat(entry?.isThumbnailOutdated).isFalse()
    }

    @Test
    fun `saves pages and records thumbnail requests`() = runTest {
        val repository = FakeRecentDocumentsRepository()
        val entry = repository.recordOpening(opening(1))

        repository.saveLastPage("content://files/1", 6)
        repository.thumbnailResult = false

        assertThat(repository.findByUri("content://files/1")?.lastPage).isEqualTo(6)
        assertThat(repository.savedPages).containsExactly("content://files/1" to 6)
        assertThat(repository.updateThumbnail(entry?.documentId.orEmpty(), onlyIfOutdated = true)).isFalse()
        assertThat(repository.thumbnailRequests.single().onlyIfOutdated).isTrue()
    }

    @Test
    fun `a failing storage records nothing`() = runTest {
        val repository = FakeRecentDocumentsRepository().apply { failRecording = true }

        assertThat(repository.recordOpening(opening(1))).isNull()
        assertThat(repository.recentDocuments.value).isEmpty()
    }

    @Test
    fun `removes an entry unless it was opened again, and records the calls`() = runTest {
        val repository = FakeRecentDocumentsRepository()
        val first = repository.recordOpening(opening(1))
        repository.recordOpening(opening(2))
        val id = first?.documentId.orEmpty()

        assertThat(repository.remove(id, FAKE_NOW.minusSeconds(1))).isFalse()
        assertThat(repository.remove(id, FAKE_NOW)).isTrue()
        assertThat(repository.remove(id)).isFalse()

        assertThat(repository.recentDocuments.value.map { it.uri }).containsExactly("content://files/2")
        assertThat(
            repository.removals.map {
                it.second
            },
        ).containsExactly(FAKE_NOW.minusSeconds(1), FAKE_NOW, null).inOrder()
    }

    @Test
    fun `saving the page of a removed document does not bring it back`() = runTest {
        val repository = FakeRecentDocumentsRepository()
        val entry = repository.recordOpening(opening(1))
        repository.remove(entry?.documentId.orEmpty())

        repository.saveLastPage("content://files/1", 3)

        assertThat(repository.recentDocuments.value).isEmpty()
    }

    @Test
    fun `clearing empties the list and counts the calls`() = runTest {
        val repository = FakeRecentDocumentsRepository()
        repository.recordOpening(opening(1))
        repository.recordOpening(opening(2))

        assertThat(repository.clear()).isEqualTo(2)
        assertThat(repository.clear()).isEqualTo(0)
        assertThat(repository.clearCount).isEqualTo(2)
        assertThat(repository.recentDocuments.value).isEmpty()
    }
}
