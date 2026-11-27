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
 * @property ignoreDocumentRestrictions whether the reader lets the user copy the text (F08, ADR
 *   0024) and print (F15, ADR 0026) a document whose author forbids it. Off by default: the
 *   permissions of the document are respected.
 * @property appTheme light, dark or the system theme for the interface (F13, ADR 0028). The
 *   system theme by default.
 * @property documentColors the colours of the documents (F13, ADR 0028): original by default,
 *   in the dark theme too.
 */
data class AppSettings(
    val dynamicColor: Boolean = DEFAULT_DYNAMIC_COLOR,
    val scrollMode: ScrollMode = DEFAULT_SCROLL_MODE,
    val pagePreview: Boolean = DEFAULT_PAGE_PREVIEW,
    val ignoreDocumentRestrictions: Boolean = DEFAULT_IGNORE_DOCUMENT_RESTRICTIONS,
    val appTheme: AppTheme = DEFAULT_APP_THEME,
    val documentColors: DocumentColors = DocumentColors(),
) {
    /**
     * Whether the reader may copy the text of a document (F08, ADR 0024): always when the
     * document allows it, and despite its author's restriction only when the user chose to
     * ignore it ([ignoreDocumentRestrictions]).
     *
     * @param documentAllowsCopy the copy permission of the document (`PdfDocument.canCopyText`).
     */
    fun allowsTextCopy(documentAllowsCopy: Boolean): Boolean = documentAllowsCopy || ignoreDocumentRestrictions

    /**
     * Whether the reader may print a document (F15, ADR 0026): always when the document allows
     * it, and despite its author's restriction only when the user chose to ignore it
     * ([ignoreDocumentRestrictions]).
     *
     * @param documentAllowsPrinting the print permission of the document (`PdfDocument.canPrint`).
     */
    fun allowsPrinting(documentAllowsPrinting: Boolean): Boolean = documentAllowsPrinting || ignoreDocumentRestrictions

    /** Default values. */
    companion object {
        /** Dynamic colours are on until the user turns them off (ADR 0010). */
        const val DEFAULT_DYNAMIC_COLOR: Boolean = true

        /** Pages scroll continuously until the user picks "page by page" (F03, ADR 0011). */
        val DEFAULT_SCROLL_MODE: ScrollMode = ScrollMode.CONTINUOUS

        /** The page slider previews the page until the user turns it off (ADR 0018). */
        const val DEFAULT_PAGE_PREVIEW: Boolean = true

        /** The restrictions of a document are respected until the user chooses otherwise (ADR 0024, 0026). */
        const val DEFAULT_IGNORE_DOCUMENT_RESTRICTIONS: Boolean = false

        /** The interface follows the theme of the system until the user forces one (ADR 0028). */
        val DEFAULT_APP_THEME: AppTheme = AppTheme.SYSTEM
    }
}
