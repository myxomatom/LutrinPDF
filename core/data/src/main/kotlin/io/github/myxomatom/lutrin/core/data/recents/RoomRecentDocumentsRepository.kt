// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.recents

import android.database.sqlite.SQLiteException
import io.github.myxomatom.lutrin.core.domain.dispatchers.IoDispatcher
import io.github.myxomatom.lutrin.core.domain.recents.DocumentOpening
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocument
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocumentsRepository
import io.github.myxomatom.lutrin.core.domain.recents.documentIdFor
import java.time.Clock
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [RecentDocumentsRepository] stored in Room (ADR 0015). It keeps at most
 * [RecentDocument.MAX_COUNT] entries; an entry that leaves the list, evicted beyond that number
 * or removed by the user ([remove], [clear], ADR 0016), gives back its persisted read permission
 * and deletes its thumbnail, always through the same path.
 *
 * A protected document (F10, ADR 0017) is listed like any other, but never gets a thumbnail: the
 * renderer could not open it without its password, which is never stored, and its picture would
 * show what the password protects. A thumbnail drawn before the document became protected is
 * deleted at its next opening.
 *
 * A singleton: thumbnails are drawn one at a time, under a single lock, so that the reader and
 * the library never draw the same one twice at once. Removals take the same lock, so that a
 * thumbnail drawn while its entry is removed is never left behind.
 *
 * A thumbnail of an older format ([ThumbnailStore.isCurrent]), too small for the resume card of
 * the library (ADR 0018), is reported outdated: the library draws it again when it shows it, and
 * the old file is deleted then, like any replaced thumbnail.
 *
 * @param dao the `recent_documents` table.
 * @param access the provider's description of documents and the persisted permissions.
 * @param thumbnails where the thumbnail files are.
 * @param renderer draws a thumbnail.
 * @param clock the date of an opening, injected for deterministic tests.
 * @param ioDispatcher where the thumbnail files are checked.
 */
@Singleton
internal class RoomRecentDocumentsRepository @Inject constructor(
    private val dao: RecentDocumentDao,
    private val access: DocumentAccess,
    private val thumbnails: ThumbnailStore,
    private val renderer: ThumbnailRenderer,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RecentDocumentsRepository {
    private val thumbnailLock = Mutex()

    override val recentDocuments: Flow<List<RecentDocument>> =
        dao.observeAll().map { entries -> entries.map { it.toDomain() } }.flowOn(ioDispatcher)

    override suspend fun findByUri(uri: String): RecentDocument? = withContext(ioDispatcher) {
        dao.findByUri(uri)?.toDomain()
    }

    override suspend fun recordOpening(opening: DocumentOpening): RecentDocument? = withContext(ioDispatcher) {
        // First, while the grant of the intent or of the picker is still valid.
        access.keep(opening.uri)
        val details = access.details(opening.uri)
        val previous = dao.findById(documentIdFor(opening.uri))
        // A protected document keeps no picture of its first page (ADR 0017).
        val thumbnail = previous?.takeUnless { opening.isProtected }
        val entry =
            RecentDocumentEntity(
                documentId = documentIdFor(opening.uri),
                uri = opening.uri,
                displayName = displayName(details.displayName, opening.fallbackName),
                sizeBytes = details.sizeBytes,
                pageCount = opening.pageCount,
                lastOpenedAt = clock.millis(),
                lastPage = opening.page.coerceIn(0, (opening.pageCount - 1).coerceAtLeast(0)),
                // Kept until the library draws it again: it is now older than this opening.
                thumbnailFile = thumbnail?.thumbnailFile,
                thumbnailDrawnAt = thumbnail?.thumbnailDrawnAt,
                isProtected = opening.isProtected,
            )
        try {
            dao.upsert(entry)
            thumbnailLock.withLock {
                // The picture of a document that became protected goes with this opening.
                if (opening.isProtected) previous?.thumbnailFile?.let(thumbnails::delete)
                dao.findBeyond(RecentDocument.MAX_COUNT).forEach { release(it) }
            }
            entry.toDomain()
        } catch (expected: SQLiteException) {
            // Storage full or database unavailable: the document stays open, just not recorded.
            null
        }
    }

    override suspend fun saveLastPage(uri: String, page: Int) {
        try {
            dao.updateLastPage(uri, page)
        } catch (expected: SQLiteException) {
            // Same as recordOpening: losing a reading position must not close the document.
        }
    }

    override suspend fun remove(documentId: String, openedAt: Instant?): Boolean = thumbnailLock.withLock {
        withContext(ioDispatcher) {
            try {
                val entry = dao.findById(documentId)?.takeUnless { it.reopenedSince(openedAt) }
                entry?.let { release(it) } != null
            } catch (expected: SQLiteException) {
                // Database unavailable: the entry stays, with its permission and thumbnail.
                false
            }
        }
    }

    override suspend fun clear(): Int = thumbnailLock.withLock {
        withContext(ioDispatcher) {
            try {
                // findBeyond(0): every entry, most recent first.
                dao.findBeyond(0).onEach { release(it) }.size
            } catch (expected: SQLiteException) {
                // Same as remove: what could not be removed stays, with its permission.
                0
            }
        }
    }

    override suspend fun updateThumbnail(documentId: String, onlyIfOutdated: Boolean): Boolean =
        thumbnailLock.withLock {
            withContext(ioDispatcher) {
                val entry = dao.findById(documentId)
                when {
                    // Never opened without its password, which is not stored (ADR 0017).
                    entry == null || entry.isProtected -> false

                    onlyIfOutdated && !entry.toDomain().isThumbnailOutdated -> true

                    else -> drawThumbnail(entry)
                }
            }
        }

    private suspend fun drawThumbnail(entry: RecentDocumentEntity): Boolean {
        val drawnAt = clock.millis()
        val name = thumbnails.newName(entry.documentId, drawnAt)
        val rendered = renderer.render(entry.uri, thumbnails.file(name))
        val recorded = rendered && dao.updateThumbnail(entry.documentId, name, drawnAt) != 0
        // Not recorded although drawn: the entry left the list, or became protected, meanwhile.
        if (rendered && !recorded) thumbnails.delete(name)
        if (recorded) entry.thumbnailFile?.takeIf { it != name }?.let(thumbnails::delete)
        return recorded
    }

    /** Whether this entry was opened again after [openedAt]; never when [openedAt] is `null`. */
    private fun RecentDocumentEntity.reopenedSince(openedAt: Instant?): Boolean =
        openedAt != null && lastOpenedAt != openedAt.toEpochMilli()

    /**
     * Removes [entry] from the list, then gives back its permission and deletes its thumbnail:
     * the only path out of the list (eviction, removal, clearing). The row goes first, so that a
     * storage failure leaves the entry whole. Called under [thumbnailLock].
     */
    private suspend fun release(entry: RecentDocumentEntity) {
        dao.delete(entry.documentId)
        access.release(entry.uri)
        entry.thumbnailFile?.let(thumbnails::delete)
    }

    // Always called on the I/O dispatcher: it checks that the thumbnail file still exists.
    private fun RecentDocumentEntity.toDomain(): RecentDocument = RecentDocument(
        documentId = documentId,
        uri = uri,
        displayName = displayName,
        sizeBytes = sizeBytes,
        pageCount = pageCount,
        lastOpenedAt = Instant.ofEpochMilli(lastOpenedAt),
        lastPage = lastPage,
        thumbnailPath = thumbnailFile?.let(thumbnails::file)?.takeIf { it.exists() }?.absolutePath,
        // A picture of an older format counts as never drawn: outdated, so drawn again when shown.
        thumbnailDrawnAt = thumbnailDrawnAt?.takeIf { thumbnailFile?.let(thumbnails::isCurrent) == true }
            ?.let(Instant::ofEpochMilli),
        isProtected = isProtected,
    )
}

/** The `.pdf` extension, removed from the names shown in the library (E1 mock-up). */
private const val PDF_EXTENSION = ".pdf"

/**
 * Name of a recent document: the provider's file name without its `.pdf` extension, or
 * [fallback] (title or last URI segment, likewise) when the provider gives no usable name.
 */
internal fun displayName(providerName: String?, fallback: String): String =
    providerName?.withoutPdfExtension()?.takeIf { it.isNotBlank() }
        ?: fallback.withoutPdfExtension().takeIf { it.isNotBlank() }
        ?: fallback

/** This name without its `.pdf` extension, in any case, and without surrounding spaces. */
internal fun String.withoutPdfExtension(): String =
    if (lowercase(Locale.ROOT).endsWith(PDF_EXTENSION)) dropLast(PDF_EXTENSION.length).trim() else trim()
