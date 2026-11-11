// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import android.graphics.Bitmap
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Document
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
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [PdfDocument] backed by MuPDF. All MuPDF objects are created, used and destroyed on
 * [dispatcher], a single thread owned by this document.
 *
 * @param releaseFailures receives the failures of [close], which never throws them.
 * @param canCopyText copy permission read when the document was opened.
 * @param resources released after the document, e.g. the file descriptor of a content URI.
 */
internal class MuPdfDocument(
    private val document: Document,
    private val dispatcher: ExecutorCoroutineDispatcher,
    override val metadata: PdfMetadata,
    private val releaseFailures: ReleaseFailureReporter,
    override val canCopyText: Boolean = true,
    private val resources: Closeable? = null,
) : PdfDocument {
    override val pageCount: Int = metadata.pageCount

    private val closed = AtomicBoolean(false)

    // Accessed on the document thread only.
    private val pages = PageCache(document, pageCount)

    // Text of the last page selected, accessed on the document thread only: dragging a
    // selection handle selects again and again on the same page.
    private var selectedPageText: Pair<Int, PageText>? = null

    override suspend fun pageSize(index: Int): PageSize = onDocumentThread {
        val bounds = pages[index].bounds.toPdfRect()
        PageSize(bounds.width, bounds.height)
    }

    override suspend fun renderPage(index: Int, target: Bitmap, region: PdfRect, scale: Float, nightMode: Boolean) {
        require(target.isMutable && target.config == Bitmap.Config.ARGB_8888) {
            "Target must be a mutable ARGB_8888 bitmap"
        }
        val pixels = renderPixels(index, RenderSpec(region, scale, target.width, target.height, nightMode))
        target.setPixels(pixels, 0, target.width, 0, 0, target.width, target.height)
    }

    /**
     * Renders like [renderPage] into an array of ARGB pixels, row by row: the platform-free
     * part of the rendering, testable on the JVM. Cancelling the caller aborts the drawing.
     *
     * The [Cookie] is the one MuPDF object deliberately created and destroyed on the caller's
     * thread rather than on the document thread (ADR 0007): it is a plain native counter block,
     * tied to no document, that MuPDF designed to be shared between threads — the document
     * thread reads it while drawing and [Cookie.abort] is called from the cancellation handler.
     * Owning it here guarantees it outlives the drawing and is destroyed exactly once.
     *
     * @throws PdfError.PageOutOfRange if [index] is not a page of the document.
     * @throws PdfError.Closed if the document is closed.
     */
    internal suspend fun renderPixels(index: Int, spec: RenderSpec): IntArray {
        val cookie = Cookie()
        return try {
            coroutineScope {
                val watcher = launch(start = CoroutineStart.UNDISPATCHED) { awaitCancellation() }
                watcher.invokeOnCompletion { cookie.abort() }
                try {
                    onDocumentThread { pages[index].drawPixels(spec, cookie) }
                } finally {
                    watcher.cancel()
                }
            }
        } finally {
            cookie.destroy()
        }
    }

    override suspend fun outline(): List<OutlineItem> = onDocumentThread {
        document.toOutlineItems(document.loadOutline())
    }

    override fun search(query: String, options: SearchOptions): Flow<SearchPageResult> = flow {
        if (query.isBlank()) return@flow
        val flags = options.toMuPdfFlags()
        var found = 0
        for (index in 0 until pageCount) {
            val hits = onDocumentThread { pages[index].searchHits(index, query, flags) }
            val remaining = options.maxHits - found
            val isLimitReached = hits.size >= remaining
            emit(SearchPageResult(index, hits.take(remaining), isLimitReached))
            if (isLimitReached) break
            found += hits.size
        }
    }

    override suspend fun selectText(index: Int, start: PdfPoint, end: PdfPoint, mode: SelectionMode): TextSelection =
        onDocumentThread {
            val text = selectedPageText?.takeIf { (page, _) -> page == index }?.second
                ?: pages[index].withStructuredText { it.toPageText() }.also { selectedPageText = index to it }
            text.select(index, start, end, mode)
        }

    override suspend fun links(index: Int): List<PdfLink> = onDocumentThread {
        val links = pages[index].links.orEmpty()
        try {
            links.mapNotNull { document.toPdfLink(it) }
        } finally {
            links.forEach { it.destroy() }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            // Queued after any call already dispatched; the executor then stops its thread.
            // Each release step runs even if an earlier one fails.
            dispatcher.executor.execute {
                releaseFailures.reporting { pages.clear() }
                releaseFailures.reporting { document.destroy() }
                releaseFailures.reporting { resources?.close() }
            }
        } finally {
            dispatcher.close()
        }
    }

    /**
     * Runs [block] on the document thread, translating MuPDF failures into [PdfError].
     *
     * @throws PdfError.Closed if the document is or gets closed before [block] runs.
     */
    private suspend fun <T> onDocumentThread(block: () -> T): T {
        ensureOpen()
        return try {
            withContext(dispatcher) {
                ensureOpen()
                translatingMuPdfErrors(block)
            }
        } catch (error: CancellationException) {
            // If close() stops the thread between ensureOpen() and the dispatch, the executor
            // rejects the call and kotlinx.coroutines cancels it: report the real cause, unless
            // the caller itself was cancelled.
            if (closed.get() && currentCoroutineContext().isActive) {
                throw PdfError.Closed().apply { addSuppressed(error) }
            }
            throw error
        }
    }

    private fun ensureOpen() {
        if (closed.get()) throw PdfError.Closed()
    }
}
