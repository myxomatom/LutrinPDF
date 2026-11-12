// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination

/**
 * One-off effect of a link of the document (F09, ADR 0022), sent by [ReaderViewModel] and
 * handled once by the screen.
 */
sealed interface LinkEffect {
    /**
     * Show [destination], an internal link. When the link leads to another page, the screen
     * then offers « Revenir à la page N » to go back to [returnPage].
     *
     * @property destination page and position to show.
     * @property returnPage zero-based page the user was on, or `null` when the link stays on it.
     */
    data class GoTo(val destination: PdfDestination, val returnPage: Int?) : LinkEffect

    /**
     * Hand [uri] to another app (`ACTION_VIEW`), after the user confirmed it in V7. The app
     * itself never opens it.
     *
     * @property uri the link, checked by `CheckExternalLinkUseCase`.
     */
    data class OpenExternalLink(val uri: String) : LinkEffect

    /** The external link has a scheme that is never opened (`javascript:`, `file:`…): say so briefly. */
    data object LinkRefused : LinkEffect
}
