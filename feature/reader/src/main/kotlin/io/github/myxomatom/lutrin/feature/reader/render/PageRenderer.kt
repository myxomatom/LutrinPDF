// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.render

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Capacity of the cache of page previews: a dozen previews of 128 dp at 2.75×, five at 4×. */
private const val PREVIEW_CACHE_BYTES = 8L * 1024 * 1024

/**
 * Turns pages of an open document into bitmaps for the reader (F02): a cached "base" rendering
 * of the whole page, uncached "detail" renderings of the visible part when zoomed in, and small
 * previews of the page aimed at with the slider (F05, ADR 0018), kept in their own cache so that
 * they never push the pages on screen out of theirs. Renderings are cancelled with the calling
 * coroutine, e.g. when a page leaves the screen or the slider aims at another page.
 *
 * @param dispatcher where bitmaps are allocated (CPU-bound work, off the main thread).
 * @param cache base renderings of the pages.
 * @param previewCache previews of the slider, bounded to [PREVIEW_CACHE_BYTES] by default.
 */
class PageRenderer(
    private val document: PdfDocument,
    private val pageSizes: List<PageSize>,
    private val dispatcher: CoroutineDispatcher,
    private val cache: BitmapCache = BitmapCache(),
    private val previewCache: BitmapCache = BitmapCache(PREVIEW_CACHE_BYTES),
) {
    /**
     * Renders the whole page at [widthPx] pixels wide, or returns it from the cache.
     *
     * @return the bitmap, or `null` if memory ran out (the cache is then emptied, `SPEC.md` §6).
     */
    suspend fun renderPage(pageIndex: Int, widthPx: Int, nightMode: Boolean): Bitmap? {
        val key = RenderKey(pageIndex, widthPx, nightMode)
        cache[key]?.let { return it }
        val page = pageSizes[pageIndex]
        val bitmap =
            render(pageIndex, PdfRect(0f, 0f, page.width, page.height), widthPx, pageHeightPx(page, widthPx), nightMode)
        bitmap?.let { cache[key] = it }
        return bitmap
    }

    /**
     * Renders the whole page as a small preview [widthPx] pixels wide (the card above the slider,
     * ADR 0018), or returns it from the preview cache. Cancelling the caller aborts the drawing.
     *
     * @param pageIndex zero-based page.
     * @param widthPx width of the preview, in pixels; its height follows the page.
     * @param nightMode whether colours are inverted, like the pages.
     * @return the bitmap, or `null` if memory ran out (both caches are then emptied).
     */
    suspend fun renderPreview(pageIndex: Int, widthPx: Int, nightMode: Boolean = false): Bitmap? {
        val key = RenderKey(pageIndex, widthPx, nightMode)
        previewCache[key]?.let { return it }
        val page = pageSizes[pageIndex]
        val bitmap =
            render(pageIndex, PdfRect(0f, 0f, page.width, page.height), widthPx, pageHeightPx(page, widthPx), nightMode)
        bitmap?.let { previewCache[key] = it }
        return bitmap
    }

    /**
     * The preview of [pageIndex] at [widthPx] pixels if it is already rendered, without rendering
     * it: a lookup cheap enough for the composition, so that a cached page shows at once.
     */
    fun cachedPreview(pageIndex: Int, widthPx: Int, nightMode: Boolean = false): Bitmap? =
        previewCache[RenderKey(pageIndex, widthPx, nightMode)]

    /**
     * Renders only [part] of a page displayed [pageWidthPx] wide, at full sharpness.
     *
     * @return the bitmap of [part] (its size), or `null` if memory ran out.
     */
    suspend fun renderPart(pageIndex: Int, pageWidthPx: Int, part: PixelRect, nightMode: Boolean): Bitmap? {
        val page = pageSizes[pageIndex]
        val region = part.toPageRect(page, pageWidthPx)
        return render(pageIndex, region, part.width, part.height, nightMode, scale = pageWidthPx / page.width)
    }

    @Suppress("LongParameterList") // Internal helper mirroring PdfDocument.renderPage.
    private suspend fun render(
        pageIndex: Int,
        region: PdfRect,
        width: Int,
        height: Int,
        nightMode: Boolean,
        scale: Float = width / region.width,
    ): Bitmap? {
        val bitmap =
            withContext(dispatcher) {
                allocateOrNull(width, height)
            } ?: return null
        document.renderPage(pageIndex, bitmap, region, scale, nightMode)
        return bitmap
    }

    // Out of memory is an expected outcome on large pages: free the cache, skip this rendering
    // and let the next one try again, instead of crashing (SPEC.md §6).
    @Suppress("SwallowedException")
    private fun allocateOrNull(width: Int, height: Int): Bitmap? = try {
        createBitmap(width, height)
    } catch (error: OutOfMemoryError) {
        cache.clear()
        previewCache.clear()
        null
    }
}
