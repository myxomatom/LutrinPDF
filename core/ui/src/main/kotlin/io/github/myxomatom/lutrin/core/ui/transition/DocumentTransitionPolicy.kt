// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.transition

/**
 * When the thumbnail of a document and its page in the reader form a shared element (ADR 0019).
 * Pure rules, tested without a device; the screens apply them through [DocumentTransitionLayout]
 * and [rememberDocumentTransition].
 *
 * Only the document the user tapped in the library is linked, and only while the system plays
 * animations. Every other way into the reader (file picker, « Ouvrir avec », share, restored
 * process) has no linked document: the screens then only fade, as before.
 */
object DocumentTransitionPolicy {
    /**
     * Whether the system plays animations. An animator duration scale of 0 is what both the
     * accessibility setting « Supprimer les animations » and the developer option set: no shared
     * element then, the screens change at once (Compose skips its animations at that scale).
     *
     * @param animatorDurationScale `Settings.Global.ANIMATOR_DURATION_SCALE`, 1 by default.
     */
    fun animationsEnabled(animatorDurationScale: Float): Boolean = animatorDurationScale > 0f

    /**
     * The document linked to the reader, given to [DocumentTransitionLayout].
     *
     * @param tappedDocumentId identifier of the document last opened by a tap on its thumbnail
     *   (card or row of the library), `null` when the last opening came another way.
     * @param animationsEnabled whether the system plays animations ([animationsEnabled]).
     * @return [tappedDocumentId], or `null` when animations are off.
     */
    fun linkedDocument(tappedDocumentId: String?, animationsEnabled: Boolean): String? =
        tappedDocumentId.takeIf { animationsEnabled }

    /**
     * Whether [documentId] takes part in the transition: it must be the linked document.
     *
     * @param linkedDocumentId the document linked by the app, or `null`.
     * @param documentId the document shown by a thumbnail or by the reader; `null` when that
     *   element never takes part (a protected document has no picture to grow).
     */
    fun isLinked(linkedDocumentId: String?, documentId: String?): Boolean =
        documentId != null && documentId == linkedDocumentId
}
