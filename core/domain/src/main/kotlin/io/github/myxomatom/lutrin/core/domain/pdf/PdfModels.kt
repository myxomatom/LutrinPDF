// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

import java.time.Instant

/**
 * Document properties shown in the properties sheet (F16). Text fields are `null` when the
 * document does not provide them.
 *
 * @property pageCount number of pages.
 * @property title document title.
 * @property author author of the content.
 * @property subject subject of the document.
 * @property keywords keywords, as written in the document.
 * @property creator application that created the original content.
 * @property producer application that produced the PDF.
 * @property creationDate when the document was created.
 * @property modificationDate when the document was last modified.
 * @property format format and version reported by the engine, e.g. `PDF 1.7`.
 * @property isEncrypted whether the document is protected by encryption.
 */
data class PdfMetadata(
    val pageCount: Int,
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val keywords: String? = null,
    val creator: String? = null,
    val producer: String? = null,
    val creationDate: Instant? = null,
    val modificationDate: Instant? = null,
    val format: String? = null,
    val isEncrypted: Boolean = false,
)

/**
 * Where a link or an outline entry leads inside the document (F06, F09).
 *
 * @property pageIndex zero-based target page.
 * @property x left edge of the target area, in page coordinates; `null` when the destination
 *   does not set it (keep the current horizontal position).
 * @property y top edge of the target area, in page coordinates; `null` when the destination
 *   does not set it (show the top of the page).
 * @throws IllegalArgumentException if [pageIndex] is negative.
 */
data class PdfDestination(val pageIndex: Int, val x: Float? = null, val y: Float? = null) {
    init {
        require(pageIndex >= 0) { "Negative page index $pageIndex" }
    }
}

/**
 * Entry of the document outline (table of contents, F06).
 *
 * @property title label shown to the user.
 * @property destination where the entry leads in the document, or `null` when it points
 *   nowhere in it (external link or broken destination).
 * @property uri external target, if any, exactly as written in the document: never opened
 *   without confirmation (F09), and filtered by the reader (ADR 0020).
 * @property children nested entries, in document order.
 */
data class OutlineItem(
    val title: String,
    val destination: PdfDestination?,
    val uri: String? = null,
    val children: List<OutlineItem> = emptyList(),
)

/** Hyperlink area on a page (F09). */
sealed interface PdfLink {
    /** Clickable area, in page coordinates. */
    val bounds: PdfRect

    /**
     * Link to a position in the same document.
     *
     * @property destination where the link leads.
     */
    data class Internal(override val bounds: PdfRect, val destination: PdfDestination) : PdfLink

    /**
     * Link to an external resource, opened by another app after confirmation.
     *
     * @property uri target of the link, as the engine reads it, whatever its scheme (`https:`,
     *   `mailto:`, `javascript:`, `file:`…): the reader decides which ones it opens (ADR 0020).
     */
    data class External(override val bounds: PdfRect, val uri: String) : PdfLink
}
