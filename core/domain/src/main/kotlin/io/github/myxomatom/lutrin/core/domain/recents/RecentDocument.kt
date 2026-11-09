// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.recents

import java.time.Instant

/**
 * A document of the recent list (F11, `SPEC.md` §5), with where the user stopped reading it (F12).
 *
 * @property documentId stable, opaque identifier of the entry ([documentIdFor]); what the
 *   assistants use to designate the document (`SPEC.md` §2.4).
 * @property uri the `content://` URI the document was opened from.
 * @property displayName name shown to the user: the file name given by its provider, without
 *   the `.pdf` extension, or a fallback when the provider gives none.
 * @property sizeBytes size of the file in bytes, or `null` when the provider does not say.
 * @property pageCount number of pages when the document was last opened, at least 1.
 * @property lastOpenedAt when the document was last opened successfully.
 * @property lastPage zero-based page the user was on, within `0 until pageCount`.
 * @property thumbnailPath absolute path of the picture of the first page, or `null` when it was
 *   not drawn yet or its file is gone.
 * @property thumbnailDrawnAt when that picture was drawn, or `null` when there is none.
 */
data class RecentDocument(
    val documentId: String,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long?,
    val pageCount: Int,
    val lastOpenedAt: Instant,
    val lastPage: Int,
    val thumbnailPath: String?,
    val thumbnailDrawnAt: Instant? = null,
) {
    /**
     * Whether the thumbnail has to be drawn (again): it is missing, or older than the last
     * opening, after which the file may have changed (ADR 0015).
     */
    val isThumbnailOutdated: Boolean
        get() = thumbnailPath == null || thumbnailDrawnAt?.isBefore(lastOpenedAt) != false

    /** Limits of the recent list. */
    companion object {
        /**
         * Most documents kept in the list (ADR 0015). Each one may hold a persisted read
         * permission, and Android keeps at most 128 of them per app (512 from Android 11): the
         * least recently opened entries, and their permissions, are released beyond this number.
         */
        const val MAX_COUNT: Int = 50
    }
}

/**
 * A document that the reader has just opened successfully, to add to the recent list.
 *
 * @property uri the URI the document was opened from.
 * @property pageCount its number of pages, at least 1.
 * @property page zero-based page the reader starts on.
 * @property fallbackName name to show when the provider gives no file name (document title or
 *   last segment of the URI).
 */
data class DocumentOpening(val uri: String, val pageCount: Int, val page: Int, val fallbackName: String)
