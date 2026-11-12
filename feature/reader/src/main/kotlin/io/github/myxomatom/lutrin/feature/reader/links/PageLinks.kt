// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.links

import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Number of pages whose links are kept: the pages on screen and their neighbours, many times
 * over. A page holds a handful of links, so the cache stays small whatever the document.
 */
internal const val LINK_CACHE_PAGES = 64

/**
 * Links of the pages of an open document (F09), read from the engine off the main thread and
 * kept for the last [capacity] pages read, so that a tap or TalkBack finds them at once.
 *
 * A page whose links cannot be read (document closed, engine failure) has none: the reader then
 * behaves as if the page had no link, and asks again the next time.
 *
 * @param document the open document, or `null` for a reader without links (tests, previews).
 * @param dispatcher where the engine is asked: never the main thread (`AGENTS.md` §3).
 * @param capacity number of pages kept, least recently used first out.
 */
class PageLinks(
    private val document: PdfDocument?,
    private val dispatcher: CoroutineDispatcher,
    private val capacity: Int = LINK_CACHE_PAGES,
) {
    private val cache = object : LinkedHashMap<Int, List<PdfLink>>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<PdfLink>>?): Boolean = size > capacity
    }

    /**
     * Links of the page at [pageIndex], in document order: from the cache, or read from the
     * engine on the dispatcher.
     *
     * @param pageIndex zero-based page of the document.
     * @return the links, empty when the page has none or when they could not be read.
     */
    suspend fun linksOf(pageIndex: Int): List<PdfLink> = cached(pageIndex) ?: read(pageIndex)

    /**
     * Links of the page at [pageIndex] if they were read already, without asking the engine.
     *
     * @return the links, or `null` when they are not in the cache.
     */
    fun cached(pageIndex: Int): List<PdfLink>? = synchronized(cache) { cache[pageIndex] }

    /**
     * Reads the links of a page from the engine and caches them; a failure is not cached, so
     * that a later call tries again.
     */
    private suspend fun read(pageIndex: Int): List<PdfLink> {
        val source = document ?: return emptyList()
        val result = runCatching { withContext(dispatcher) { source.links(pageIndex) } }
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        // Any engine failure means "no link", never a crash.
        return result
            .onSuccess { links -> synchronized(cache) { cache[pageIndex] = links } }
            .getOrDefault(emptyList())
    }

    private companion object {
        /** Default load factor of [LinkedHashMap]. */
        const val LOAD_FACTOR = 0.75f
    }
}
