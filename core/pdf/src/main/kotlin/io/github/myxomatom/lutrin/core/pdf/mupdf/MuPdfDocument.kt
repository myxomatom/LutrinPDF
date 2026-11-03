// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import android.graphics.Bitmap
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Point
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfMetadata
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [PdfDocument] backed by MuPDF. All MuPDF objects are created, used and destroyed on
 * [dispatcher], a single thread owned by this document.
 *
 * @param resources released after the document, e.g. the file descriptor of a content URI.
 */
internal class MuPdfDocument(
    private val document: Document,
    private val dispatcher: ExecutorCoroutineDispatcher,
    override val metadata: PdfMetadata,
    private val resources: Closeable? = null,
) : PdfDocument {
    override val pageCount: Int = metadata.pageCount

    private val closed = AtomicBoolean(false)

    // Accessed on the document thread only.
    private val pages = PageCache(document, pageCount)

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

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = flow {
        if (query.isBlank()) return@flow
        val flags = options.toMuPdfFlags()
        for (index in 0 until pageCount) {
            val hits =
                onDocumentThread {
                    pages[index].search(query, flags).orEmpty().map { quads ->
                        SearchHit(index, quads.map { it.toPdfRect() })
                    }
                }
            hits.forEach { emit(it) }
        }
    }

    override suspend fun textAt(index: Int, selection: PdfRect): String = onDocumentThread {
        val text = pages[index].toStructuredText()
        try {
            text.copy(Point(selection.left, selection.top), Point(selection.right, selection.bottom)).orEmpty()
        } finally {
            text.destroy()
        }
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
        // Queued after any call already dispatched; the executor then stops its thread.
        dispatcher.executor.execute {
            pages.clear()
            document.destroy()
            resources?.close()
        }
        dispatcher.close()
    }

    /** Runs [block] on the document thread, translating MuPDF failures into [PdfError]. */
    private suspend fun <T> onDocumentThread(block: () -> T): T {
        ensureOpen()
        return withContext(dispatcher) {
            ensureOpen()
            translatingMuPdfErrors(block)
        }
    }

    private fun ensureOpen() {
        if (closed.get()) throw PdfError.Closed()
    }
}
