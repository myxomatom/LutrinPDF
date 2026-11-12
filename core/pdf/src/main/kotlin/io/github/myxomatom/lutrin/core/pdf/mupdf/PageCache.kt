// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Page
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError

/** Pages kept loaded, so that rendering neighbouring tiles does not reload the page. */
internal const val PAGE_CACHE_SIZE = 4

private const val LOAD_FACTOR = 0.75f

/**
 * Least-recently-used cache of loaded MuPDF pages. Not thread-safe: only used on the thread
 * of its document.
 */
internal class PageCache(
    private val document: Document,
    private val pageCount: Int,
    private val capacity: Int = PAGE_CACHE_SIZE,
) {
    private val pages =
        object : LinkedHashMap<Int, Page>(capacity, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Page>): Boolean =
                (size > capacity).also { evict -> if (evict) eldest.value.destroy() }
        }

    /** Returns the page at [index], loading it if needed; throws [PdfError.PageOutOfRange]. */
    operator fun get(index: Int): Page {
        if (index !in 0 until pageCount) throw PdfError.PageOutOfRange(index, pageCount)
        return pages.getOrPut(index) { document.loadPage(index) }
    }

    /**
     * Runs [block] on the page at [index] without changing the cache: a page already loaded is
     * used without becoming the most recent, any other one is loaded for [block] only and then
     * released. For walks over the whole document (search), which must not evict the pages the
     * reader shows. Throws [PdfError.PageOutOfRange].
     */
    fun <T> withPageOutsideCache(index: Int, block: (Page) -> T): T {
        if (index !in 0 until pageCount) throw PdfError.PageOutOfRange(index, pageCount)
        // Iterating the entries, unlike get(), does not change the access order.
        pages.entries.firstOrNull { it.key == index }?.let { return block(it.value) }
        val page = document.loadPage(index)
        try {
            return block(page)
        } finally {
            page.destroy()
        }
    }

    /** Number of pages currently loaded. */
    val size: Int get() = pages.size

    /** Releases every loaded page. */
    fun clear() {
        pages.values.forEach { it.destroy() }
        pages.clear()
    }
}
