// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.render

import android.graphics.Bitmap

/**
 * Outcome of a rendering by [PageRenderer] (ADR 0031): the screen draws the bitmap, explains a
 * page that cannot be read, or shows nothing and lets a later rendering try again.
 */
sealed interface RenderResult {
    /**
     * The page, or its part, is drawn.
     *
     * @property bitmap the rendering; owned by the renderer's cache for a base rendering or a preview.
     */
    data class Rendered(val bitmap: Bitmap) : RenderResult

    /**
     * The engine cannot read the page (for example a page missing from the page tree of a damaged
     * file): trying again gives the same answer, the screen shows the page as damaged.
     */
    data object Unreadable : RenderResult

    /**
     * Memory ran out for the bitmap: the caches are emptied and nothing is shown; a later
     * rendering of the page, after a scroll or a zoom, may succeed (`SPEC.md` §6).
     */
    data object OutOfMemory : RenderResult
}

/** The bitmap of a [RenderResult.Rendered], `null` for any other outcome. */
val RenderResult.bitmapOrNull: Bitmap?
    get() = (this as? RenderResult.Rendered)?.bitmap
