// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.myxomatom.lutrin.core.domain.dispatchers.ApplicationScope
import io.github.myxomatom.lutrin.core.domain.recents.ObserveRecentDocumentsUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocument
import io.github.myxomatom.lutrin.core.domain.recents.RemoveRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.UpdateThumbnailUseCase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long the recent list keeps being observed after the screen stops (configuration change). */
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * State holder of the library screen (E1): the recent documents (F11), and their thumbnails,
 * drawn when the screen shows a row whose thumbnail is missing or older than its last opening
 * (ADR 0015): never while the reader opens the document.
 *
 * « Retirer des récents » hides the row at once and offers « Annuler » ([pendingRemoval]); the
 * entry, its permission and its thumbnail are removed only when the offer ends without undo
 * (ADR 0016): a released permission could not be taken back. The removal then runs in
 * [applicationScope], so that leaving the screen never stops it half-way.
 *
 * @param observeRecentDocuments reads the recent list.
 * @param updateThumbnail draws a missing thumbnail.
 * @param removeRecentDocument removes an entry once its removal is confirmed.
 * @param thumbnailLoader decodes thumbnail files off the main thread.
 * @param clock today's date and time zone, to write the opening dates.
 * @param applicationScope where confirmed removals run; it outlives the screen.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeRecentDocuments: ObserveRecentDocumentsUseCase,
    private val updateThumbnail: UpdateThumbnailUseCase,
    private val removeRecentDocument: RemoveRecentDocumentUseCase,
    private val thumbnailLoader: ThumbnailLoader,
    private val clock: Clock,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {
    /** Last opening of each entry shown, to ask for its thumbnail once per opening. */
    private val openings = mutableMapOf<String, Instant>()

    /** The entries of the last list read, by identifier. */
    private var documents = emptyMap<String, RecentDocument>()

    /** Thumbnails already asked for, as (entry, opening): each is tried once. */
    private val thumbnailRequests = mutableSetOf<Pair<String, Instant?>>()

    /**
     * Entries removed by the user, hidden from the list. An entry opened again since its removal
     * has another opening date: it shows again.
     */
    private val hidden = MutableStateFlow(emptySet<PendingRemoval>())

    private val pending = MutableStateFlow<PendingRemoval?>(null)

    /** The removal for which « Annuler » is offered, or `null`. */
    val pendingRemoval: StateFlow<PendingRemoval?> = pending.asStateFlow()

    /** Current state of the screen. */
    val uiState: StateFlow<LibraryUiState> =
        combine(observeRecentDocuments(), hidden) { list, removed -> state(list, removed) }
            .catch { emit(LibraryUiState.Error) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState.Loading)

    /**
     * Removes an entry from the list (« Retirer des récents »): hidden at once, removed for good
     * when the offer to undo ends ([onConfirmRemoval]). A removal still pending is confirmed
     * first. Does nothing for an entry that is not shown.
     *
     * @param documentId the entry.
     */
    fun onRemove(documentId: String) {
        val document = documents[documentId] ?: return
        pending.value?.let(::onConfirmRemoval)
        val removal = PendingRemoval(document.documentId, document.displayName, document.lastOpenedAt)
        hidden.update { it + removal }
        pending.value = removal
    }

    /**
     * Brings back the entry of [removal] (« Annuler »). Ignored when it is no longer pending.
     *
     * @param removal the removal to undo.
     */
    fun onUndoRemoval(removal: PendingRemoval) {
        if (pending.compareAndSet(removal, null)) hidden.update { it - removal }
    }

    /**
     * Removes the entry of [removal] for good, with its permission, thumbnail and saved page,
     * unless it was opened again meanwhile. Called when the offer to undo ends: timeout, swipe,
     * or the screen going away. Ignored when [removal] is no longer pending.
     *
     * @param removal the removal to confirm.
     */
    fun onConfirmRemoval(removal: PendingRemoval) {
        if (!pending.compareAndSet(removal, null)) return
        applicationScope.launch { removeRecentDocument(removal.documentId, removal.openedAt) }
    }

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

    override fun onCleared() {
        // The activity is going away for good: a removal not undone by now is confirmed.
        pending.value?.let(::onConfirmRemoval)
    }

    private fun state(list: List<RecentDocument>, removed: Set<PendingRemoval>): LibraryUiState {
        documents = list.associateBy { it.documentId }
        list.forEach { openings[it.documentId] = it.lastOpenedAt }
        val shown = list.filterNot { PendingRemoval(it.documentId, it.displayName, it.lastOpenedAt) in removed }
        return if (shown.isEmpty()) LibraryUiState.Empty else LibraryUiState.Content(shown.map { it.toItem() })
    }

    private fun RecentDocument.toItem() = RecentDocumentItem(
        documentId = documentId,
        uri = uri,
        name = displayName,
        pageCount = pageCount,
        lastPage = lastPage,
        openedOn = openedOn(lastOpenedAt, clock),
        thumbnailPath = thumbnailPath,
        isProtected = isProtected,
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
