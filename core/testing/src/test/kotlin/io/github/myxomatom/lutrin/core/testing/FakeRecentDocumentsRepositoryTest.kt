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
}
