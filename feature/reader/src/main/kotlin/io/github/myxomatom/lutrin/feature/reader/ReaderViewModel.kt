// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.myxomatom.lutrin.core.domain.dispatchers.DefaultDispatcher
import io.github.myxomatom.lutrin.core.domain.links.CheckExternalLinkUseCase
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.recents.DocumentOpening
import io.github.myxomatom.lutrin.core.domain.recents.documentIdFor
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.domain.settings.SetScrollModeUseCase
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.pdf.PdfEngine
import io.github.myxomatom.lutrin.feature.reader.links.LinkController
import io.github.myxomatom.lutrin.feature.reader.links.PageLinks
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Opens the document given in the navigation arguments ([READER_URI_ARG]) and keeps it open while
 * the reader is shown; the document is closed with the ViewModel. It opens at the page of
 * [READER_PAGE_ARG] when given, otherwise where the user stopped reading (F12), clamped to the
 * document. A successful opening is added to the recent list (F11) and the page shown is saved
 * as the user reads ([ReadingHistory]). A document that cannot be opened but is in the recent
 * list can be removed from it from the error screen (V8, [onRemoveFromRecents]). Also follows the
 * scroll mode (F03) and page preview (F05, ADR 0018) settings, and remembers the current page, so
 * that switching mode keeps it.
 *
 * An encrypted document asks for its password ([ReaderUiState.PasswordRequired], V4, F10); each
 * password tried ([onPasswordSubmit]) goes through the same opening as any document, so that a
 * protected document starts at its saved page and enters the recent list, flagged as protected
 * (ADR 0017). The password is never kept (`AGENTS.md` §1.8, §9): not in the state, not in a
 * property, not in [SavedStateHandle] nor in the navigation arguments; it lives only while the
 * engine checks it. After a rotation the question is asked again with an empty field; after the
 * death of the process the document is opened again from its URI and asks again.
 *
 * @param observeSettings reads the scroll mode and the page preview setting, shared by every
 *   document (ADR 0011, ADR 0018).
 * @param setScrollMode stores the scroll mode chosen in the reader options.
 * @param history the recent list and the reading position of this document.
 * @param checkExternalLink decides which external links may be offered to another app (ADR 0022).
 *
 * The links of the document (F09, ADR 0022) are followed by [linkController]; their effects
 * are handled by the screen, since the ViewModel never sees a `Context`.
 */
@Suppress("LongParameterList") // Dependencies injected by Hilt.
@HiltViewModel
class ReaderViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val engine: PdfEngine,
    @param:DefaultDispatcher private val renderDispatcher: CoroutineDispatcher,
    observeSettings: ObserveSettingsUseCase,
    private val setScrollMode: SetScrollModeUseCase,
    private val history: ReadingHistory,
    private val checkExternalLink: CheckExternalLinkUseCase = CheckExternalLinkUseCase(),
) : ViewModel() {
    private val uri: String = checkNotNull(savedStateHandle[READER_URI_ARG]) { "Missing $READER_URI_ARG argument" }
    private val requestedPage: Int? = savedStateHandle.get<Int>(READER_PAGE_ARG)?.takeIf { it >= 0 }
    private val documentState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    private val currentPage = MutableStateFlow(0)
    private var document: PdfDocument? = null

    /**
     * Links of the document (F09, ADR 0022): what a tap or a TalkBack action on a link does, and
     * the confirmation of external links (V7). Entries of the outline leading out of the
     * document go through [LinkController.onExternalLink] too.
     */
    val linkController: LinkController =
        LinkController(checkExternalLink) { (documentState.value as? ReaderUiState.Ready)?.pageSizes?.size }

    /**
     * Identifier of the document in the recent list (`documentIdFor`, ADR 0015), known before it
     * is opened: the key of the shared element linking its thumbnail in the library and its page
     * in the reader (ADR 0019).
     */
    val documentId: String = documentIdFor(uri)

    /** State of the screen; [ReaderUiState.Ready] once the document and the settings are read. */
    val uiState: StateFlow<ReaderUiState> =
        combine(documentState, observeSettings(), currentPage) { state, settings, page ->
            if (state is ReaderUiState.Ready) {
                state.copy(scrollMode = settings.scrollMode, pagePreview = settings.pagePreview, currentPage = page)
            } else {
                state
            }
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

    /**
     * Tries [password] on the encrypted document. Ignored unless the password is asked
     * ([ReaderUiState.PasswordRequired]), while a previous one is being checked, or when it is
     * empty. A wrong password asks again ([ReaderUiState.PasswordRequired.isIncorrect]); there is
     * no limit on the number of attempts (ADR 0017).
     *
     * The password is turned into the engine's `String` only for the call, and no reference to
     * it is kept once the engine has answered.
     *
     * @param password what the user typed; read once, never stored.
     */
    fun onPasswordSubmit(password: CharSequence) {
        val asked = documentState.value as? ReaderUiState.PasswordRequired ?: return
        if (asked.isChecking || password.isEmpty()) return
        documentState.value = asked.copy(isChecking = true)
        val attempt = password.toString()
        viewModelScope.launch { open(attempt) }
    }

    /**
     * Removes the document that could not be opened from the recent list (V8, ADR 0016). The
     * removal completes even if the reader closes right after. Ignored unless the error screen
     * offers it.
     */
    fun onRemoveFromRecents() {
        val error = documentState.value as? ReaderUiState.Error ?: return
        if (!error.inRecents) return
        history.remove(uri)
        documentState.value = error.copy(inRecents = false)
    }

    /**
     * Opens the document, with the user password of an encrypted one. A document opened with a
     * password is recorded as protected; one restricted by its owner only opens without any.
     */
    private suspend fun open(password: String? = null) {
        engine
            .open(DocumentSource.FromUri(uri.toUri()), password)
            .onSuccess { opened -> show(opened, isProtected = password != null) }
            .onFailure { error -> documentState.value = failureState(error) }
    }

    private suspend fun show(opened: PdfDocument, isProtected: Boolean) {
        document = opened
        val sizes = (0 until opened.pageCount).map { opened.pageSize(it) }
        val title = opened.metadata.title ?: displayName(uri)
        // Before the Ready state: the screen starts on the page of its first Ready state.
        currentPage.value = history.startPage(uri, requestedPage, opened.pageCount)
        documentState.value =
            ReaderUiState.Ready(
                title = title,
                pageSizes = sizes,
                renderer = PageRenderer(opened, sizes, renderDispatcher),
                links = PageLinks(opened, renderDispatcher),
            )
        val opening = DocumentOpening(uri, opened.pageCount, currentPage.value, title, isProtected)
        viewModelScope.launch { history.follow(opening, currentPage) }
    }

    private suspend fun failureState(error: Throwable): ReaderUiState = when (error) {
        is PdfError.PasswordRequired -> ReaderUiState.PasswordRequired(documentName())
        is PdfError.IncorrectPassword -> ReaderUiState.PasswordRequired(documentName(), isIncorrect = true)
        else -> ReaderUiState.Error(error.toReaderError(), inRecents = history.isRecent(uri))
    }

    /** Name of the document in the password dialog; looked up once, then kept for the next attempts. */
    private suspend fun documentName(): String = (documentState.value as? ReaderUiState.PasswordRequired)?.documentName
        ?: history.documentName(uri)
        ?: displayName(uri).removeSuffix(PDF_EXTENSION)

    override fun onCleared() {
        // The debounced save is cancelled with the ViewModel: the page shown last is written now.
        if (document != null) history.saveOnClose(uri, currentPage.value)
        document?.close()
        document = null
    }
}

/**
 * Last path segment of a URI, decoded: the title of a document without one, and the name of its
 * recent entry when its provider gives none.
 */
internal fun displayName(uri: String): String = Uri.decode(uri.substringAfterLast('/').substringAfterLast("%2F"))

/** Extension removed from a name taken from the URI, as the library does (ADR 0015). */
private const val PDF_EXTENSION = ".pdf"

/**
 * Maps an engine failure to what the error screen explains. The password errors never reach it:
 * the reader asks for the password instead ([ReaderUiState.PasswordRequired]).
 */
internal fun Throwable.toReaderError(): ReaderError = when (this) {
    is PdfError.UnsupportedFormat -> ReaderError.UNSUPPORTED_FORMAT
    is PdfError.NotAccessible -> ReaderError.NOT_ACCESSIBLE
    else -> ReaderError.UNREADABLE
}
