// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.render

import android.graphics.Bitmap
import android.util.LruCache

/** Share of the app heap given to rendered pages (`SPEC.md` §3, Mémoire). */
private const val HEAP_SHARE = 4

/** Upper bound of the cache, whatever the heap: a few screens of pages are enough. */
private const val MAX_CACHE_BYTES = 128L * 1024 * 1024

/**
 * Identifies a base rendering.
 *
 * @property pageIndex zero-based page.
 * @property widthPx rendering width in pixels.
 * @property nightMode whether colours were inverted.
 */
data class RenderKey(val pageIndex: Int, val widthPx: Int, val nightMode: Boolean)

/**
 * Least-recently-used cache of rendered pages, bounded in bytes. Evicted bitmaps are not
 * recycled explicitly: they may still be on screen, and the garbage collector reclaims them.
 *
 * @param maxBytes capacity, in bytes of pixel data.
 */
class BitmapCache(maxBytes: Long = defaultMaxBytes(Runtime.getRuntime().maxMemory())) {
    private val cache =
        object : LruCache<RenderKey, Bitmap>(maxBytes.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()) {
            override fun sizeOf(key: RenderKey, value: Bitmap): Int = value.allocationByteCount
        }

    /** Returns the cached rendering for [key], if any. */
    operator fun get(key: RenderKey): Bitmap? = cache.get(key)

    /** Stores a rendering. */
    operator fun set(key: RenderKey, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    /** Bytes currently held. */
    val sizeBytes: Int get() = cache.size()

    /** Empties the cache, for example when memory runs low (`SPEC.md` §6). */
    fun clear() = cache.evictAll()

    /** Default capacity of the cache, from the size of the heap. */
    companion object {
        /** A quarter of the heap, at most [MAX_CACHE_BYTES]. */
        fun defaultMaxBytes(maxMemory: Long): Long = (maxMemory / HEAP_SHARE).coerceAtMost(MAX_CACHE_BYTES)
    }
}
