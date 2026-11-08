// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.myxomatom.lutrin.core.domain.dispatchers.DefaultDispatcher
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.domain.settings.SetScrollModeUseCase
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.pdf.PdfEngine
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Opens the document given in the navigation arguments ([READER_URI_ARG]) at the page of
 * [READER_PAGE_ARG], clamped to the document, and keeps it open while the reader is shown; the
 * document is closed with the ViewModel. Also follows the scroll mode setting (F03) and
 * remembers the current page, so that switching mode keeps it.
 *
 * @param observeSettings reads the scroll mode, shared by every document (ADR 0011).
 * @param setScrollMode stores the scroll mode chosen in the reader options.
 */
@HiltViewModel
class ReaderViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val engine: PdfEngine,
    @param:DefaultDispatcher private val renderDispatcher: CoroutineDispatcher,
    observeSettings: ObserveSettingsUseCase,
    private val setScrollMode: SetScrollModeUseCase,
) : ViewModel() {
    private val uri: String = checkNotNull(savedStateHandle[READER_URI_ARG]) { "Missing $READER_URI_ARG argument" }
    private val initialPage: Int = savedStateHandle[READER_PAGE_ARG] ?: 0
    private val documentState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    private val currentPage = MutableStateFlow(0)
    private var document: PdfDocument? = null

    /** State of the screen; [ReaderUiState.Ready] once the document and the settings are read. */
    val uiState: StateFlow<ReaderUiState> =
        combine(documentState, observeSettings().map { it.scrollMode }, currentPage) { state, mode, page ->
            if (state is ReaderUiState.Ready) state.copy(scrollMode = mode, currentPage = page) else state
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderUiState.Loading)

    init {
        viewModelScope.launch { open() }
    }

    /**
     * Records the page the screen shows, to start from it again after a change of scroll mode.
     *
     * @param page zero-based page; clamped to the document. Ignored before the document is open.
     */
    fun onPageChange(page: Int) {
        val pageCount = (documentState.value as? ReaderUiState.Ready)?.pageSizes?.size ?: return
        currentPage.value = page.coerceIn(0, pageCount - 1)
    }

    /**
     * Stores the scroll mode chosen by the user; it applies to every document (ADR 0011).
     *
     * @param mode continuous scroll or page by page.
     */
    fun onScrollModeChange(mode: ScrollMode) {
        viewModelScope.launch { setScrollMode(mode) }
    }

    private suspend fun open() {
        engine
            .open(DocumentSource.FromUri(uri.toUri()))
            .onSuccess { opened ->
                document = opened
                val sizes = (0 until opened.pageCount).map { opened.pageSize(it) }
                // Before the Ready state: the screen starts on the page of its first Ready state.
                currentPage.value = initialPage.coerceIn(0, opened.pageCount - 1)
                documentState.value =
                    ReaderUiState.Ready(
                        title = opened.metadata.title ?: displayName(uri),
                        pageSizes = sizes,
                        renderer = PageRenderer(opened, sizes, renderDispatcher),
                    )
            }.onFailure { error -> documentState.value = ReaderUiState.Error(error.toReaderError()) }
    }

    override fun onCleared() {
        document?.close()
        document = null
    }
}

/** Last path segment of a URI, decoded; good enough as a title until recents store names (M3). */
internal fun displayName(uri: String): String = Uri.decode(uri.substringAfterLast('/').substringAfterLast("%2F"))

/** Maps an engine failure to what the screen explains. */
internal fun Throwable.toReaderError(): ReaderError = when (this) {
    is PdfError.PasswordRequired, is PdfError.IncorrectPassword -> ReaderError.PASSWORD_REQUIRED
    is PdfError.UnsupportedFormat -> ReaderError.UNSUPPORTED_FORMAT
    is PdfError.NotAccessible -> ReaderError.NOT_ACCESSIBLE
    else -> ReaderError.UNREADABLE
}
