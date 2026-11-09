// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.recents

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Row of the `recent_documents` table: one recent document (F11) and its reading position (F12),
 * schema version 1 (ADR 0015).
 *
 * @property documentId identifier derived from [uri] (`documentIdFor`), primary key.
 * @property uri the `content://` URI the document was opened from; unique.
 * @property displayName name shown in the library.
 * @property sizeBytes file size given by the provider, or `null`.
 * @property pageCount number of pages at the last opening.
 * @property lastOpenedAt date of the last successful opening, in milliseconds since the epoch.
 * @property lastPage zero-based page the user was on.
 * @property thumbnailFile name of the thumbnail file in the thumbnail directory, or `null` when
 *   none was drawn yet.
 * @property thumbnailDrawnAt when the thumbnail was drawn, in milliseconds since the epoch, or
 *   `null`; a thumbnail older than [lastOpenedAt] is drawn again.
 */
@Entity(
    tableName = "recent_documents",
    indices = [Index(value = ["uri"], unique = true), Index(value = ["last_opened_at"])],
)
internal data class RecentDocumentEntity(
    @PrimaryKey
    @ColumnInfo(name = "document_id")
    val documentId: String,
    @ColumnInfo(name = "uri")
    val uri: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long?,
    @ColumnInfo(name = "page_count")
    val pageCount: Int,
    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAt: Long,
    @ColumnInfo(name = "last_page")
    val lastPage: Int,
    @ColumnInfo(name = "thumbnail_file")
    val thumbnailFile: String?,
    @ColumnInfo(name = "thumbnail_drawn_at")
    val thumbnailDrawnAt: Long?,
)
