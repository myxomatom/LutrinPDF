// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.recents

import kotlinx.coroutines.flow.Flow

/**
 * The recent documents (F11) and their reading positions (F12), stored on the device only.
 *
 * Implementations keep at most [RecentDocument.MAX_COUNT] entries: when an opening adds one more,
 * the least recently opened entry leaves the list, and its read permission and thumbnail are
 * released with it.
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
     * Draws the thumbnail of the first page of a recent document again, off the main thread.
     *
     * @param documentId the entry.
     * @param onlyIfMissing when `true`, an existing thumbnail file is kept and nothing is drawn.
     * @return whether the entry has a thumbnail afterwards; `false` when the entry is gone or the
     *   document cannot be read any more (file deleted, permission revoked).
     */
    suspend fun updateThumbnail(documentId: String, onlyIfMissing: Boolean): Boolean
}
