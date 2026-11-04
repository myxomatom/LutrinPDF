// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import android.graphics.Bitmap
import android.util.LruCache
import javax.inject.Inject

/**
 * Least-recently-used cache of rendered pages, bounded in bytes (`AGENTS.md` §8). Evicted
 * bitmaps are not recycled: the screen may still be drawing them, the garbage collector frees
 * them once unused.
 *
 * @param maxBytes total size of the cached bitmaps.
 */
class PageBitmapCache(maxBytes: Int) {
    /** Cache sized from the memory available to the app: [MEMORY_FRACTION] of it. */
    @Inject
    constructor() : this((Runtime.getRuntime().maxMemory() / MEMORY_FRACTION).toInt())

    /**
     * Identifies a rendering.
     *
     * @property pageIndex page index.
     * @property widthPx width the page was requested at.
     */
    data class Key(val pageIndex: Int, val widthPx: Int)

    private val cache = object : LruCache<Key, Bitmap>(maxBytes) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
    }

    /** Cached rendering for [key], or `null`. */
    operator fun get(key: Key): Bitmap? = cache.get(key)

    /** Stores a rendering, evicting the least recently used ones if needed. */
    fun put(key: Key, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    /** Empties the cache (the document is closed). */
    fun clear() {
        cache.evictAll()
    }

    /** Sizing. */
    companion object {
        /** Share of the app's memory given to rendered pages: one quarter. */
        const val MEMORY_FRACTION: Long = 4
    }
}
