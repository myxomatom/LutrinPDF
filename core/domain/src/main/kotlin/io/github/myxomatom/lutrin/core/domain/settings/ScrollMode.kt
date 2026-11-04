// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.settings

/** How the reader lays out the pages of a document (F03, ADR 0011). */
enum class ScrollMode {
    /** Pages one below the other, in a continuous vertical scroll. The default. */
    CONTINUOUS,

    /** One page per screen, the next or previous one reached with a horizontal swipe. */
    PAGED,
}
