// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import io.github.myxomatom.lutrin.core.domain.pdf.PageRotation
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.RenderOptions
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.DocumentColors
import io.github.myxomatom.lutrin.core.domain.settings.FitMode
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.feature.reader.links.PageLinks
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.Dispatchers

/** State of the reader screen (E2), exposed by [ReaderViewModel]. */
sealed interface ReaderUiState {
    /** The document is being opened. */
    data object Loading : ReaderUiState

    /**
     * The document could not be opened (V8).
     *
     * @property reason what went wrong, mapped to a message by the screen.
     * @property inRecents whether the document is in the recent list: the screen then offers
     *   « Retirer des récents » (`SPEC.md` §6, ADR 0016).
     */
    data class Error(val reason: ReaderError, val inRecents: Boolean = false) : ReaderUiState

    /**
     * The document is encrypted and needs its user password (F10): the password dialog (V4)
     * asks for it. The password itself is never part of the state (ADR 0017): it stays in the
     * field of the dialog until it is tried, so that no state, log or `toString()` can hold it.
     *
     * @property documentName name of the document, shown in the question.
     * @property isIncorrect whether the last password tried did not open the document.
     * @property isChecking whether a password is being tried: the dialog ignores new attempts.
     */
    data class PasswordRequired(
        val documentName: String,
        val isIncorrect: Boolean = false,
        val isChecking: Boolean = false,
    ) : ReaderUiState

    /**
     * The document is open.
     *
     * @property title document title, or file name.
     * @property pageSizes size of every page, in points; never empty.
     * @property renderer draws the pages into bitmaps.
     * @property scrollMode continuous scroll or page by page (F03), from the settings.
     * @property pagePreview whether dragging the page slider previews the page aimed at and
     *   moves the document on release only (F05, ADR 0018), from the settings.
     * @property currentPage zero-based page last reported by the screen; where the reader
     *   starts again when the scroll mode changes.
     * @property links links of the pages (F09), read on demand; none by default.
     * @property outline table of contents (F06, ADR 0021): empty while it is read and for a
     *   document without one.
     * @property canCopyText whether selected text may be copied (F08, ADR 0024): the document allows
     *   it, or the user chose to ignore its restriction in the settings; followed live.
     * @property canPrint whether the document may be printed (F15, ADR 0026): the document allows
     *   it, or the user chose to ignore its restrictions in the settings; followed live.
     * @property documentColors the colours the user chose for the documents (F13, ADR 0028), from
     *   the settings and followed live; the screen resolves them with the theme it is drawn in
     *   ([renderOptions]).
     * @property chosenFitMode what the pages are fitted to as the user picked it (F04, ADR 0029),
     *   `null` until then: see [fitMode].
     * @property keepScreenOn whether the screen stays on while the reader is shown (ADR 0029), from the
     *   settings and followed live.
     * @property lockOrientation whether the reader keeps its orientation (F17, ADR 0029), from the
     *   settings and followed live.
     * @property rotation rotation of the display of this document (F27, ADR 0032), kept with its
     *   reading position; [PageRotation.NONE] until the user turns the pages.
     */
    data class Ready(
        val title: String,
        val pageSizes: List<PageSize>,
        val renderer: PageRenderer,
        val scrollMode: ScrollMode = AppSettings.DEFAULT_SCROLL_MODE,
        val currentPage: Int = 0,
        val pagePreview: Boolean = AppSettings.DEFAULT_PAGE_PREVIEW,
        val links: PageLinks = PageLinks(document = null, dispatcher = Dispatchers.Unconfined),
        val outline: ReaderOutline = ReaderOutline(),
        val canCopyText: Boolean = true,
        val canPrint: Boolean = true,
        val documentColors: DocumentColors = DocumentColors(),
        val chosenFitMode: FitMode? = null,
        val keepScreenOn: Boolean = AppSettings.DEFAULT_KEEP_SCREEN_ON,
        val lockOrientation: Boolean = AppSettings.DEFAULT_LOCK_ORIENTATION,
        val rotation: PageRotation = PageRotation.NONE,
    ) : ReaderUiState {
        /**
         * Sizes of the pages as they are shown, turned by [rotation] (F27, ADR 0032): what the views
         * lay out, fit and render. [pageSizes] stay those of the document, for everything that
         * speaks to it (text, links, search, printing).
         */
        val displayPageSizes: List<PageSize> by lazy { pageSizes.map(rotation::displaySize) }

        /**
         * What zoom 1 fits now (F04, ADR 0029): the choice of the user, or the one of the scroll
         * mode (the width scrolling, the whole page page by page) until there is one.
         */
        val fitMode: FitMode get() = chosenFitMode ?: FitMode.defaultFor(scrollMode)

        /**
         * How to draw the pages (ADR 0028): with the colours of [documentColors] in a theme that is
         * dark or not, turned by [rotation]. The theme is known to the screen only, which may force
         * it (E3).
         *
         * @param darkTheme whether the interface uses its dark colour scheme.
         */
        fun renderOptions(darkTheme: Boolean): RenderOptions =
            RenderOptions(documentColors.modeFor(darkTheme), rotation)
    }
}

/**
 * Why a document could not be opened (`SPEC.md` §6). An encrypted document is not an error: the
 * reader asks for its password ([ReaderUiState.PasswordRequired]).
 */
enum class ReaderError {
    /** Damaged PDF. */
    UNREADABLE,

    /** Not a PDF. */
    UNSUPPORTED_FORMAT,

    /** File missing or permission revoked. */
    NOT_ACCESSIBLE,
}
