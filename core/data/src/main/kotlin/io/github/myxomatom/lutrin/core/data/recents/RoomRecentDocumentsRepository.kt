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
 * [RecentDocument.MAX_COUNT] entries; an entry that leaves the list gives back its persisted
 * read permission and deletes its thumbnail.
 *
 * A singleton: thumbnails are drawn one at a time, under a single lock, so that the reader and
 * the library never draw the same one twice at once.
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
                thumbnailFile = previous?.thumbnailFile,
                thumbnailDrawnAt = previous?.thumbnailDrawnAt,
            )
        try {
            dao.upsert(entry)
            dao.findBeyond(RecentDocument.MAX_COUNT).forEach { remove(it) }
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

    override suspend fun updateThumbnail(documentId: String, onlyIfOutdated: Boolean): Boolean =
        thumbnailLock.withLock {
            withContext(ioDispatcher) {
                val entry = dao.findById(documentId)
                when {
                    entry == null -> false
                    onlyIfOutdated && !entry.toDomain().isThumbnailOutdated -> true
                    else -> drawThumbnail(entry)
                }
            }
        }

    private suspend fun drawThumbnail(entry: RecentDocumentEntity): Boolean {
        val drawnAt = clock.millis()
        val name = thumbnails.newName(entry.documentId, drawnAt)
        if (!renderer.render(entry.uri, thumbnails.file(name))) return false
        if (dao.updateThumbnail(entry.documentId, name, drawnAt) == 0) {
            // The entry left the list while its thumbnail was drawn.
            thumbnails.delete(name)
            return false
        }
        entry.thumbnailFile?.takeIf { it != name }?.let(thumbnails::delete)
        return true
    }

    private suspend fun remove(entry: RecentDocumentEntity) {
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
        thumbnailDrawnAt = thumbnailDrawnAt?.let(Instant::ofEpochMilli),
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

private fun String.withoutPdfExtension(): String =
    if (lowercase(Locale.ROOT).endsWith(PDF_EXTENSION)) dropLast(PDF_EXTENSION.length).trim() else trim()
