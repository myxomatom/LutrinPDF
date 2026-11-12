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
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.domain.settings.SetScrollModeUseCase
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.pdf.PdfEngine
import io.github.myxomatom.lutrin.feature.reader.links.LinkController
import io.github.myxomatom.lutrin.feature.reader.links.PageLinks
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import io.github.myxomatom.lutrin.feature.reader.search.SearchController
import io.github.myxomatom.lutrin.feature.reader.selection.SelectionController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * The outline of the document (F06, ADR 0021) is read once the document is shown, off the main
 * thread, and flattened into [ReaderUiState.Ready.outline] with the section of the current page;
 * [onOutlineOpened] and [onOutlineToggle] drive which sections are expanded.
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
 * are handled by the screen, since the ViewModel never sees a `Context`. The search (F07, ADR
 * 0023) is run by [searchController], the text selection and its copy (F08, ADR 0024) by
 * [selectionController].
 */
@Suppress("LongParameterList", "TooManyFunctions") // Hilt dependencies; one short handler per user action.
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
    private val outline = MutableStateFlow(ReaderOutline())
    private var document: PdfDocument? = null

    /** Whether the document was opened with its password: its copied text is flagged sensitive. */
    private var isProtected = false

    /** The settings, followed as long as the ViewModel lives (scroll mode, preview, copy). */
    private val settings: StateFlow<AppSettings> =
        observeSettings().stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /**
     * Links of the document (F09, ADR 0022): what a tap or a TalkBack action on a link does, and
     * the confirmation of external links (V7). Entries of the outline leading out of the
     * document go through [LinkController.onExternalLink] too.
     */
    val linkController: LinkController =
        LinkController(checkExternalLink) { (documentState.value as? ReaderUiState.Ready)?.pageSizes?.size }

    /**
     * Search in the document (F07, V1, ADR 0023): the search bar, its results page by page and
     * the occurrence shown. It searches from the page shown, on the render dispatcher, and is
     * cancelled with the ViewModel, before the document closes.
     */
    val searchController: SearchController =
        SearchController(viewModelScope, renderDispatcher, document = { document }, currentPage = { currentPage.value })

    /**
     * Text selection and copy (F08, ADR 0024): a long press selects a word, handles extend it, and
     * « Copier » obeys the permission of the document, or the setting that ignores it, read at
     * each copy.
     */
    val selectionController: SelectionController =
        SelectionController(
            scope = viewModelScope,
            dispatcher = renderDispatcher,
            document = { document },
            canCopy = { canCopyText() },
            isProtected = { isProtected },
        )

    /**
     * Identifier of the document in the recent list (`documentIdFor`, ADR 0015), known before it
     * is opened: the key of the shared element linking its thumbnail in the library and its page
     * in the reader (ADR 0019).
     */
    val documentId: String = documentIdFor(uri)

    /** State of the screen; [ReaderUiState.Ready] once the document and the settings are read. */
    val uiState: StateFlow<ReaderUiState> =
        combine(documentState, settings, currentPage, outline) { state, settings, page, outline ->
            if (state is ReaderUiState.Ready) {
                state.copy(
                    scrollMode = settings.scrollMode,
                    pagePreview = settings.pagePreview,
                    currentPage = page,
                    outline = outline.withCurrentSection(page),
                    canCopyText = settings.allowsTextCopy(document?.canCopyText ?: true),
                )
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
     * The table of contents is about to be shown: expands the path down to the section of the
     * current page, so that it is visible and highlighted (F06). Sections the user expanded stay
     * expanded. Ignored while the document has no outline.
     */
    fun onOutlineOpened() {
        val page = currentPage.value
        outline.update { it.withCurrentSection(page).revealingCurrentSection() }
    }

    /**
     * Expands the sub-sections of an outline entry that is collapsed, or collapses one that is
     * expanded. Ignored for an unknown entry and for an entry without sub-sections.
     *
     * @param id [OutlineEntry.id] of the entry.
     */
    fun onOutlineToggle(id: Int) {
        outline.update { it.toggling(id) }
    }

    /**
     * The user chose an outline entry that leads outside the document ([OutlineTarget.External]):
     * it goes the way of an external link (ADR 0022), checked, then confirmed in V7, or refused
     * with a message. The reader never opens an address by itself.
     *
     * @param uri address of the entry, as the engine reads it, whatever its scheme.
     */
    fun onExternalOutlineEntry(uri: String) = linkController.onExternalLink(uri)

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

    /** Whether selected text may be copied now (ADR 0024); `false` before the document is open. */
    private fun canCopyText(): Boolean = document?.let { settings.value.allowsTextCopy(it.canCopyText) } ?: false

    private suspend fun show(opened: PdfDocument, isProtected: Boolean) {
        document = opened
        this.isProtected = isProtected
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
        viewModelScope.launch { loadOutline(opened) }
    }

    /**
     * Reads the outline and flattens it, off the main thread. A document whose outline cannot be
     * read is read without one: the table of contents is a help, not a reason to refuse it.
     */
    private suspend fun loadOutline(opened: PdfDocument) {
        val entries =
            withContext(renderDispatcher) {
                try {
                    flattenOutline(opened.outline(), opened.pageCount)
                } catch (ignored: PdfError) {
                    emptyList()
                }
            }
        outline.update { it.copy(entries = entries) }
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
        // Normally cancelled with the scope already: no page may be searched once the document closes.
        searchController.cancel()
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
