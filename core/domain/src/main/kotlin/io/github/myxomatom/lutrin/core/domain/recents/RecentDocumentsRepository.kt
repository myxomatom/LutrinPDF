// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.recents

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The recent documents (F11) and their reading positions (F12), stored on the device only.
 *
 * Implementations keep at most [RecentDocument.MAX_COUNT] entries: when an opening adds one more,
 * the least recently opened entry leaves the list, and its read permission and thumbnail are
 * released with it. An entry removed by the user ([remove], [clear]) is released the same way.
 */
interface RecentDocumentsRepository {
    /** The recent documents, most recently opened first; emits again on every change. */
    val recentDocuments: Flow<List<RecentDocument>>

    /**
     * The entry of the document opened from [uri].
     *
     * @return the entry, or `null` when the document is not in the list.
     */
    suspend fun findByUri(uri: String): RecentDocument?

    /**
     * Adds the document to the top of the list, or moves it there with its new page count, page
     * and opening date. Keeps a persisted read permission on the URI when the system allows it
     * (documents of the system file picker); a document received from another app without such
     * a permission is recorded all the same, and may become inaccessible later.
     *
     * @param opening the document just opened.
     * @return the stored entry, or `null` when it could not be stored (storage full).
     */
    suspend fun recordOpening(opening: DocumentOpening): RecentDocument?

    /**
     * Remembers the page the user is on. Does nothing for a document that is not in the list.
     *
     * @param uri the document URI.
     * @param page zero-based page.
     */
    suspend fun saveLastPage(uri: String, page: Int)

    /**
     * Draws the thumbnail of the first page of a recent document, off the main thread. The
     * document is opened for this alone, through the PDF engine.
     *
     * @param documentId the entry.
     * @param onlyIfOutdated when `true`, a thumbnail that is not [RecentDocument.isThumbnailOutdated]
     *   is kept and nothing is drawn.
     * @return whether the entry has a thumbnail afterwards; `false` when the entry is gone or the
     *   document cannot be read any more (file deleted, permission revoked).
     */
    suspend fun updateThumbnail(documentId: String, onlyIfOutdated: Boolean): Boolean

    /**
     * Removes an entry from the list (« Retirer des récents »): its row, with the page saved in
     * it, its persisted read permission and its thumbnail file. The document itself is never
     * touched. A permission that was never persisted and a thumbnail file already gone are not
     * errors.
     *
     * @param documentId the entry.
     * @param openedAt when given, the entry is removed only if it was last opened at this date:
     *   a document opened again after the removal was asked (undo delay of the library) stays.
     * @return whether an entry was removed; `false` when it is not in the list, was opened again
     *   since [openedAt], or could not be removed (storage unavailable).
     */
    suspend fun remove(documentId: String, openedAt: Instant? = null): Boolean

    /**
     * Removes every entry (« Vider les récents »), each one as [remove] does.
     *
     * @return the number of entries removed; 0 when the list was already empty.
     */
    suspend fun clear(): Int
}
