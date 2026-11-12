// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.links

import io.github.myxomatom.lutrin.core.domain.links.CheckExternalLinkUseCase
import io.github.myxomatom.lutrin.core.domain.links.ExternalLinkCheck
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.feature.reader.LinkEffect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * What the reader does with the links of the document (F09, ADR 0022), owned by the
 * `ReaderViewModel`: an internal link is sent to the screen as [LinkEffect.GoTo]; an external one
 * is checked by [checkExternalLink], then either waits for the user's confirmation in V7
 * ([externalLink]) or is refused ([LinkEffect.LinkRefused]). Once confirmed, the screen hands it
 * to another app ([LinkEffect.OpenExternalLink]): no `Context` here.
 *
 * @param checkExternalLink decides which external links may be offered to another app.
 * @param pageCount number of pages of the open document, or `null` before it is open.
 */
class LinkController(private val checkExternalLink: CheckExternalLinkUseCase, private val pageCount: () -> Int?) {
    private val effectChannel = Channel<LinkEffect>(Channel.BUFFERED)
    private val pendingExternalLink = MutableStateFlow<String?>(null)

    /** Effects of the links, each handled once by the screen: go to a destination, open a link, refusal. */
    val effects: Flow<LinkEffect> = effectChannel.receiveAsFlow()

    /**
     * External link waiting for the user's confirmation in V7, checked already; `null` when the
     * dialog is closed. Kept as long as the ViewModel, so across a rotation.
     */
    val externalLink: StateFlow<String?> = pendingExternalLink.asStateFlow()

    /**
     * Follows a link the user activated, by a tap or a TalkBack action. An internal link whose
     * page is outside the document, or activated before the document is open, is ignored.
     *
     * @param link the link.
     * @param fromPage zero-based page shown when the link was activated: where « Revenir »
     *   goes back to.
     */
    fun onLinkClick(link: PdfLink, fromPage: Int) {
        when (link) {
            is PdfLink.Internal -> goTo(link.destination, fromPage)
            is PdfLink.External -> onExternalLink(link.uri)
        }
    }

    /**
     * Asks to open the external link [uri], from a link or an entry of the outline: shows V7 if
     * its scheme is accepted, otherwise refuses it ([LinkEffect.LinkRefused]). Ignored while V7
     * already asks about another link.
     *
     * @param uri the link as written in the document.
     */
    fun onExternalLink(uri: String) {
        if (pendingExternalLink.value != null) return
        when (val check = checkExternalLink(uri)) {
            is ExternalLinkCheck.Allowed -> pendingExternalLink.value = check.uri
            ExternalLinkCheck.Refused -> effectChannel.trySend(LinkEffect.LinkRefused)
        }
    }

    /** « Ouvrir » in V7: closes it and asks the screen to hand the link to another app. */
    fun onExternalLinkConfirm() {
        val uri = pendingExternalLink.value ?: return
        pendingExternalLink.value = null
        effectChannel.trySend(LinkEffect.OpenExternalLink(uri))
    }

    /** « Annuler », « Copier le lien », back or a tap outside V7: closes it without opening the link. */
    fun onExternalLinkDismiss() {
        pendingExternalLink.value = null
    }

    private fun goTo(destination: PdfDestination, fromPage: Int) {
        val count = pageCount() ?: return
        if (destination.pageIndex >= count) return
        val returnPage = fromPage.coerceIn(0, count - 1).takeIf { it != destination.pageIndex }
        effectChannel.trySend(LinkEffect.GoTo(destination, returnPage))
    }
}
