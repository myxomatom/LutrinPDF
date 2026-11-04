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
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.pdf.PdfEngine
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Opens the document given in the navigation arguments ([READER_URI_ARG]) and keeps it open
 * while the reader is shown; the document is closed with the ViewModel.
 */
@HiltViewModel
class ReaderViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val engine: PdfEngine,
    @param:DefaultDispatcher private val renderDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val uri: String = checkNotNull(savedStateHandle[READER_URI_ARG]) { "Missing $READER_URI_ARG argument" }
    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    private var document: PdfDocument? = null

    /** State of the screen. */
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        engine
            .open(DocumentSource.FromUri(uri.toUri()))
            .onSuccess { opened ->
                document = opened
                val sizes = (0 until opened.pageCount).map { opened.pageSize(it) }
                _uiState.value =
                    ReaderUiState.Ready(
                        title = opened.metadata.title ?: displayName(uri),
                        pageSizes = sizes,
                        renderer = PageRenderer(opened, sizes, renderDispatcher),
                    )
            }.onFailure { error -> _uiState.value = ReaderUiState.Error(error.toReaderError()) }
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
