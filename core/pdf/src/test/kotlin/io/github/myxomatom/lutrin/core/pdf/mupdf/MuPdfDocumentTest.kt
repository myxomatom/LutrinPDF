// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.Document
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfMetadata
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
import io.github.myxomatom.lutrin.core.domain.pdf.SearchPageResult
import io.github.myxomatom.lutrin.core.domain.pdf.SelectionMode
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** Whole A4 page, used to select or render everything. */
internal val wholePage = PdfRect(0f, 0f, 595f, 842f)

/** The whole text of a page, selected from corner to corner. */
internal suspend fun PdfDocument.allText(index: Int = 0): String =
    selectText(index, PdfPoint(0f, 0f), PdfPoint(wholePage.right, wholePage.bottom)).text

/** Every result of a search, in order. */
internal suspend fun Flow<SearchPageResult>.hits(): List<SearchHit> = toList().flatMap { it.hits }

class MuPdfDocumentTest {
    @Before
    fun requireHostEngine() = assumeHostEngine()

    @Test
    fun `reads the document information`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { document ->
            with(document.metadata) {
                assertThat(pageCount).isEqualTo(1)
                assertThat(title).isEqualTo("simple_1page.pdf")
                assertThat(author).isEqualTo("Lutrin test suite")
                assertThat(creationDate).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
                assertThat(format).startsWith("PDF")
                assertThat(isEncrypted).isFalse()
                assertThat(subject).isNull()
            }
        }
    }

    @Test
    fun `reports each page size with its rotation`() = runTest {
        openTestPdf("pdfs/mixed_page_sizes.pdf").use { document ->
            val sizes = (0 until document.pageCount).map { document.pageSize(it).rounded() }

            assertThat(sizes)
                .containsExactly(595 to 842, 792 to 612, 420 to 595, 500 to 500, 842 to 595)
                .inOrder()
        }
    }

    @Test
    fun `rejects a page index out of range`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { document ->
            val error = runCatching { document.pageSize(1) }.exceptionOrNull()

            assertThat(error).isInstanceOf(PdfError.PageOutOfRange::class.java)
            assertThat(
                runCatching {
                    document.links(-1)
                }.exceptionOrNull(),
            ).isInstanceOf(PdfError.PageOutOfRange::class.java)
        }
    }

    @Test
    fun `reads a nested outline with page targets`() = runTest {
        openTestPdf("pdfs/nested_outline.pdf").use { document ->
            val outline = document.outline()

            assertThat(outline.map { it.title }).containsExactly("Chapitre 1", "Chapitre 2", "Chapitre 3").inOrder()
            assertThat(outline.map { it.destination?.pageIndex }).containsExactly(0, 2, 4).inOrder()
            assertThat(outline[1].children.map { it.title }).containsExactly("Section 2.1", "Section 2.2").inOrder()
            assertThat(outline[1].children[1].destination?.pageIndex).isEqualTo(3)
            assertThat(outline[2].children[0].children.single().title).isEqualTo("Sous-section 3.1.1")
        }
    }

    @Test
    fun `returns an empty outline when the document has none`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { assertThat(it.outline()).isEmpty() }
    }

    @Test
    fun `reads an outline entry pointing inside a page and an external one`() = runTest {
        openTestPdf("pdfs/links_internal_external.pdf").use { document ->
            val (middle, external) = document.outline()

            assertThat(middle.title).isEqualTo("Milieu de la page 4")
            assertThat(middle.destination?.pageIndex).isEqualTo(3)
            assertThat(middle.destination?.y).isWithin(1f).of(MIDDLE_OF_PAGE)
            assertThat(middle.uri).isNull()
            assertThat(external.destination).isNull()
            assertThat(external.uri).isEqualTo("https://example.org/sommaire")
        }
    }

    @Test
    fun `reads the outline of a long document`() = runTest {
        openTestPdf("pdfs/large_600pages.pdf").use { document ->
            val outline = document.outline()

            assertThat(outline).hasSize(12)
            assertThat(outline.last().title).isEqualTo("Partie 12")
            assertThat(outline.last().destination?.pageIndex).isEqualTo(550)
            assertThat(outline.last().children.last().destination?.pageIndex).isEqualTo(590)
        }
    }

    @Test
    fun `reads internal and external links`() = runTest {
        openTestPdf("pdfs/links_internal_external.pdf").use { document ->
            val links = document.links(0)

            assertThat(links.filterIsInstance<PdfLink.Internal>().single().destination.pageIndex).isEqualTo(2)
            assertThat(links.filterIsInstance<PdfLink.External>().single().uri).isEqualTo("https://example.org/")
            assertThat(links.all { it.bounds.width > 0f }).isTrue()
            assertThat(document.links(2)).isEmpty()
        }
    }

    @Test
    fun `resolves a link to the middle of a page and a named destination`() = runTest {
        openTestPdf("pdfs/links_internal_external.pdf").use { document ->
            val (middle, named) = document.links(1).filterIsInstance<PdfLink.Internal>().map { it.destination }

            assertThat(middle.pageIndex).isEqualTo(3)
            assertThat(middle.y).isWithin(1f).of(MIDDLE_OF_PAGE)
            assertThat(named.pageIndex).isEqualTo(2)
            assertThat(named.y).isWithin(1f).of(NAMED_DESTINATION_Y)
        }
    }

    @Test
    fun `keeps the raw URI of every external link, whatever its scheme`() = runTest {
        openTestPdf("pdfs/links_internal_external.pdf").use { document ->
            val uris = document.links(1).filterIsInstance<PdfLink.External>().map { it.uri }

            // Filtering the schemes is the reader's decision (ADR 0020); MuPDF turns the
            // relative URI into a file:// one.
            assertThat(uris)
                .containsExactly(
                    "mailto:lutrin@example.org",
                    "javascript:alert(1)",
                    "file:///sdcard/secret.pdf",
                    "file://annexe.html",
                ).inOrder()
        }
    }

    @Test
    fun `finds text on every page of a large document`() = runTest {
        openTestPdf("pdfs/large_600pages.pdf").use { document ->
            val hits = document.search("Contenu de la page 600").hits()

            // The generator repeats the page body three times.
            assertThat(hits.map { it.pageIndex }).containsExactly(599, 599, 599)
            assertThat(hits.all { it.areas.isNotEmpty() }).isTrue()
        }
    }

    @Test
    fun `reports every page searched, with or without results`() = runTest {
        openTestPdf("pdfs/nested_outline.pdf").use { document ->
            val pages = document.search("section 2.2").toList()

            assertThat(pages.map { it.pageIndex }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
            assertThat(pages.filter { it.hits.isNotEmpty() }.map { it.pageIndex }).containsExactly(3)
            assertThat(pages.none { it.isLimitReached }).isTrue()
        }
    }

    @Test
    fun `stops at the maximum number of results`() = runTest {
        openTestPdf("pdfs/large_600pages.pdf").use { document ->
            // "lutrin" appears several times on every page.
            val pages = document.search("lutrin", SearchOptions(maxHits = 10)).toList()

            assertThat(pages.sumOf { it.hits.size }).isEqualTo(10)
            assertThat(pages.last().isLimitReached).isTrue()
            assertThat(pages.size).isLessThan(600)
        }
    }

    @Test
    fun `gives each result an excerpt in context`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { document ->
            val excerpt = document.search("PUPITRE").hits().first().excerpt

            assertThat(excerpt?.match).isEqualTo("pupitre")
            assertThat(excerpt?.before).endsWith("un ")
            assertThat(excerpt?.after).startsWith(" sur lequel")
        }
    }

    @Test
    fun `finds a word cut by a hyphen at the end of a line`() = runTest {
        openTestPdf("pdfs/text_selection.pdf").use { document ->
            val hit = document.search("bibliothèque").hits().single()

            assertThat(hit.areas).hasSize(2)
            assertThat(hit.excerpt?.match).isEqualTo("bibliothèque")
            assertThat(hit.excerpt?.after).startsWith(" municipale")
        }
    }

    @Test
    fun `search ignores case unless asked`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { document ->
            assertThat(document.search("DOCUMENT SIMPLE").hits()).hasSize(1)
            assertThat(document.search("DOCUMENT SIMPLE", SearchOptions(matchCase = true)).hits()).isEmpty()
        }
    }

    @Test
    fun `search ignores accents unless asked`() = runTest {
        openTestPdf("pdfs/non_latin_text.pdf").use { document ->
            assertThat(document.search("l'ete").hits()).isNotEmpty()
            assertThat(document.search("l'ete", SearchOptions(matchAccents = true)).hits()).isEmpty()
            assertThat(document.search("l'été", SearchOptions(matchAccents = true)).hits()).isNotEmpty()
        }
    }

    @Test
    fun `search finds Chinese text`() = runTest {
        openTestPdf("pdfs/non_latin_text.pdf").use { assertThat(it.search("中文").hits()).hasSize(1) }
    }

    @Test
    fun `search finds Arabic text`() = runTest {
        openTestPdf("pdfs/text_selection.pdf").use { document ->
            val hit = document.search("مكتبة").hits().single()

            assertThat(hit.excerpt?.match).isEqualTo("مكتبة")
        }
    }

    @Test
    fun `search yields nothing for a blank query or a scanned document`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { assertThat(it.search("   ").toList()).isEmpty() }
        openTestPdf("pdfs/scanned_images_only.pdf").use { document ->
            val pages = document.search("page").toList()

            assertThat(pages.map { it.pageIndex }).containsExactly(0, 1).inOrder()
            assertThat(pages.flatMap { it.hits }).isEmpty()
        }
    }

    @Test
    fun `selects the whole text of a page`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { document ->
            val selection = document.selectText(0, PdfPoint(0f, 0f), PdfPoint(595f, 842f))

            assertThat(selection.text).startsWith("Document simple")
            assertThat(selection.quads).isNotEmpty()
        }
    }

    @Test
    fun `a long press selects a word and lines mode its line`() = runTest {
        openTestPdf("pdfs/text_selection.pdf").use { document ->
            // Inside "mot" on the first line of the body (core/testing/scripts/generate_test_files.py).
            val press = PdfPoint(98f, 136f)

            assertThat(document.selectText(0, press, press, SelectionMode.Words).text).isEqualTo("mot")
            assertThat(document.selectText(0, press, press, SelectionMode.Lines).text)
                .isEqualTo("Le mot suivant est coupé en fin de ligne : la biblio-")
            assertThat(document.selectText(0, press, press, SelectionMode.Characters).isEmpty).isTrue()
        }
    }

    @Test
    fun `selects a whole Arabic word in reading order`() = runTest {
        openTestPdf("pdfs/text_selection.pdf").use { document ->
            val word = document.search("مكتبة").hits().single().bounds
            val press = PdfPoint((word.left + word.right) / 2, (word.top + word.bottom) / 2)

            val selection = document.selectText(0, press, press, SelectionMode.Words)

            assertThat(selection.text).isEqualTo("مكتبة")
            assertThat(selection.quads.single().bounds.width).isWithin(1f).of(word.width)
        }
    }

    @Test
    fun `resolves explicit destinations on a rotated page and an upright one`() = runTest {
        openTestPdf("pdfs/rotated_destinations.pdf").use { document ->
            val destinations = document.links(0).filterIsInstance<PdfLink.Internal>().map { it.destination }

            assertThat(destinations)
                .containsExactly(
                    // Page 2 is turned by 90°: the single coordinate of FitH, FitV and an incomplete
                    // XYZ is lost by MuPDF, so only the page is kept.
                    PdfDestination(1),
                    PdfDestination(1),
                    // In the coordinates of the turned page: PDF (x, y) becomes (y, x).
                    PdfDestination(1, x = 200f, y = 100f, width = 200f, height = 200f),
                    PdfDestination(1, x = 500f, y = 100f),
                    PdfDestination(1),
                    // Page 3 is upright, 842 points high: y is measured from the top.
                    PdfDestination(2, y = 542f),
                    PdfDestination(2, x = 100f, y = 442f, width = 200f, height = 200f),
                ).inOrder()
        }
    }

    @Test
    fun `returns every occurrence of a page that has many`() = runTest {
        openTestPdf("pdfs/search_many_hits.pdf").use { document ->
            val hits = document.search("lutrin", SearchOptions(maxHits = 5_000)).hits()

            assertThat(hits).hasSize(800)
        }
    }

    @Test
    fun `starts the search at the given page and wraps around`() = runTest {
        openTestPdf("pdfs/nested_outline.pdf").use { document ->
            val pages = document.search("section", startPage = 4).toList()

            assertThat(pages.map { it.pageIndex }).containsExactly(4, 5, 0, 1, 2, 3).inOrder()
            assertThat(runCatching { document.search("section", startPage = 6).toList() }.exceptionOrNull())
                .isInstanceOf(PdfError.PageOutOfRange::class.java)
        }
    }

    @Test
    fun `describes only the results it returns`() = runTest {
        openTestPdf("pdfs/search_many_hits.pdf").use { document ->
            val pages = document.search("lutrin", SearchOptions(maxHits = 15)).toList()

            assertThat(pages.single().hits).hasSize(15)
            assertThat(pages.single().hits.all { it.excerpt?.match == "lutrin" }).isTrue()
        }
    }

    @Test
    fun `tells whether a point is on text`() = runTest {
        openTestPdf("pdfs/text_selection.pdf").use { document ->
            assertThat(document.isOnText(0, PdfPoint(98f, 136f))).isTrue()
            assertThat(document.isOnText(0, PdfPoint(20f, 20f))).isFalse()
        }
        openTestPdf("pdfs/scanned_images_only.pdf").use { assertThat(it.isOnText(0, PdfPoint(100f, 100f))).isFalse() }
    }

    @Test
    fun `places the handles of a selection`() = runTest {
        openTestPdf("pdfs/text_selection.pdf").use { document ->
            val press = PdfPoint(98f, 136f)

            val selection = document.selectText(0, press, press, SelectionMode.Words)
            val start = checkNotNull(selection.start)
            val end = checkNotNull(selection.end)

            assertThat(start.x).isLessThan(end.x)
            assertThat(document.selectText(0, start, end).text).isEqualTo("mot")
        }
    }

    @Test
    fun `a blank search on a closed document reports it closed`() = runTest {
        val document = openTestPdf("pdfs/simple_1page.pdf")
        document.close()

        assertThat(runCatching { document.search(" ").toList() }.exceptionOrNull())
            .isInstanceOf(PdfError.Closed::class.java)
    }

    @Test
    fun `selects nothing on a scanned page`() = runTest {
        openTestPdf("pdfs/scanned_images_only.pdf").use { document ->
            assertThat(document.selectText(0, PdfPoint(0f, 0f), PdfPoint(595f, 842f), SelectionMode.Lines).isEmpty)
                .isTrue()
        }
    }

    @Test
    fun `renders a page on a white background`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { document ->
            val pixels = document.renderPixels(
                0,
                RenderSpec(wholePage, scale = 0.5f, width = 298, height = 421, nightMode = false),
            )

            assertThat(pixels.size).isEqualTo(298 * 421)
            assertThat(pixels.count { it == WHITE }).isGreaterThan(pixels.size / 2)
            assertThat(pixels.any { it.isDark() }).isTrue()
            assertThat(pixels.all { it ushr ALPHA_SHIFT == OPAQUE }).isTrue()
        }
    }

    @Test
    fun `night mode inverts the lightness`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { document ->
            val pixels = document.renderPixels(
                0,
                RenderSpec(wholePage, scale = 0.5f, width = 298, height = 421, nightMode = true),
            )

            assertThat(pixels.count { it.isDark() }).isGreaterThan(pixels.size / 2)
        }
    }

    @Test
    fun `renders only the requested region`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { document ->
            // Bottom-right corner of the page: blank.
            val pixels = document.renderPixels(
                0,
                RenderSpec(PdfRect(500f, 800f, 595f, 842f), scale = 1f, width = 95, height = 42, nightMode = false),
            )

            assertThat(pixels.all { it == WHITE }).isTrue()
        }
    }

    @Test
    fun `renders scanned pages`() = runTest {
        openTestPdf("pdfs/scanned_images_only.pdf").use { document ->
            // Title line of the scanned page, at twice its size.
            val title = PdfRect(60f, 60f, 400f, 110f)
            val pixels = document.renderPixels(
                0,
                RenderSpec(title, scale = 2f, width = 680, height = 100, nightMode = false),
            )

            assertThat(pixels.any { it.isDark() }).isTrue()
        }
    }

    @Test
    fun `rejects an invalid render size`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { document ->
            val error = runCatching { RenderSpec(wholePage, 1f, 0, 10, false) }.exceptionOrNull()

            assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `serialises concurrent calls on the document thread`() = runBlocking<Unit> {
        openTestPdf("pdfs/large_600pages.pdf").use { document ->
            val sizes =
                withContext(Dispatchers.Default) {
                    (0 until 50).map { index -> async { document.pageSize(index * 12) } }.awaitAll()
                }

            assertThat(sizes.toSet()).containsExactly(PageSize(595f, 842f))
        }
    }

    @Test
    fun `fails cleanly after being closed`() = runTest {
        val document = openTestPdf("pdfs/simple_1page.pdf")
        document.close()
        document.close() // Closing twice is harmless.

        assertThat(runCatching { document.pageSize(0) }.exceptionOrNull()).isInstanceOf(PdfError.Closed::class.java)
        assertThrows(PdfError.Closed::class.java) { runBlocking { document.search("x").toList() } }
    }

    @Test
    fun `every call fails with Closed after close`() = runTest {
        val document = openTestPdf("pdfs/links_internal_external.pdf")
        document.close()

        assertThat(runCatching { document.outline() }.exceptionOrNull()).isInstanceOf(PdfError.Closed::class.java)
        assertThat(
            runCatching { document.selectText(0, PdfPoint(0f, 0f), PdfPoint(10f, 10f)) }.exceptionOrNull(),
        ).isInstanceOf(PdfError.Closed::class.java)
        assertThat(runCatching { document.links(0) }.exceptionOrNull()).isInstanceOf(PdfError.Closed::class.java)
        assertThat(
            runCatching { document.renderPixels(0, RenderSpec(wholePage, 0.1f, 60, 84, false)) }.exceptionOrNull(),
        ).isInstanceOf(PdfError.Closed::class.java)
    }

    @Test
    fun `selectText rejects a page index out of range`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { document ->
            val origin = PdfPoint(0f, 0f)

            assertThat(runCatching { document.selectText(1, origin, origin) }.exceptionOrNull())
                .isInstanceOf(PdfError.PageOutOfRange::class.java)
            assertThat(runCatching { document.selectText(-1, origin, origin) }.exceptionOrNull())
                .isInstanceOf(PdfError.PageOutOfRange::class.java)
        }
    }

    @Test
    fun `reports Closed when the document is closed just before a call is dispatched`() = runBlocking<Unit> {
        val executor = HookedExecutor()
        val document =
            engine(dispatchers = { executor.asCoroutineDispatcher() })
                .open(TestAssets.source("pdfs/simple_1page.pdf"))
                .getOrThrow()
        // Closes the document after the call passed its first check, right before it reaches
        // the document thread: the executor then rejects the call.
        executor.beforeNextExecute = { document.close() }

        val error = runCatching { document.pageSize(0) }.exceptionOrNull()

        assertThat(error).isInstanceOf(PdfError.Closed::class.java)
    }

    @Test
    fun `close releases the resources and reports the failure when destroying the document fails`() {
        // Regression: the failure used to escape on the document thread, uncaught, which
        // crashes an Android app although the user has already left the document.
        val uncaught = AtomicReference<Throwable>()
        val threads = CopyOnWriteArrayList<Thread>()
        val executor =
            Executors.newSingleThreadExecutor { task ->
                val thread = Thread(task)
                thread.setUncaughtExceptionHandler { _, error -> uncaught.set(error) }
                thread.also(threads::add)
            }
        val released = AtomicBoolean(false)
        val reported = RecordingReleaseFailures()
        val failingDocument =
            object : Document(0L) {
                override fun destroy() {
                    error("Simulated native failure")
                }
            }

        MuPdfDocument(failingDocument, executor.asCoroutineDispatcher(), PdfMetadata(pageCount = 1), reported) {
            released.set(true)
        }.close()

        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
        threads.forEach { it.join(TimeUnit.SECONDS.toMillis(10)) }
        assertThat(released.get()).isTrue()
        // The failure is reported, not swallowed, and nothing escapes on the document thread.
        assertThat(reported.failures.single()).isInstanceOf(IllegalStateException::class.java)
        assertThat(uncaught.get()).isNull()
    }

    @Test
    fun `close reports every failed step and still runs the next ones`() {
        val executor = Executors.newSingleThreadExecutor()
        val reported = RecordingReleaseFailures()
        val failingDocument =
            object : Document(0L) {
                override fun destroy() {
                    error("Simulated native failure")
                }
            }

        MuPdfDocument(failingDocument, executor.asCoroutineDispatcher(), PdfMetadata(pageCount = 1), reported) {
            throw IOException("Simulated descriptor failure")
        }.close()

        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
        assertThat(
            reported.failures.map {
                it::class
            },
        ).containsExactly(IllegalStateException::class, IOException::class).inOrder()
    }

    @Test
    fun `a normal close reports nothing`() = runTest {
        val reported = RecordingReleaseFailures()
        val document = engine(releaseFailures = reported).open(TestAssets.source("pdfs/simple_1page.pdf")).getOrThrow()

        document.close()

        assertThat(reported.failures).isEmpty()
    }

    /** Single-thread executor that runs a hook right before accepting its next task. */
    private class HookedExecutor :
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(),
            { task -> Thread(task).apply { isDaemon = true } },
        ) {
        @Volatile
        var beforeNextExecute: (() -> Unit)? = null

        override fun execute(command: Runnable) {
            val hook = beforeNextExecute
            beforeNextExecute = null
            hook?.invoke()
            super.execute(command)
        }
    }

    private fun PageSize.rounded() = Math.round(width) to Math.round(height)

    private fun Int.isDark(): Boolean =
        (this shr RED_SHIFT and BYTE) + (this shr GREEN_SHIFT and BYTE) + (this and BYTE) < DARK_SUM

    private companion object {
        const val WHITE = -1 // 0xFFFFFFFF
        const val ALPHA_SHIFT = 24
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val BYTE = 0xFF
        const val OPAQUE = 0xFF
        const val DARK_SUM = 3 * 0x60

        // Targets of the links of links_internal_external.pdf, in points from the top of the page.
        const val MIDDLE_OF_PAGE = 421f
        const val NAMED_DESTINATION_Y = 100f
    }
}
