// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.DrawDevice
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Pixmap
import io.github.myxomatom.lutrin.core.domain.pdf.AffineTransform
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.RenderOptions
import io.github.myxomatom.lutrin.core.domain.settings.DocumentColorMode

/** White opaque value for every byte of a BGRA pixmap. */
private const val OPAQUE_WHITE = 0xFF

/** The red, green and blue bytes of an ARGB colour, as `Pixmap.tint` expects them. */
private const val RGB_MASK = 0xFFFFFF

/**
 * What to render: the pixel ([0], [0]) shows the point ([region] left, top) of the page, one
 * point covers [scale] pixels, and the result is [width] × [height] pixels, drawn with [options]
 * (ADR 0028). With a rotation ([RenderOptions.rotation], F27), [region] is in the display space of
 * the turned page (ADR 0032).
 */
internal data class RenderSpec(
    val region: PdfRect,
    val scale: Float,
    val width: Int,
    val height: Int,
    val options: RenderOptions = RenderOptions.ORIGINAL,
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
            val transform = with(spec.transform(bounds.let { PageSize(it.x1 - it.x0, it.y1 - it.y0) })) {
                Matrix(a, b, c, d, e, f)
            }
            run(device, transform, cookie)
            device.close()
        } finally {
            device.destroy()
        }
        pixmap.applyColors(spec.options.colorMode)
        return pixmap.pixels
    } finally {
        pixmap.destroy()
    }
}

/**
 * The matrix that draws the page of size [page] for this spec: the rotation of the display first
 * (from page space to display space, [io.github.myxomatom.lutrin.core.domain.pdf.PageRotation]),
 * then the shift to [RenderSpec.region] and the scale. MuPDF draws the page through it in one
 * pass, so a turned page costs no more than a straight one (ADR 0032).
 */
internal fun RenderSpec.transform(page: PageSize): AffineTransform {
    val turn = options.rotation.transform(page)
    return AffineTransform(
        a = turn.a * scale,
        b = turn.b * scale,
        c = turn.c * scale,
        d = turn.d * scale,
        e = (turn.e - region.left) * scale,
        f = (turn.f - region.top) * scale,
    )
}

/**
 * Applies the colours of [mode] to the drawn page, in place (F13, ADR 0028): two passes over the
 * pixels at most, never a second drawing of the page.
 * - Night inverts the lightness and keeps the hues (images stay recognisable, without turning to
 *   negatives), then maps black onto the night paper and white onto the night ink.
 * - Sepia maps black onto the sepia ink and white onto the sepia paper, each channel linearly: a
 *   plain colour filter.
 */
private fun Pixmap.applyColors(mode: DocumentColorMode) {
    when (mode) {
        DocumentColorMode.NORMAL -> Unit

        DocumentColorMode.NIGHT -> {
            invertLuminance()
            tint(mode.paper and RGB_MASK, mode.ink and RGB_MASK)
        }

        DocumentColorMode.SEPIA -> tint(mode.ink and RGB_MASK, mode.paper and RGB_MASK)
    }
}
