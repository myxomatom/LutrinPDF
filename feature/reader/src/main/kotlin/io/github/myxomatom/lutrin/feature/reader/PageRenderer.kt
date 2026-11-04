// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Draws whole pages into bitmaps. Until tiled rendering lands (milestone M2, `ROADMAP.md`),
 * the bitmap size is capped by [MAX_PIXELS]: beyond it a zoomed page is upscaled, hence blurry.
 */
internal class PageRenderer {
    /**
     * Renders a page.
     *
     * @param document the open document.
     * @param index page index.
     * @param pageSize size of the page, in points.
     * @param widthPx wanted width in pixels.
     * @return a new bitmap holding the page.
     * @throws PdfError when the engine fails.
     */
    suspend fun render(document: PdfDocument, index: Int, pageSize: PageSize, widthPx: Int): Bitmap {
        val (width, height) = renderSize(pageSize, widthPx)
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val region = PdfRect(0f, 0f, pageSize.width, pageSize.height)
            document.renderPage(index, bitmap, region, scale = width / pageSize.width, nightMode = false)
        } catch (error: CancellationException) {
            bitmap.recycle()
            throw error
        } catch (error: PdfError) {
            bitmap.recycle()
            throw error
        }
        return bitmap
    }

    /** Pixel budget values. */
    companion object {
        /** Largest bitmap drawn for one page (16 megapixels, 64 MB in ARGB_8888). */
        const val MAX_PIXELS: Int = 16_000_000

        /**
         * Size of the bitmap drawn for a page shown [widthPx] wide, keeping its aspect ratio
         * and at most [MAX_PIXELS] pixels.
         *
         * @return width and height in pixels, each at least 1.
         */
        fun renderSize(pageSize: PageSize, widthPx: Int): Pair<Int, Int> {
            val aspect = pageSize.height / pageSize.width
            var width = max(widthPx, 1).toFloat()
            if (width * width * aspect > MAX_PIXELS) width = sqrt(MAX_PIXELS / aspect)
            val height = (width * aspect).roundToInt()
            return max(width.roundToInt(), 1) to max(height, 1)
        }
    }
}
