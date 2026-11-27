// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

import io.github.myxomatom.lutrin.core.domain.settings.DocumentColorMode

/**
 * How the pages of a document are drawn, beyond which part and at which scale (ADR 0028): every
 * rendering of the reader (pages, sharp details, previews of the slider) carries them down to
 * the engine, and every cache of renderings keys on them, so that a change never shows a bitmap
 * drawn with other options.
 *
 * Display options only: printing, sharing and the thumbnails of the library always use
 * [ORIGINAL].
 *
 * @property colorMode colours of the pages (F13): original, night or sepia.
 * @property rotation rotation of the display (F27, ADR 0032): the engine draws the page turned, and
 *   the region it is asked for is in the display space of the turned page ([PageRotation]).
 */
data class RenderOptions(
    val colorMode: DocumentColorMode = DocumentColorMode.NORMAL,
    val rotation: PageRotation = PageRotation.NONE,
) {
    /** Shared values. */
    companion object {
        /** The document as it is: original colours, not turned. */
        val ORIGINAL: RenderOptions = RenderOptions()
    }
}
