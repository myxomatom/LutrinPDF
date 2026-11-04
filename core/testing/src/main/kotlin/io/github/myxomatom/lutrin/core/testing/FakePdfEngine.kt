// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import android.graphics.Bitmap
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfMetadata
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.pdf.PdfEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * In-memory [PdfEngine] for tests. Every [open] returns [result]; when [gate] is set, [open]
 * suspends until it completes, which lets a test observe the loading state.
 *
 * @property result what [open] returns: a [FakePdfDocument] or a failure holding a [PdfError].
 */
class FakePdfEngine(var result: Result<PdfDocument> = Result.success(FakePdfDocument())) : PdfEngine {
    /** Sources passed to [open], in call order. */
    val openedSources: MutableList<DocumentSource> = mutableListOf()

    /** When not null, [open] waits for it before returning. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun open(source: DocumentSource, password: String?): Result<PdfDocument> {
        openedSources += source
        gate?.await()
        return result
    }
}

/**
 * In-memory [PdfDocument] for tests: pages of the given sizes, rendered as a solid colour.
 *
 * @property pageSizes size of each page, in points; at least one.
 * @property renderFailure when not null, thrown by [renderPage].
 */
class FakePdfDocument(
    private val pageSizes: List<PageSize> = List(DEFAULT_PAGE_COUNT) { A4 },
    var renderFailure: PdfError? = null,
) : PdfDocument {
    /** Whether [close] was called. */
    var isClosed: Boolean = false
        private set

    /** Number of [renderPage] calls, to check caching. */
    var renderCount: Int = 0
        private set

    override val pageCount: Int get() = pageSizes.size

    override val metadata: PdfMetadata get() = PdfMetadata(pageCount = pageCount)

    override suspend fun pageSize(index: Int): PageSize = pageSizes.getOrNull(checkOpen(index))
        ?: throw PdfError.PageOutOfRange(index, pageCount)

    override suspend fun renderPage(index: Int, target: Bitmap, region: PdfRect, scale: Float, nightMode: Boolean) {
        pageSize(index)
        renderFailure?.let { throw it }
        renderCount++
        target.eraseColor(RENDERED_COLOR)
    }

    override suspend fun outline(): List<OutlineItem> = emptyList()

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = emptyFlow()

    override suspend fun textAt(index: Int, selection: PdfRect): String = ""

    override suspend fun links(index: Int): List<PdfLink> = emptyList()

    override fun close() {
        isClosed = true
    }

    private fun checkOpen(index: Int): Int {
        if (isClosed) throw PdfError.Closed()
        return index
    }

    /** Defaults of the fake. */
    companion object {
        /** Number of pages when none are given. */
        const val DEFAULT_PAGE_COUNT: Int = 3

        /** A4 portrait, in points. */
        val A4: PageSize = PageSize(width = 595f, height = 842f)

        /** Colour every rendered page is filled with (opaque grey). */
        const val RENDERED_COLOR: Int = 0xFF808080.toInt()
    }
}
