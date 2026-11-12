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
 * @property pagePreview whether dragging the page slider of the reader previews the page aimed
 *   at and moves the document only when the slider is released (F05, ADR 0018); otherwise the
 *   document follows the slider as it moves. On by default.
 * @property ignoreCopyRestriction whether the reader lets the user copy the text of a document
 *   whose author forbids it (F08, ADR 0024). Off by default: the permission of the document is
 *   respected.
 */
data class AppSettings(
    val dynamicColor: Boolean = DEFAULT_DYNAMIC_COLOR,
    val scrollMode: ScrollMode = DEFAULT_SCROLL_MODE,
    val pagePreview: Boolean = DEFAULT_PAGE_PREVIEW,
    val ignoreCopyRestriction: Boolean = DEFAULT_IGNORE_COPY_RESTRICTION,
) {
    /**
     * Whether the reader may copy the text of a document (F08, ADR 0024): always when the
     * document allows it, and despite its author's restriction only when the user chose to
     * ignore it ([ignoreCopyRestriction]).
     *
     * @param documentAllowsCopy the copy permission of the document (`PdfDocument.canCopyText`).
     */
    fun allowsTextCopy(documentAllowsCopy: Boolean): Boolean = documentAllowsCopy || ignoreCopyRestriction

    /** Default values. */
    companion object {
        /** Dynamic colours are on until the user turns them off (ADR 0010). */
        const val DEFAULT_DYNAMIC_COLOR: Boolean = true

        /** Pages scroll continuously until the user picks "page by page" (F03, ADR 0011). */
        val DEFAULT_SCROLL_MODE: ScrollMode = ScrollMode.CONTINUOUS

        /** The page slider previews the page until the user turns it off (ADR 0018). */
        const val DEFAULT_PAGE_PREVIEW: Boolean = true

        /** The copy restriction of a document is respected until the user chooses otherwise (ADR 0024). */
        const val DEFAULT_IGNORE_COPY_RESTRICTION: Boolean = false
    }
}
