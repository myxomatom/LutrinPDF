// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import io.github.myxomatom.lutrin.core.domain.dispatchers.ApplicationScope
import io.github.myxomatom.lutrin.core.domain.recents.DocumentOpening
import io.github.myxomatom.lutrin.core.domain.recents.GetStartPageUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecordDocumentOpeningUseCase
import io.github.myxomatom.lutrin.core.domain.recents.SaveLastPageUseCase
import io.github.myxomatom.lutrin.core.domain.recents.UpdateThumbnailUseCase
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * What the reader tells the recent list about one document (F11, F12, ADR 0015): where to start,
 * the opening itself, and the page the user is on. One instance per reader: it remembers the
 * last page it saved, so that it writes only when the page really changed.
 *
 * The page is saved [SAVE_DELAY] after the user stops changing page (never once per frame or
 * per page flicked through), and once more when the reader closes, in [applicationScope], which
 * outlives the reader.
 *
 * @param getStartPage chooses the first page: explicit, saved, or the first one.
 * @param recordOpening adds the document to the recent list.
 * @param updateThumbnail draws its thumbnail after each opening.
 * @param saveLastPage stores the page.
 * @param applicationScope where the last write runs once the reader is gone.
 */
class ReadingHistory @Inject constructor(
    private val getStartPage: GetStartPageUseCase,
    private val recordOpening: RecordDocumentOpeningUseCase,
    private val updateThumbnail: UpdateThumbnailUseCase,
    private val saveLastPage: SaveLastPageUseCase,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    /** Last page stored for the followed document; `null` until it is recorded. */
    private var savedPage: Int? = null

    /**
     * The page to open a document at: [requestedPage] when given, otherwise the saved page,
     * otherwise the first page; always within the document.
     *
     * @param uri the document.
     * @param requestedPage zero-based page asked by the navigation, or `null`.
     * @param pageCount number of pages of the open document.
     */
    suspend fun startPage(uri: String, requestedPage: Int?, pageCount: Int): Int =
        getStartPage(uri, requestedPage, pageCount)

    /**
     * Records [opening] in the recent list, draws the thumbnail, then saves the pages of
     * [currentPage] until the calling coroutine is cancelled. Records nothing more if the
     * opening could not be stored.
     *
     * @param opening the document just opened, with its start page.
     * @param currentPage the zero-based page shown, starting with the start page.
     */
    @OptIn(FlowPreview::class)
    suspend fun follow(opening: DocumentOpening, currentPage: Flow<Int>) {
        val recorded = recordOpening(opening) ?: return
        savedPage = recorded.lastPage
        coroutineScope {
            launch { updateThumbnail(recorded.documentId) }
            currentPage.distinctUntilChanged().debounce(SAVE_DELAY).collect { save(opening.uri, it) }
        }
    }

    /**
     * Saves [page] when the reader closes, if it was not saved yet. Does not suspend: the write
     * runs in the application scope.
     *
     * @param uri the document.
     * @param page zero-based page shown when the reader closes.
     */
    fun saveOnClose(uri: String, page: Int) {
        if (savedPage == null || savedPage == page) return
        savedPage = page
        applicationScope.launch { saveLastPage(uri, page) }
    }

    private suspend fun save(uri: String, page: Int) {
        if (page == savedPage) return
        savedPage = page
        saveLastPage(uri, page)
    }

    /** Timing of the writes. */
    companion object {
        /** Quiet time after the last page change before the page is written. */
        val SAVE_DELAY = 1.seconds
    }
}
