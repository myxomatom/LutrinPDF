// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.transition

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * Which document's thumbnail and reader page are linked (ADR 0019), as the user moves between
 * the library and the reader. Pure rules, tested without a device; `LutrinApp` keeps one value
 * and gives [linked] to [DocumentTransitionLayout].
 *
 * Going to the reader is a plain fade: a document tapped in the library is only *opening* while
 * the reader comes on screen, and becomes [linked] once the reader is settled (its navigation
 * entry resumed). Going back, the page shown then shrinks into the thumbnail. The grow of the
 * thumbnail into a reader still opening, tried first, looked broken on a device (amended ADR 0019).
 *
 * @property opening the document tapped in the library, while its reader comes on screen.
 * @property linked the document whose page shrinks back into its thumbnail, or `null`.
 */
@Immutable
data class DocumentLink(val opening: String? = null, val linked: String? = null) {
    /**
     * The user tapped [documentId] in the library: nothing is linked while the reader comes in,
     * so the screens only fade.
     */
    fun tapped(documentId: String): DocumentLink = DocumentLink(opening = documentId)

    /** The reader opens another way (file picker, other app): nothing is linked. */
    fun openedElsewhere(): DocumentLink = DocumentLink()

    /**
     * A screen is settled: its navigation is over. The reader of the [opening] document links it;
     * the library, reached again before that, forgets it (back at once, nothing to shrink).
     *
     * @param onLibrary whether the settled screen is the library.
     */
    fun settled(onLibrary: Boolean): DocumentLink = when {
        onLibrary -> copy(opening = null)
        opening != null -> DocumentLink(linked = opening)
        else -> this
    }

    /** Saving of a [DocumentLink] in the saved state. */
    companion object {
        /** Keeps a [DocumentLink] across a rotation or the death of the process. */
        val Saver: Saver<DocumentLink, Any> =
            listSaver(
                save = { listOf(it.opening, it.linked) },
                restore = { DocumentLink(opening = it[0], linked = it[1]) },
            )
    }
}
