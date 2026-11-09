// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import io.github.myxomatom.lutrin.core.domain.recents.DocumentOpening
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocument
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocumentsRepository
import io.github.myxomatom.lutrin.core.domain.recents.documentIdFor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Date of the fake clock used by default: 9 November 2026, 20:41 UTC. */
val FAKE_NOW: Instant = Instant.parse("2026-11-09T20:41:00Z")

/**
 * In-memory [RecentDocumentsRepository] for tests: keeps the order and the limit of the real
 * one, and records the calls that have no visible state.
 *
 * @param initial entries before any change, most recent first.
 * @property clock date given to recorded openings.
 */
class FakeRecentDocumentsRepository(
    initial: List<RecentDocument> = emptyList(),
    var clock: Clock = Clock.fixed(FAKE_NOW, ZoneOffset.UTC),
) : RecentDocumentsRepository {
    private val state = MutableStateFlow(initial)

    /** Every call to [updateThumbnail], in order. */
    val thumbnailRequests: MutableList<ThumbnailRequest> = CopyOnWriteArrayList()

    /** Every call to [saveLastPage], in order, as `(uri, page)`. */
    val savedPages: MutableList<Pair<String, Int>> = CopyOnWriteArrayList()

    /** What [updateThumbnail] answers. */
    var thumbnailResult: Boolean = true

    /** When `true`, [recordOpening] stores nothing and returns `null`, like a full storage. */
    var failRecording: Boolean = false

    /**
     * One call to [updateThumbnail].
     *
     * @property documentId the entry.
     * @property onlyIfOutdated the flag given.
     */
    data class ThumbnailRequest(val documentId: String, val onlyIfOutdated: Boolean)

    /** Current entries; a [StateFlow], so tests can read [StateFlow.value] directly. */
    override val recentDocuments: StateFlow<List<RecentDocument>> = state

    /** Looks the entry up in memory. */
    override suspend fun findByUri(uri: String): RecentDocument? = state.value.firstOrNull { it.uri == uri }

    /** Moves or adds the entry to the top, keeping at most [RecentDocument.MAX_COUNT] entries. */
    override suspend fun recordOpening(opening: DocumentOpening): RecentDocument? {
        if (failRecording) return null
        val previous = findByUri(opening.uri)
        val entry =
            RecentDocument(
                documentId = documentIdFor(opening.uri),
                uri = opening.uri,
                displayName = previous?.displayName ?: opening.fallbackName,
                sizeBytes = previous?.sizeBytes,
                pageCount = opening.pageCount,
                lastOpenedAt = clock.instant(),
                lastPage = opening.page,
                thumbnailPath = previous?.thumbnailPath,
                thumbnailDrawnAt = previous?.thumbnailDrawnAt,
            )
        state.value = (listOf(entry) + state.value.filterNot { it.uri == opening.uri }).take(RecentDocument.MAX_COUNT)
        return entry
    }

    /** Records the call in [savedPages] and updates the entry, if any. */
    override suspend fun saveLastPage(uri: String, page: Int) {
        savedPages += uri to page
        state.value = state.value.map { if (it.uri == uri) it.copy(lastPage = page) else it }
    }

    /** Records the call in [thumbnailRequests] and returns [thumbnailResult]. */
    override suspend fun updateThumbnail(documentId: String, onlyIfOutdated: Boolean): Boolean {
        thumbnailRequests += ThumbnailRequest(documentId, onlyIfOutdated)
        return thumbnailResult
    }
}
