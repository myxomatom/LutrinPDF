// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.search

import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
import io.github.myxomatom.lutrin.core.domain.pdf.SearchPageResult
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Wait after the last keystroke before searching (V1, ADR 0023). */
val SEARCH_DEBOUNCE: Duration = 400.milliseconds

/**
 * The search in the reader (F07, V1, ADR 0023), owned by the `ReaderViewModel`.
 *
 * A search starts 400 ms after the last keystroke ([SEARCH_DEBOUNCE]), at once when the keyboard
 * validates it or an option changes, and cancels the previous one. It starts from the page shown
 * and wraps around (ADR 0020); results arrive page by page in [state], the first one found is
 * shown at once ([SearchEffect.Reveal]). Pages are searched, and results merged, on
 * [dispatcher], never on the main thread. Closing the search, or the scope (the document closes
 * with the ViewModel), cancels it.
 *
 * @param scope where searches run: the scope of the ViewModel.
 * @param dispatcher where pages are searched and results merged.
 * @param document the open document, or `null` before it is open.
 * @param currentPage zero-based page shown, where a search starts.
 */
@Suppress("TooManyFunctions") // One short handler per control of the search bar.
class SearchController(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val document: () -> PdfDocument?,
    private val currentPage: () -> Int,
) {
    private val mutableState = MutableStateFlow(SearchState())
    private val effectChannel = Channel<SearchEffect>(Channel.BUFFERED)
    private var searchJob: Job? = null

    /** Whether a search waits for the end of the typing ([SEARCH_DEBOUNCE]). */
    private var isWaiting = false

    /** What the search bar, the highlights and the excerpt card show. */
    val state: StateFlow<SearchState> = mutableState.asStateFlow()

    /** Occurrences to bring into view, each handled once by the screen. */
    val effects: Flow<SearchEffect> = effectChannel.receiveAsFlow()

    /** Opens the search bar (« Rechercher » in the top bar), with an empty field. */
    fun onOpen() {
        mutableState.update { it.copy(isOpen = true) }
    }

    /**
     * Closes the search bar (its arrow, or back): cancels the search and clears the field and the
     * highlights. The options are kept for the session.
     */
    fun onClose() {
        cancel()
        mutableState.update { SearchState(matchCase = it.matchCase, matchAccents = it.matchAccents) }
    }

    /**
     * The text of the field changed: searches it once the typing stops ([SEARCH_DEBOUNCE]). A
     * blank text clears the results at once.
     *
     * @param query the whole text of the field.
     */
    fun onQueryChange(query: String) {
        if (query == mutableState.value.query) return
        mutableState.update { it.copy(query = query) }
        restart(debounce = true)
    }

    /**
     * The keyboard validated the field: searches at once instead of waiting for the end of the
     * typing, or again after a failure. Ignored while the search of the field runs or once it ended.
     */
    fun onSubmit() {
        if (isWaiting || mutableState.value.status in Restartable) restart(debounce = false)
    }

    /** « Respecter la casse » toggled: searches again with it. */
    fun onMatchCaseChange(matchCase: Boolean) {
        if (matchCase == mutableState.value.matchCase) return
        mutableState.update { it.copy(matchCase = matchCase) }
        restart(debounce = false)
    }

    /** « Respecter les accents » toggled: searches again with it. */
    fun onMatchAccentsChange(matchAccents: Boolean) {
        if (matchAccents == mutableState.value.matchAccents) return
        mutableState.update { it.copy(matchAccents = matchAccents) }
        restart(debounce = false)
    }

    /** « Résultat suivant »: the next occurrence in document order, the first one after the last. */
    fun onNext() = move(step = 1)

    /** « Résultat précédent »: the previous occurrence, the last one before the first. */
    fun onPrevious() = move(step = -1)

    /** Cancels the search running, if any; the results found so far stay. */
    fun cancel() {
        searchJob?.cancel()
        searchJob = null
        isWaiting = false
    }

    private fun move(step: Int) {
        val state = mutableState.value
        val results = state.results
        if (results.isEmpty()) return
        val from = state.currentIndex.takeIf { it >= 0 } ?: 0
        val index = Math.floorMod(from + step, results.size)
        mutableState.update { it.copy(current = results.refAt(index), hasMoved = true) }
        effectChannel.trySend(SearchEffect.Reveal(results.hits[index], byUser = true))
    }

    /**
     * Cancels the search running and starts the one of the current query and options, after
     * [SEARCH_DEBOUNCE] when [debounce]. A blank query clears the results instead.
     */
    private fun restart(debounce: Boolean) {
        cancel()
        val state = mutableState.value
        val opened = document()
        if (state.query.isBlank() || opened == null) {
            mutableState.update {
                it.copy(results = SearchResults.Empty, current = null, hasMoved = false, status = SearchStatus.Idle)
            }
            return
        }
        val options = SearchOptions(matchCase = state.matchCase, matchAccents = state.matchAccents)
        isWaiting = debounce
        searchJob = scope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE)
            isWaiting = false
            search(opened, state.query, options)
        }
    }

    private suspend fun search(opened: PdfDocument, query: String, options: SearchOptions) {
        mutableState.update {
            it.copy(
                results = SearchResults.Empty,
                current = null,
                hasMoved = false,
                status = SearchStatus.Searching,
                pageCount = opened.pageCount,
            )
        }
        val startPage = currentPage().coerceIn(0, opened.pageCount - 1)
        try {
            opened.search(query, options, startPage)
                .accumulate()
                .flowOn(dispatcher)
                .collect { (results, page) -> onPageSearched(results, page) }
            mutableState.update { it.copy(status = SearchStatus.Done) }
        } catch (expected: PdfError) {
            // Shown to the user (« La recherche a échoué »); the results found so far stay.
            mutableState.update { it.copy(status = SearchStatus.Failed) }
        }
    }

    /** Shows the results with one more page; the first occurrence found becomes the current one. */
    private fun onPageSearched(results: SearchResults, page: SearchPageResult) {
        val first = mutableState.value.current == null && page.hits.isNotEmpty()
        mutableState.update { state ->
            state.copy(results = results, current = state.current ?: HitRef(page.pageIndex, 0).takeIf { first })
        }
        if (first) effectChannel.trySend(SearchEffect.Reveal(page.hits.first(), byUser = false))
    }
}

/**
 * The results so far after each page, with that page: merged where the pages are searched, so
 * that the main thread only receives them.
 */
private fun Flow<SearchPageResult>.accumulate(): Flow<Pair<SearchResults, SearchPageResult>> = flow {
    var results = SearchResults.Empty
    collect { page ->
        results += page
        emit(results to page)
    }
}

/** States after which validating the field searches again: nothing searched yet, or a failure. */
private val Restartable = setOf(SearchStatus.Idle, SearchStatus.Failed)
