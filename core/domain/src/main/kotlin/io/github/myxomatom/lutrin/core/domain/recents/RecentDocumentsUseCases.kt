// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.recents

import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observes the recent documents (F11).
 *
 * @param repository where the recent documents are stored.
 */
class ObserveRecentDocumentsUseCase @Inject constructor(private val repository: RecentDocumentsRepository) {
    /** @return the recent documents, most recently opened first, then every change. */
    operator fun invoke(): Flow<List<RecentDocument>> = repository.recentDocuments
}

/**
 * Adds a document that was just opened to the recent list, or moves it to the top (F11).
 *
 * @param repository where the recent documents are stored.
 */
class RecordDocumentOpeningUseCase @Inject constructor(private val repository: RecentDocumentsRepository) {
    /**
     * @param opening the document just opened, with the page the reader starts on.
     * @return the stored entry, or `null` when it could not be stored.
     */
    suspend operator fun invoke(opening: DocumentOpening): RecentDocument? = repository.recordOpening(opening)
}

/**
 * Draws the thumbnail of a recent document (F11). The library asks for it when it shows an
 * entry whose thumbnail is missing or older than its last opening (ADR 0015), so that the
 * reader never competes with it while it opens a document.
 *
 * @param repository where the recent documents are stored.
 */
class UpdateThumbnailUseCase @Inject constructor(private val repository: RecentDocumentsRepository) {
    /**
     * @param documentId the entry.
     * @param onlyIfOutdated keep a thumbnail that is up to date instead of drawing it again.
     * @return whether the entry has a thumbnail afterwards.
     */
    suspend operator fun invoke(documentId: String, onlyIfOutdated: Boolean = false): Boolean =
        repository.updateThumbnail(documentId, onlyIfOutdated)
}

/**
 * Remembers the page the user is reading (F12).
 *
 * @param repository where the reading positions are stored.
 */
class SaveLastPageUseCase @Inject constructor(private val repository: RecentDocumentsRepository) {
    /**
     * @param uri the document URI.
     * @param page zero-based page; a negative page is stored as the first page.
     */
    suspend operator fun invoke(uri: String, page: Int) {
        repository.saveLastPage(uri, page.coerceAtLeast(0))
    }
}

/**
 * Chooses the page a document opens at (F12, ADR 0015): the page asked explicitly (the `page`
 * extra of « Ouvrir avec », ADR 0014), otherwise the page where the user stopped reading,
 * otherwise the first page. The result is clamped to the document, which may have fewer pages
 * than when it was last read.
 *
 * @param repository where the reading positions are stored.
 */
class GetStartPageUseCase @Inject constructor(private val repository: RecentDocumentsRepository) {
    /**
     * @param uri the document URI.
     * @param requestedPage zero-based page asked explicitly, or `null`.
     * @param pageCount number of pages of the document now, at least 1.
     * @return a zero-based page within `0 until pageCount`.
     */
    suspend operator fun invoke(uri: String, requestedPage: Int?, pageCount: Int): Int {
        val page = requestedPage ?: repository.findByUri(uri)?.lastPage ?: 0
        return page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }
}

/**
 * Tells whether a document is in the recent list, so that the error screen (V8) offers to remove
 * it (`SPEC.md` §6).
 *
 * @param repository where the recent documents are stored.
 */
class IsRecentDocumentUseCase @Inject constructor(private val repository: RecentDocumentsRepository) {
    /**
     * @param uri the document URI.
     * @return whether the list has an entry for [uri].
     */
    suspend operator fun invoke(uri: String): Boolean = repository.findByUri(uri) != null
}

/**
 * Removes a document from the recent list (F11, « Retirer des récents »): its entry, its saved
 * page, its persisted read permission and its thumbnail. The file itself is never touched.
 *
 * @param repository where the recent documents are stored.
 */
class RemoveRecentDocumentUseCase @Inject constructor(private val repository: RecentDocumentsRepository) {
    /**
     * @param documentId the entry, as [documentIdFor] gives it.
     * @param openedAt when given, the entry is removed only if it was not opened again since
     *   this date (removal confirmed after an undo delay).
     * @return whether an entry was removed.
     */
    suspend operator fun invoke(documentId: String, openedAt: Instant? = null): Boolean =
        repository.remove(documentId, openedAt)
}

/**
 * Empties the recent list (F11, « Vider les récents » in the settings): every entry is removed
 * as [RemoveRecentDocumentUseCase] does.
 *
 * @param repository where the recent documents are stored.
 */
class ClearRecentDocumentsUseCase @Inject constructor(private val repository: RecentDocumentsRepository) {
    /** @return the number of entries removed. */
    suspend operator fun invoke(): Int = repository.clear()
}
