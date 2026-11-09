// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.recents

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Queries of the `recent_documents` table. "Most recent first" is the order of every list:
 * by opening date, then by identifier so that equal dates keep a stable order.
 */
@Dao
internal interface RecentDocumentDao {
    /** Every entry, most recent first; emits again whenever the table changes. */
    @Query("SELECT * FROM recent_documents ORDER BY last_opened_at DESC, document_id")
    fun observeAll(): Flow<List<RecentDocumentEntity>>

    /** The entry with this identifier, or `null`. */
    @Query("SELECT * FROM recent_documents WHERE document_id = :documentId")
    suspend fun findById(documentId: String): RecentDocumentEntity?

    /** The entry of this URI, or `null`. */
    @Query("SELECT * FROM recent_documents WHERE uri = :uri")
    suspend fun findByUri(uri: String): RecentDocumentEntity?

    /** Inserts the entry, or replaces the one with the same identifier. */
    @Upsert
    suspend fun upsert(entry: RecentDocumentEntity)

    /**
     * The entries after the [keep] most recent ones, which have to leave the list.
     *
     * @param keep number of entries kept, ≥ 0.
     */
    @Query("SELECT * FROM recent_documents ORDER BY last_opened_at DESC, document_id LIMIT -1 OFFSET :keep")
    suspend fun findBeyond(keep: Int): List<RecentDocumentEntity>

    /** Removes the entry with this identifier, if any. */
    @Query("DELETE FROM recent_documents WHERE document_id = :documentId")
    suspend fun delete(documentId: String)

    /**
     * Changes the page of the entry of [uri].
     *
     * @return the number of entries changed, 0 when the URI is not in the list.
     */
    @Query("UPDATE recent_documents SET last_page = :page WHERE uri = :uri")
    suspend fun updateLastPage(uri: String, page: Int): Int

    /**
     * Changes the thumbnail file of an entry and the date it was drawn.
     *
     * @return the number of entries changed, 0 when the entry is gone.
     */
    @Query(
        "UPDATE recent_documents SET thumbnail_file = :thumbnailFile, thumbnail_drawn_at = :drawnAt " +
            "WHERE document_id = :documentId",
    )
    suspend fun updateThumbnail(documentId: String, thumbnailFile: String?, drawnAt: Long?): Int
}
