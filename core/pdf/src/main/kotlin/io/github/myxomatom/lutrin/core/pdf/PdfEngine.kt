// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf

import android.graphics.Bitmap
import android.net.Uri
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfMetadata
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
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

    /** Table of contents (F06); empty when the document has none. */
    suspend fun outline(): List<OutlineItem>

    /**
     * Searches the whole document, page by page (F07). The flow is cold and stops at the first
     * cancellation; a blank [query] yields nothing.
     */
    fun search(query: String, options: SearchOptions = SearchOptions()): Flow<SearchHit>

    /** Text inside [selection] on the page at [index], in reading order (F08). */
    suspend fun textAt(index: Int, selection: PdfRect): String

    /** Links of the page at [index] (F09). */
    suspend fun links(index: Int): List<PdfLink>
}
