// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.selection

import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.SelectionMode
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One of the two handles of a selection (F08). */
enum class SelectionHandle {
    /** Where the selection starts, in reading order. */
    Start,

    /** Where the selection ends, in reading order. */
    End,
}

/** One-off effect of the selection, handled once by the screen (ADR 0024). */
sealed interface SelectionEffect {
    /**
     * Puts [text] on the clipboard. The screen does it: the ViewModel never sees a `Context`.
     *
     * @property text the selected text, in reading order.
     * @property isSensitive whether the document was opened with a password (ADR 0017): from
     *   Android 13, the clipboard then hides the text in its preview.
     */
    data class Copy(val text: String, val isSensitive: Boolean) : SelectionEffect

    /** The author of the document forbids copying its text, and the user did not choose to ignore it. */
    data object CopyRefused : SelectionEffect

    /** « Copier le texte de la page » on a page without text (scanned page). */
    data object NothingToCopy : SelectionEffect
}

/**
 * The text selection of the reader (F08, ADR 0024), owned by the `ReaderViewModel`, like the
 * links and the search. A selection is on one page only (ADR 0020): a long press on a word
 * selects it ([onLongPress]); dragging a handle extends it character by character
 * ([onHandleDragStart], [onHandleDrag]); « Tout sélectionner » takes the whole page. Text is read
 * on [dispatcher], never on the main thread.
 *
 * Copying obeys the permission of the document unless the user chose to ignore it ([canCopy]); a
 * forbidden copy leaves the selection visible and says why ([SelectionEffect.CopyRefused]).
 *
 * @param scope where the text is read: the scope of the ViewModel.
 * @param dispatcher where the text of the page is read.
 * @param document the open document, or `null` before it is open.
 * @param canCopy whether the text of the document may be copied now: its permission, or the
 *   setting that ignores it, read at each copy.
 * @param isProtected whether the document was opened with a password.
 */
@Suppress("TooManyFunctions") // One short handler per gesture and action of the selection.
class SelectionController(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val document: () -> PdfDocument?,
    private val canCopy: () -> Boolean,
    private val isProtected: () -> Boolean,
) {
    private val mutableSelection = MutableStateFlow<TextSelection?>(null)
    private val effectChannel = Channel<SelectionEffect>(Channel.BUFFERED)

    /** The other end of the selection while a handle is dragged: it stays where it is. */
    private var fixedPoint: PdfPoint? = null

    /** Latest ends (fixed, moving) of a drag not handled yet: only the latest one counts. */
    private var pendingDrag: Pair<PdfPoint, PdfPoint>? = null
    private var dragJob: Job? = null

    /** The selection shown, never empty; `null` when nothing is selected. */
    val selection: StateFlow<TextSelection?> = mutableSelection.asStateFlow()

    /** Copies and refusals, each handled once by the screen. */
    val effects: Flow<SelectionEffect> = effectChannel.receiveAsFlow()

    /**
     * A long press at [point] of the page at [pageIndex]: selects the word under it, with its
     * handles. A press outside the text (margin, image, scanned page) selects nothing, and the
     * selection shown, if any, stays.
     *
     * @return whether a word was selected; otherwise the press keeps its former meaning.
     */
    suspend fun onLongPress(pageIndex: Int, point: PdfPoint): Boolean {
        val opened = document() ?: return false
        val word =
            readText {
                if (opened.isOnText(pageIndex, point)) {
                    opened.selectText(pageIndex, point, point, SelectionMode.Words)
                } else {
                    null
                }
            }
        if (word == null || word.isEmpty) return false
        stopDragging()
        mutableSelection.value = word
        return true
    }

    /**
     * The user starts dragging [handle]: the other end of the selection stays put while it moves.
     * Ignored without a selection.
     */
    fun onHandleDragStart(handle: SelectionHandle) {
        val current = mutableSelection.value ?: return
        fixedPoint = if (handle == SelectionHandle.Start) current.end else current.start
    }

    /**
     * The dragged handle is at [point] of the page: selects, character by character, from the
     * other end to it. The handles may cross; a position that selects nothing keeps the last
     * selection. Positions arriving while the text is read are merged: only the latest is used.
     *
     * @param point where the handle points, in page coordinates.
     */
    fun onHandleDrag(point: PdfPoint) {
        val fixed = fixedPoint ?: return
        pendingDrag = fixed to point
        if (dragJob?.isActive == true) return
        dragJob = scope.launch {
            while (true) {
                val (from, to) = pendingDrag ?: break
                pendingDrag = null
                extendTo(from, to)
            }
        }
    }

    /** The handle is released: the selection stays as the last position made it. */
    fun onHandleDragEnd() {
        fixedPoint = null
    }

    /** « Tout sélectionner »: the whole text of the page of the selection. */
    fun onSelectAll() {
        val page = mutableSelection.value?.pageIndex ?: return
        stopDragging()
        scope.launch {
            val all = readText { pageText(page) }
            if (all != null && !all.isEmpty) mutableSelection.value = all
        }
    }

    /**
     * « Copier »: copies the selection and closes it, or says that the author forbids it, the
     * selection staying shown. Ignored without a selection.
     */
    fun onCopy() {
        val current = mutableSelection.value ?: return
        if (!canCopy()) {
            effectChannel.trySend(SelectionEffect.CopyRefused)
            return
        }
        effectChannel.trySend(SelectionEffect.Copy(current.text, isProtected()))
        onClear()
    }

    /**
     * « Copier le texte de la page », the TalkBack action of the document area: copies the whole
     * text of the page at [pageIndex] without showing a selection, under the same permission as
     * « Copier ».
     */
    fun onCopyPage(pageIndex: Int) {
        if (document() == null) return
        if (!canCopy()) {
            effectChannel.trySend(SelectionEffect.CopyRefused)
            return
        }
        scope.launch {
            val all = readText { pageText(pageIndex) }
            val effect =
                if (all == null || all.isEmpty) {
                    SelectionEffect.NothingToCopy
                } else {
                    SelectionEffect.Copy(all.text, isProtected())
                }
            effectChannel.trySend(effect)
        }
    }

    /** Closes the selection: a tap elsewhere, back, another page, or the search opening. */
    fun onClear() {
        stopDragging()
        mutableSelection.value = null
    }

    private suspend fun extendTo(fixed: PdfPoint, moving: PdfPoint) {
        val page = mutableSelection.value?.pageIndex ?: return
        val opened = document() ?: return
        val extended = readText { opened.selectText(page, fixed, moving, SelectionMode.Characters) }
        // Ignored if the selection was closed or replaced while the text was read.
        if (extended != null && !extended.isEmpty && mutableSelection.value?.pageIndex == page) {
            mutableSelection.value = extended
        }
    }

    /**
     * Every line of the page: from its top-left corner to its bottom-right corner, whole lines.
     * In a page laid out in columns, this is the text of the first line to the last one in
     * reading order.
     */
    private suspend fun pageText(page: Int): TextSelection? {
        val opened = document() ?: return null
        val size = opened.pageSize(page)
        return opened.selectText(page, PdfPoint(0f, 0f), PdfPoint(size.width, size.height), SelectionMode.Lines)
    }

    /** Runs [read] on the dispatcher; `null` when the document is closed or the page unreadable. */
    private suspend fun <T> readText(read: suspend () -> T?): T? = try {
        withContext(dispatcher) { read() }
    } catch (expected: PdfError) {
        // Nothing to select: the reader shows its own error if the document became unreadable.
        null
    }

    private fun stopDragging() {
        dragJob?.cancel()
        dragJob = null
        pendingDrag = null
        fixedPoint = null
    }
}
