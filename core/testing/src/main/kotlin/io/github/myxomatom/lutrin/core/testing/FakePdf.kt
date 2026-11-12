// SPDX-License-Identifier: GPL-3.0-or-later
// Test data: the numbers are the definitions themselves (an A4 page, a grey).
@file:Suppress("MagicNumber")

package io.github.myxomatom.lutrin.core.testing

import android.graphics.Bitmap
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfMetadata
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
import io.github.myxomatom.lutrin.core.domain.pdf.SearchPageResult
import io.github.myxomatom.lutrin.core.domain.pdf.SelectionMode
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.pdf.PdfEngine
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Grey drawn by [FakePdfDocument.renderPage], so that tests and screenshots see the rendering. */
const val FAKE_PAGE_COLOR: Int = 0xFFB0B4BA.toInt()

/** A4 portrait, in points. */
val A4: PageSize = PageSize(595f, 842f)

/**
 * In-memory [PdfDocument] for tests: pages of the given sizes, rendered as a flat grey. Its
 * outline, links, search results and text are empty until a test sets them, for example:
 *
 * ```
 * val document = FakePdfDocument().apply {
 *     outline = listOf(OutlineItem("Chapitre 1", PdfDestination(pageIndex = 2)))
 *     searchHits = { query -> if (query == "lutrin") listOf(SearchHit(1, listOf(area))) else emptyList() }
 *     searchPageDelay = 10.milliseconds // To see the progress and cancel the search.
 * }
 * ```
 *
 * Like the real engine, the functions taking a page index throw [PdfError.PageOutOfRange]
 * outside the document, and every function throws [PdfError.Closed] once [close] is called.
 *
 * @property pageSizes size of each page.
 */
class FakePdfDocument(val pageSizes: List<PageSize> = List(3) { A4 }, title: String? = "Fake document") : PdfDocument {
    /** Every rendering request, in order. */
    val renders: MutableList<Render> = CopyOnWriteArrayList()

    /**
     * Whether [close] was called since the last opening. [FakePdfEngine] opens the document again
     * on each `open`: the real engine gives a new document every time, so a document closed by a
     * previous user (a thumbnail, a reader that was left) must not reach the next one closed.
     */
    @Volatile
    var isClosed: Boolean = false
        internal set

    /** What [outline] returns. */
    @Volatile
    var outline: List<OutlineItem> = emptyList()

    /** Links of each page, by zero-based page index; pages missing from the map have none. */
    @Volatile
    var links: Map<Int, List<PdfLink>> = emptyMap()

    /**
     * Occurrences of a query, in any page order: [search] sorts them by page, applies
     * [SearchOptions.maxHits] and reports every page. Ignores the search options otherwise.
     */
    @Volatile
    var searchHits: (query: String) -> List<SearchHit> = { emptyList() }

    /**
     * Wait before each page of a search, in the coroutine that collects it (virtual time under
     * `runTest`): lets a test see the results arrive one page after the other, and cancel the
     * search in the middle.
     */
    @Volatile
    var searchPageDelay: Duration = Duration.ZERO

    /**
     * Selection returned for each page, whatever the points and mode; pages missing from the map
     * have no text (empty selection).
     */
    @Volatile
    var selections: Map<Int, TextSelection> = emptyMap()

    /** Areas of text of each page, for [isOnText]; pages missing from the map have none. */
    @Volatile
    var textAreas: Map<Int, List<PdfRect>> = emptyMap()

    /** What [canCopyText] returns. */
    @Volatile
    override var canCopyText: Boolean = true

    /** Every call to [search], in order. */
    val searches: MutableList<Search> = CopyOnWriteArrayList()

    /** Every call to [selectText], in order. */
    val selectionRequests: MutableList<SelectionRequest> = CopyOnWriteArrayList()

    /**
     * One call to [search].
     *
     * @property query searched text.
     * @property options search options.
     * @property startPage page searched first.
     */
    data class Search(val query: String, val options: SearchOptions, val startPage: Int = 0)

    /**
     * One call to [selectText].
     *
     * @property pageIndex requested page.
     * @property start first point.
     * @property end second point.
     * @property mode requested mode.
     */
    data class SelectionRequest(val pageIndex: Int, val start: PdfPoint, val end: PdfPoint, val mode: SelectionMode)

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
    override suspend fun pageSize(index: Int): PageSize {
        checkPage(index)
        return pageSizes[index]
    }

    /** Records the request in [renders] and fills [target] with [FAKE_PAGE_COLOR]. */
    override suspend fun renderPage(index: Int, target: Bitmap, region: PdfRect, scale: Float, nightMode: Boolean) {
        checkPage(index)
        renders += Render(index, target.width, target.height, region, scale)
        target.eraseColor(FAKE_PAGE_COLOR)
    }

    /** Returns the [outline] set by the test. */
    override suspend fun outline(): List<OutlineItem> {
        ensureOpen()
        return outline
    }

    /**
     * Records the call in [searches], then emits a result for each page, from [startPage] and
     * wrapping around like the real engine, after [searchPageDelay], with the [searchHits] of
     * [query] on that page, until [SearchOptions.maxHits]; nothing for a blank query.
     */
    override fun search(query: String, options: SearchOptions, startPage: Int): Flow<SearchPageResult> = flow {
        checkPage(startPage)
        searches += Search(query, options, startPage)
        if (query.isBlank()) return@flow
        val byPage = searchHits(query).groupBy { it.pageIndex }
        var found = 0
        for (index in (startPage until pageCount) + (0 until startPage)) {
            delay(searchPageDelay)
            ensureOpen()
            val hits = byPage[index].orEmpty()
            val remaining = options.maxHits - found
            val isLimitReached = hits.size >= remaining
            emit(SearchPageResult(index, hits.take(remaining), isLimitReached))
            if (isLimitReached) return@flow
            found += hits.size
        }
    }

    /** Records the call in [selectionRequests] and returns the [selections] entry of the page. */
    override suspend fun selectText(index: Int, start: PdfPoint, end: PdfPoint, mode: SelectionMode): TextSelection {
        checkPage(index)
        selectionRequests += SelectionRequest(index, start, end, mode)
        return selections[index] ?: TextSelection.empty(index)
    }

    /** Whether [point] lies in one of the [textAreas] of the page. */
    override suspend fun isOnText(index: Int, point: PdfPoint): Boolean {
        checkPage(index)
        return textAreas[index].orEmpty().any { it.contains(point.x, point.y) }
    }

    /** Returns the [links] set by the test for the page. */
    override suspend fun links(index: Int): List<PdfLink> {
        checkPage(index)
        return links[index].orEmpty()
    }

    /** Marks the document as closed ([isClosed]). */
    override fun close() {
        isClosed = true
    }

    private fun ensureOpen() {
        if (isClosed) throw PdfError.Closed()
    }

    private fun checkPage(index: Int) {
        ensureOpen()
        if (index !in 0 until pageCount) throw PdfError.PageOutOfRange(index, pageCount)
    }
}

/**
 * [PdfEngine] for tests: returns [result] for every source and records the requests.
 *
 * @property result what `open` returns; a failure should hold a `PdfError`.
 * @property password when set, the documents are encrypted with this user password, like the
 *   real engine: no password gives [PdfError.PasswordRequired], another one
 *   [PdfError.IncorrectPassword], and this one [result]. Test data, never a real secret.
 */
class FakePdfEngine(
    var result: Result<PdfDocument> = Result.success(FakePdfDocument()),
    var password: String? = null,
) : PdfEngine {
    /** Every opened source, in order. */
    val openedSources: MutableList<DocumentSource> = CopyOnWriteArrayList()

    /** The password given to each call, in order (`null` when none). */
    val givenPasswords: MutableList<String?> = CopyOnWriteArrayList()

    /**
     * Records the call in [openedSources] and [givenPasswords], then checks [password] and returns
     * [result]; a [FakePdfDocument] it returns is opened again ([FakePdfDocument.isClosed]), like
     * the new document the real engine would give.
     */
    override suspend fun open(source: DocumentSource, password: String?): Result<PdfDocument> {
        openedSources += source
        givenPasswords += password
        val expected = this.password
        val outcome = when {
            expected == null -> result
            password == null -> Result.failure(PdfError.PasswordRequired())
            password != expected -> Result.failure(PdfError.IncorrectPassword())
            else -> result
        }
        (outcome.getOrNull() as? FakePdfDocument)?.isClosed = false
        return outcome
    }
}
