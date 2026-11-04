// SPDX-License-Identifier: GPL-3.0-or-later
// Test data: the numbers are the definitions themselves (an A4 page, a grey).
@file:Suppress("MagicNumber")

package io.github.myxomatom.lutrin.core.testing

import android.graphics.Bitmap
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfMetadata
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.pdf.PdfEngine
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Grey drawn by [FakePdfDocument.renderPage], so that tests and screenshots see the rendering. */
const val FAKE_PAGE_COLOR: Int = 0xFFB0B4BA.toInt()

/** A4 portrait, in points. */
val A4: PageSize = PageSize(595f, 842f)

/**
 * In-memory [PdfDocument] for tests: pages of the given sizes, rendered as a flat grey.
 *
 * @property pageSizes size of each page.
 */
class FakePdfDocument(val pageSizes: List<PageSize> = List(3) { A4 }, title: String? = "Fake document") : PdfDocument {
    /** Every rendering request, in order. */
    val renders: MutableList<Render> = CopyOnWriteArrayList()

    /** Whether [close] was called. */
    var isClosed: Boolean = false
        private set

    /**
     * One call to [renderPage].
     *
     * @property pageIndex requested page.
     * @property width width of the target bitmap.
     * @property height height of the target bitmap.
     * @property region requested region.
     * @property scale requested scale.
     */
    data class Render(val pageIndex: Int, val width: Int, val height: Int, val region: PdfRect, val scale: Float)

    /** Number of [pageSizes]. */
    override val pageCount: Int = pageSizes.size

    /** Metadata with the given title. */
    override val metadata: PdfMetadata = PdfMetadata(pageCount = pageCount, title = title)

    /** Returns the configured size of the page. */
    override suspend fun pageSize(index: Int): PageSize = pageSizes[index]

    /** Records the request in [renders] and fills [target] with [FAKE_PAGE_COLOR]. */
    override suspend fun renderPage(index: Int, target: Bitmap, region: PdfRect, scale: Float, nightMode: Boolean) {
        renders += Render(index, target.width, target.height, region, scale)
        target.eraseColor(FAKE_PAGE_COLOR)
    }

    /** No outline. */
    override suspend fun outline(): List<OutlineItem> = emptyList()

    /** No result. */
    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = emptyFlow()

    /** No text. */
    override suspend fun textAt(index: Int, selection: PdfRect): String = ""

    /** No link. */
    override suspend fun links(index: Int): List<PdfLink> = emptyList()

    /** Marks the document as closed ([isClosed]). */
    override fun close() {
        isClosed = true
    }
}

/**
 * [PdfEngine] for tests: returns [result] for every source and records the requests.
 *
 * @property result what `open` returns; a failure should hold a `PdfError`.
 */
class FakePdfEngine(var result: Result<PdfDocument> = Result.success(FakePdfDocument())) : PdfEngine {
    /** Every opened source, in order. */
    val openedSources: MutableList<DocumentSource> = CopyOnWriteArrayList()

    /** Records [source] in [openedSources] and returns [result]. */
    override suspend fun open(source: DocumentSource, password: String?): Result<PdfDocument> {
        openedSources += source
        return result
    }
}
