// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.settings

/**
 * User preferences of the app (`SPEC.md` §5), stored on the device only. Fields are added
 * with their settings (theme, document rendering, scrolling…) as the milestones land.
 *
 * @property dynamicColor whether the colours follow the wallpaper (Material You, Android 12+)
 *   instead of the Lutrin palette (F13, ADR 0010). On by default.
 * @property scrollMode how the reader lays out the pages (F03, ADR 0011): the same for every
 *   document. Continuous by default.
 */
data class AppSettings(
    val dynamicColor: Boolean = DEFAULT_DYNAMIC_COLOR,
    val scrollMode: ScrollMode = DEFAULT_SCROLL_MODE,
) {
    /** Default values. */
    companion object {
        /** Dynamic colours are on until the user turns them off (ADR 0010). */
        const val DEFAULT_DYNAMIC_COLOR: Boolean = true

        /** Pages scroll continuously until the user picks "page by page" (F03, ADR 0011). */
        val DEFAULT_SCROLL_MODE: ScrollMode = ScrollMode.CONTINUOUS
    }
}
