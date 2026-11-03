// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.DrawDevice
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Pixmap
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect

/** White opaque value for every byte of a BGRA pixmap. */
private const val OPAQUE_WHITE = 0xFF

/**
 * What to render: the pixel ([0], [0]) shows the point ([region] left, top) of the page, one
 * point covers [scale] pixels, and the result is [width] × [height] pixels.
 */
internal data class RenderSpec(
    val region: PdfRect,
    val scale: Float,
    val width: Int,
    val height: Int,
    val nightMode: Boolean,
) {
    init {
        require(width > 0 && height > 0 && scale > 0f) { "Invalid render size ${width}x$height at scale $scale" }
    }
}

/**
 * Draws this page as ARGB pixels, row by row. Must run on the thread of the page's document.
 *
 * @param cookie lets another thread abort the drawing.
 */
internal fun Page.drawPixels(spec: RenderSpec, cookie: Cookie): IntArray {
    // BGRA bytes read as little-endian ints are exactly Android's ARGB_8888 colour ints.
    val pixmap = Pixmap(ColorSpace.DeviceBGR, 0, 0, spec.width, spec.height, true)
    try {
        pixmap.clear(OPAQUE_WHITE)
        val device = DrawDevice(pixmap)
        try {
            val transform = Matrix(
                spec.scale,
                0f,
                0f,
                spec.scale,
                -spec.region.left * spec.scale,
                -spec.region.top * spec.scale,
            )
            run(device, transform, cookie)
            device.close()
        } finally {
            device.destroy()
        }
        if (spec.nightMode) pixmap.invertLuminance()
        return pixmap.pixels
    } finally {
        pixmap.destroy()
    }
}
