// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.settings

/**
 * What the pages are fitted to when the zoom is "100 %" (F04, V6 « Ajustement », ADR 0029): the
 * size the reader starts from, comes back to with a double tap, and applies at once when the
 * choice changes.
 */
enum class FitMode {
    /** The page fills the width of the reading area; a tall page scrolls. */
    WIDTH,

    /** The whole page is visible without scrolling, whatever its shape. */
    PAGE,
    ;

    /** Companion helpers. */
    companion object {
        /**
         * The fit used while the user has chosen none: what the reader did before the choice
         * existed (ADR 0011), so that nothing changes for an existing installation.
         *
         * @param scrollMode how the pages are laid out.
         * @return [WIDTH] for the continuous scroll, [PAGE] page by page.
         */
        fun defaultFor(scrollMode: ScrollMode): FitMode = when (scrollMode) {
            ScrollMode.CONTINUOUS -> WIDTH
            ScrollMode.PAGED -> PAGE
        }
    }
}
