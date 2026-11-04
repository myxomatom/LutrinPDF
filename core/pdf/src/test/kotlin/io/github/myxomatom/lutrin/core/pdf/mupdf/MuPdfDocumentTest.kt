// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertThrows
import org.junit.Test

/** Whole A4 page, used to select or render everything. */
internal val wholePage = PdfRect(0f, 0f, 595f, 842f)

class MuPdfDocumentTest {
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
            assertThat(outline.map { it.pageIndex }).containsExactly(0, 2, 4).inOrder()
            assertThat(outline[1].children.map { it.title }).containsExactly("Section 2.1", "Section 2.2").inOrder()
            assertThat(outline[1].children[1].pageIndex).isEqualTo(3)
            assertThat(outline[2].children[0].children.single().title).isEqualTo("Sous-section 3.1.1")
        }
    }

    @Test
    fun `returns an empty outline when the document has none`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { assertThat(it.outline()).isEmpty() }
    }

    @Test
    fun `reads internal and external links`() = runTest {
        openTestPdf("pdfs/links_internal_external.pdf").use { document ->
            val links = document.links(0)

            assertThat(links.filterIsInstance<PdfLink.Internal>().single().pageIndex).isEqualTo(2)
            assertThat(links.filterIsInstance<PdfLink.External>().single().uri).isEqualTo("https://example.org/")
            assertThat(links.all { it.bounds.width > 0f }).isTrue()
            assertThat(document.links(1)).isEmpty()
        }
    }

    @Test
    fun `finds text on every page of a large document`() = runTest {
        openTestPdf("pdfs/large_600pages.pdf").use { document ->
            val hits = document.search("Contenu de la page 600").toList()

            // The generator repeats the page body three times.
            assertThat(hits.map { it.pageIndex }).containsExactly(599, 599, 599)
            assertThat(hits.all { it.areas.isNotEmpty() }).isTrue()
        }
    }

    @Test
    fun `search ignores case unless asked`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { document ->
            assertThat(document.search("DOCUMENT SIMPLE").toList()).hasSize(1)
            assertThat(document.search("DOCUMENT SIMPLE", SearchOptions(matchCase = true)).toList()).isEmpty()
        }
    }

    @Test
    fun `search ignores accents unless asked`() = runTest {
        openTestPdf("pdfs/non_latin_text.pdf").use { document ->
            assertThat(document.search("l'ete").toList()).isNotEmpty()
            assertThat(document.search("l'ete", SearchOptions(matchAccents = true)).toList()).isEmpty()
            assertThat(document.search("l'été", SearchOptions(matchAccents = true)).toList()).isNotEmpty()
        }
    }

    @Test
    fun `search finds Chinese text`() = runTest {
        openTestPdf("pdfs/non_latin_text.pdf").use { assertThat(it.search("中文").toList()).hasSize(1) }
    }

    @Test
    fun `search yields nothing for a blank query or a scanned document`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { assertThat(it.search("   ").toList()).isEmpty() }
        openTestPdf("pdfs/scanned_images_only.pdf").use { assertThat(it.search("page").toList()).isEmpty() }
    }

    @Test
    fun `copies the text of a selection`() = runTest {
        openTestPdf("pdfs/simple_1page.pdf").use { document ->
            assertThat(document.textAt(0, wholePage)).contains("Document simple")
            assertThat(document.textAt(0, PdfRect(0f, 0f, 1f, 1f))).isEmpty()
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
    }
}
