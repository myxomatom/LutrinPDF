// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf

import android.graphics.Bitmap
import android.net.Uri
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfMetadata
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
import io.github.myxomatom.lutrin.core.domain.pdf.SearchPageResult
import io.github.myxomatom.lutrin.core.domain.pdf.SelectionMode
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * Opens PDF documents. The only entry point to the PDF engine (ADR 0001): no MuPDF type is
 * visible outside `core:pdf`.
 */
interface PdfEngine {
    /**
     * Opens a document.
     *
     * @param source where to read the document from.
     * @param password user password of an encrypted document; kept in memory only for this call.
     * @return the open document, to be closed by the caller; or a failure holding a [PdfError]:
     *   [PdfError.PasswordRequired], [PdfError.IncorrectPassword], [PdfError.UnsupportedFormat],
     *   [PdfError.Unreadable] or [PdfError.NotAccessible].
     */
    suspend fun open(source: DocumentSource, password: String? = null): Result<PdfDocument>
}

/** Where a document is read from. */
sealed interface DocumentSource {
    /**
     * A file the app can read directly (for example in its cache).
     *
     * @property file the PDF file.
     */
    data class FromFile(val file: File) : DocumentSource

    /**
     * A document granted through the Storage Access Framework or an incoming intent.
     *
     * @property uri `content://` (or `file://`) URI of the document.
     */
    data class FromUri(val uri: Uri) : DocumentSource

    /**
     * A document already in memory.
     *
     * @property bytes content of the PDF file.
     */
    class FromBytes(val bytes: ByteArray) : DocumentSource
}

/**
 * An open PDF document. Every function is safe to call from any thread or coroutine: calls are
 * serialised on a thread dedicated to the document. Functions taking a page index throw
 * [PdfError.PageOutOfRange] when it is outside `0 until pageCount`, and every function throws
 * [PdfError.Closed] after [close].
 */
interface PdfDocument : Closeable {
    /** Number of pages, at least 1. */
    val pageCount: Int

    /** Document properties (F16). */
    val metadata: PdfMetadata

    /**
     * Whether the document lets its readers copy its text (F08): `false` when its author
     * restricted copying through the permissions of an encrypted document (a document opened
     * with its owner password has every permission). The engine only reports the permission;
     * enforcing it is the caller's decision (ADR 0020).
     */
    val canCopyText: Boolean

    /** Size of the page at [index], rotation applied, in points. */
    suspend fun pageSize(index: Int): PageSize

    /**
     * Draws part of a page into [target] (F02). The top-left pixel of [target] shows the point
     * ([region] left, top) of the page, and one point covers [scale] pixels; whatever falls
     * outside [target] is not drawn. Cancelling the calling coroutine aborts the rendering.
     *
     * @param target mutable [Bitmap.Config.ARGB_8888] bitmap receiving the pixels.
     * @param region area of the page to draw, in page coordinates.
     * @param scale pixels per point (1 = 72 dpi).
     * @param nightMode whether to invert the lightness of the result (F13), keeping hues.
     */
    suspend fun renderPage(index: Int, target: Bitmap, region: PdfRect, scale: Float, nightMode: Boolean)

    /**
     * Table of contents (F06); empty when the document has none. Entries whose destination
     * does not resolve keep their title, with a `null` destination.
     */
    suspend fun outline(): List<OutlineItem>

    /**
     * Searches the whole document, page by page from the first one (F07). The flow is cold and
     * stops at the first cancellation. It emits one [SearchPageResult] per page searched, even
     * without any occurrence, so that the caller can follow the progress; it ends after the last
     * page or once [SearchOptions.maxHits] occurrences are found. A blank [query] emits nothing.
     *
     * @throws PdfError.Closed from the flow if the document is closed.
     */
    fun search(query: String, options: SearchOptions = SearchOptions()): Flow<SearchPageResult>

    /**
     * Selects the text of the page at [index] between two points (F08), for example where a
     * finger pressed and where it was released, or the two handles of a selection. The order
     * of the points does not matter; each one is first moved to the nearest character, so a
     * point beside the text still selects up to the edge of the closest line.
     *
     * @param start one end of the selection, in page coordinates.
     * @param end the other end; equal to [start] for a long press.
     * @param mode how the selection grows: [SelectionMode.Words] selects the whole word under a
     *   long press; [SelectionMode.Characters] gives an empty selection when both points are equal.
     * @return the selection, empty when the page has no text layer (scanned page).
     */
    suspend fun selectText(
        index: Int,
        start: PdfPoint,
        end: PdfPoint,
        mode: SelectionMode = SelectionMode.Characters,
    ): TextSelection

    /**
     * Links of the page at [index] (F09), in document order. Internal links whose destination
     * does not resolve are left out; external ones keep their URI as written, whatever its
     * scheme (ADR 0020).
     */
    suspend fun links(index: Int): List<PdfLink>
}
