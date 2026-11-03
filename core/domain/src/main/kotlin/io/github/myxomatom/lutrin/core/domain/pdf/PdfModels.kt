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
 * Entry of the document outline (table of contents, F06).
 *
 * @property title label shown to the user.
 * @property pageIndex zero-based target page, or `null` when the entry points nowhere in the
 *   document (external link or broken destination).
 * @property uri external target, if any (never opened without confirmation, F09).
 * @property children nested entries, in document order.
 */
data class OutlineItem(
    val title: String,
    val pageIndex: Int?,
    val uri: String? = null,
    val children: List<OutlineItem> = emptyList(),
)

/** Hyperlink area on a page (F09). */
sealed interface PdfLink {
    /** Clickable area, in page coordinates. */
    val bounds: PdfRect

    /**
     * Link to a page of the same document.
     *
     * @property pageIndex zero-based target page.
     */
    data class Internal(override val bounds: PdfRect, val pageIndex: Int) : PdfLink

    /**
     * Link to an external resource, opened by another app after confirmation.
     *
     * @property uri target of the link, as written in the document.
     */
    data class External(override val bounds: PdfRect, val uri: String) : PdfLink
}

/**
 * Options of a text search (F07).
 *
 * @property matchCase when `false`, "Lutrin" also matches "lutrin".
 * @property matchAccents when `false`, "ete" also matches "été".
 */
data class SearchOptions(val matchCase: Boolean = false, val matchAccents: Boolean = false)

/**
 * One occurrence of the searched text.
 *
 * @property pageIndex zero-based page of the occurrence.
 * @property areas highlighted areas, in page coordinates; several when the match spans lines.
 */
data class SearchHit(val pageIndex: Int, val areas: List<PdfRect>)
