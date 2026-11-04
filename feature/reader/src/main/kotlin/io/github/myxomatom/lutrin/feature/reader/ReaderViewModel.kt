// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.pdf.PdfEngine
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State holder of the reader (E2). Opens the document named by the navigation arguments
 * ([ReaderDestination.ARG_URI], [ReaderDestination.ARG_TITLE]), renders its pages on demand
 * and closes it when the screen goes away.
 *
 * @param savedStateHandle navigation arguments; the document is reopened from them after a
 *   process death.
 * @param engine opens the document.
 * @param pageCache rendered pages, shared between the visible pages and those near them.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val engine: PdfEngine,
    private val pageCache: PageBitmapCache,
) : ViewModel() {
    private val uri: String = savedStateHandle.get<String>(ReaderDestination.ARG_URI).orEmpty()
    private val title: String = savedStateHandle.get<String>(ReaderDestination.ARG_TITLE).orEmpty()
    private val renderer = PageRenderer()

    private var document: PdfDocument? = null

    private val mutableState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading(title))

    /** Current state of the screen. */
    val uiState: StateFlow<ReaderUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        if (uri.isBlank()) {
            mutableState.value = ReaderUiState.Error(title, ReaderError.NOT_ACCESSIBLE)
            return
        }
        engine.open(DocumentSource.FromUri(uri.toUri()))
            .onSuccess { opened -> showContent(opened) }
            .onFailure { error -> mutableState.value = ReaderUiState.Error(title, error.toReaderError()) }
    }

    private suspend fun showContent(opened: PdfDocument) {
        document = opened
        val sizes = try {
            List(opened.pageCount) { index -> opened.pageSize(index) }
        } catch (error: PdfError) {
            mutableState.value = ReaderUiState.Error(title, error.toReaderError())
            return
        }
        mutableState.value = ReaderUiState.Content(title, sizes)
    }

    /**
     * Renders a page at the given width, or returns it from the cache. Cancelling the caller
     * (the page left the screen) aborts the rendering.
     *
     * @param index page index, `0 until pageCount`.
     * @param widthPx wanted width in pixels; capped by [PageRenderer.renderSize].
     * @return the page, or `null` when the document is not open or the page cannot be drawn
     *   (the screen then keeps showing a blank page).
     */
    suspend fun loadPage(index: Int, widthPx: Int): ImageBitmap? {
        val opened = document
        val pageSize = (mutableState.value as? ReaderUiState.Content)?.pageSizes?.getOrNull(index)
        if (opened == null || pageSize == null) return null
        val key = PageBitmapCache.Key(index, widthPx)
        val bitmap = pageCache[key] ?: renderOrNull(opened, index, pageSize, widthPx)?.also { pageCache.put(key, it) }
        return bitmap?.asImageBitmap()
    }

    private suspend fun renderOrNull(opened: PdfDocument, index: Int, pageSize: PageSize, widthPx: Int): Bitmap? = try {
        renderer.render(opened, index, pageSize, widthPx)
    } catch (ignored: PdfError) {
        // A single damaged page stays blank; the rest of the document is still readable.
        null
    }

    override fun onCleared() {
        pageCache.clear()
        document?.close()
        document = null
    }
}

/** Maps an engine failure to what the user is told. */
internal fun Throwable.toReaderError(): ReaderError = when (this) {
    is PdfError.PasswordRequired, is PdfError.IncorrectPassword -> ReaderError.PASSWORD_PROTECTED
    is PdfError.NotAccessible -> ReaderError.NOT_ACCESSIBLE
    else -> ReaderError.UNREADABLE
}
