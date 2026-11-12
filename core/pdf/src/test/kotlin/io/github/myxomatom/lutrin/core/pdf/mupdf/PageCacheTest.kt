// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.Document
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import org.junit.After
import org.junit.Before
import org.junit.Test

class PageCacheTest {
    private lateinit var document: Document
    private lateinit var cache: PageCache

    @Before
    fun open() {
        assumeHostEngine()
        document = Document.openDocument(TestAssets.file("pdfs/mixed_page_sizes.pdf").absolutePath)
        cache = PageCache(document, pageCount = 5, capacity = 2)
    }

    @After
    fun close() {
        if (::cache.isInitialized) cache.clear()
        if (::document.isInitialized) document.destroy()
    }

    @Test
    fun `a page read outside the cache is not kept`() {
        cache[0]
        cache[1]

        val width = cache.withPageOutsideCache(1) { it.bounds.x1 }
        val other = cache.withPageOutsideCache(4) { it.bounds.x1 }

        assertThat(cache.size).isEqualTo(2)
        assertThat(width).isEqualTo(792f)
        assertThat(other).isEqualTo(842f)
    }

    @Test
    fun `a page read outside the cache does not become the most recent`() {
        val first = cache[0]
        cache[1]
        cache.withPageOutsideCache(0) { }

        cache[2] // Evicts the least recently used page: page 0, despite the read above.

        assertThat(cache[0]).isNotSameInstanceAs(first)
    }

    @Test
    fun `rejects a page out of range`() {
        val error = runCatching { cache.withPageOutsideCache(5) { } }.exceptionOrNull()

        assertThat(error).isInstanceOf(PdfError.PageOutOfRange::class.java)
    }
}
