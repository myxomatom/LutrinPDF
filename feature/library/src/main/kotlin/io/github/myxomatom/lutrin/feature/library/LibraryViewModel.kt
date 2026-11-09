// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.myxomatom.lutrin.core.domain.recents.ObserveRecentDocumentsUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocument
import io.github.myxomatom.lutrin.core.domain.recents.UpdateThumbnailUseCase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How long the recent list keeps being observed after the screen stops (configuration change). */
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * State holder of the library screen (E1): the recent documents (F11), and their thumbnails,
 * drawn when the screen shows a row whose thumbnail is missing or older than its last opening
 * (ADR 0015): never while the reader opens the document.
 *
 * @param observeRecentDocuments reads the recent list.
 * @param updateThumbnail draws a missing thumbnail.
 * @param thumbnailLoader decodes thumbnail files off the main thread.
 * @param clock today's date and time zone, to write the opening dates.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeRecentDocuments: ObserveRecentDocumentsUseCase,
    private val updateThumbnail: UpdateThumbnailUseCase,
    private val thumbnailLoader: ThumbnailLoader,
    private val clock: Clock,
) : ViewModel() {
    /** Last opening of each entry shown, to ask for its thumbnail once per opening. */
    private val openings = mutableMapOf<String, Instant>()

    /** Thumbnails already asked for, as (entry, opening): each is tried once. */
    private val thumbnailRequests = mutableSetOf<Pair<String, Instant?>>()

    /** Current state of the screen. */
    val uiState: StateFlow<LibraryUiState> =
        observeRecentDocuments()
            .map<List<RecentDocument>, LibraryUiState> { documents ->
                documents.forEach { openings[it.documentId] = it.lastOpenedAt }
                if (documents.isEmpty()) LibraryUiState.Empty else LibraryUiState.Content(documents.map { it.toItem() })
            }.catch { emit(LibraryUiState.Error) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState.Loading)

    /**
     * Asks for the thumbnail of an entry shown with a missing or outdated one. Asked once per
     * opening of the entry: a document that cannot be read keeps its placeholder, or its old
     * picture.
     *
     * @param documentId the entry.
     */
    fun onThumbnailOutdated(documentId: String) {
        if (!thumbnailRequests.add(documentId to openings[documentId])) return
        viewModelScope.launch { updateThumbnail(documentId, onlyIfOutdated = true) }
    }

    /**
     * Reads a thumbnail file.
     *
     * @param path absolute path of the file.
     * @return the picture, or `null` when the file cannot be read.
     */
    suspend fun loadThumbnail(path: String): ImageBitmap? = thumbnailLoader.load(path)

    private fun RecentDocument.toItem() = RecentDocumentItem(
        documentId = documentId,
        uri = uri,
        name = displayName,
        pageCount = pageCount,
        lastPage = lastPage,
        openedOn = openedOn(lastOpenedAt, clock),
        thumbnailPath = thumbnailPath,
        thumbnailOutdated = isThumbnailOutdated,
    )
}

/**
 * Where [instant] falls relative to today, in the time zone of [clock].
 *
 * @param instant date of an opening.
 * @param clock the current date and time zone.
 */
internal fun openedOn(instant: Instant, clock: Clock): OpenedOn {
    val opened = instant.atZone(clock.zone)
    val today = LocalDate.now(clock)
    val date = opened.toLocalDate()
    return when {
        date == today -> OpenedOn.Today(opened.toLocalTime())
        date == today.minusDays(1) -> OpenedOn.Yesterday
        date.year == today.year && date.isBefore(today) -> OpenedOn.ThisYear(date)
        else -> OpenedOn.OtherYear(date)
    }
}
