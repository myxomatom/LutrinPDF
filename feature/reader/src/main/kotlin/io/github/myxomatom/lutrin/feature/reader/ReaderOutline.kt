// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.compose.runtime.Immutable
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem

/** Where a tap on an outline entry leads (F06, ADR 0021). */
sealed interface OutlineTarget {
    /**
     * A place in the document.
     *
     * @property pageIndex zero-based page, within the document.
     * @property yOffset distance from the top of the page, in PDF points; 0 shows the top.
     */
    data class Page(val pageIndex: Int, val yOffset: Float) : OutlineTarget

    /**
     * An address outside the document, as the engine reads it, whatever its scheme: the reader
     * never opens it on its own, the link confirmation does (ADR 0020, F09).
     *
     * @property uri the address, unfiltered.
     */
    data class External(val uri: String) : OutlineTarget
}

/**
 * One line of the outline, in the flat list the drawer shows (ADR 0021).
 *
 * @property id position of the entry in the document order (pre-order walk of the tree): stable
 *   for a document, so it is the key of the list and of the expanded state.
 * @property title title as written in the document; may be blank.
 * @property level depth in the tree, 0 for the top entries.
 * @property parentId [id] of the entry that contains this one, `null` at the top.
 * @property hasChildren whether sub-sections follow, which the user can show or hide.
 * @property target where the entry leads, `null` for a title that leads nowhere.
 */
data class OutlineEntry(
    val id: Int,
    val title: String,
    val level: Int,
    val parentId: Int?,
    val hasChildren: Boolean,
    val target: OutlineTarget?,
) {
    /** Zero-based page the entry leads to; `null` for an external or an unreachable entry. */
    val pageIndex: Int? get() = (target as? OutlineTarget.Page)?.pageIndex
}

/**
 * The outline of the open document, as the reader shows it.
 *
 * @property entries every entry, in document order, whether its parents are expanded or not.
 * @property expanded [OutlineEntry.id] of the entries whose sub-sections are shown.
 * @property currentSection [OutlineEntry.id] of the section of the page being read, `null` when
 *   no entry leads to or before it.
 */
@Immutable
data class ReaderOutline(
    val entries: List<OutlineEntry> = emptyList(),
    val expanded: Set<Int> = emptySet(),
    val currentSection: Int? = null,
) {
    /** Whether the document has no outline: the reader then offers no table of contents. */
    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Title of the section that [page] belongs to, for the preview of the slider.
     *
     * @param page zero-based page.
     * @return the title, or `null` when the page is before the first section or the title is blank.
     */
    fun sectionTitleAt(page: Int): String? =
        entries.sectionIndexOf(page)?.let { entries[it].title.takeIf(String::isNotBlank) }

    /** The same outline with the section of [page] marked as the current one. */
    fun withCurrentSection(page: Int): ReaderOutline = copy(currentSection = entries.sectionIndexOf(page))

    /**
     * The same outline with the path down to the current section expanded, and the section itself
     * when it has sub-sections; other entries unchanged.
     */
    fun revealingCurrentSection(): ReaderOutline {
        val current = currentSection?.let(entries::getOrNull) ?: return this
        val own = if (current.hasChildren) listOf(current.id) else emptyList()
        return copy(expanded = expanded + own + entries.ancestorIds(current.id))
    }

    /** The same outline with [id] expanded if it was collapsed, collapsed if it was expanded. */
    fun toggling(id: Int): ReaderOutline {
        if (entries.getOrNull(id)?.hasChildren != true) return this
        return copy(expanded = if (id in expanded) expanded - id else expanded + id)
    }
}

/**
 * Flattens the tree of the engine into the list the drawer shows, in document order.
 *
 * The walk is iterative: an outline deep enough to overflow the stack of a recursive one is
 * still read. A destination beyond the last page is dropped, as the entry is shown but leads
 * nowhere; an entry without destination but with an address leads outside the document.
 *
 * @param items top entries of the outline.
 * @param pageCount number of pages of the document.
 */
internal fun flattenOutline(items: List<OutlineItem>, pageCount: Int): List<OutlineEntry> {
    val entries = ArrayList<OutlineEntry>()
    val frames = ArrayDeque<Frame>()
    frames.addLast(Frame(items.iterator(), parentId = null))
    while (frames.isNotEmpty()) {
        val frame = frames.last()
        if (!frame.items.hasNext()) {
            frames.removeLast()
            continue
        }
        val item = frame.items.next()
        val id = entries.size
        entries += OutlineEntry(
            id = id,
            title = item.title,
            level = frames.size - 1,
            parentId = frame.parentId,
            hasChildren = item.children.isNotEmpty(),
            target = item.target(pageCount),
        )
        if (item.children.isNotEmpty()) frames.addLast(Frame(item.children.iterator(), parentId = id))
    }
    return entries
}

/** A level of the tree being walked: the entries still to read and the entry that holds them. */
private class Frame(val items: Iterator<OutlineItem>, val parentId: Int?)

private fun OutlineItem.target(pageCount: Int): OutlineTarget? {
    val destination = destination?.takeIf { it.pageIndex < pageCount }
    return when {
        destination != null -> OutlineTarget.Page(destination.pageIndex, destination.y ?: 0f)
        !uri.isNullOrBlank() -> OutlineTarget.External(uri.orEmpty())
        else -> null
    }
}

/**
 * Index of the section [page] belongs to: the last entry, in document order, among those that
 * lead to the furthest page not after [page]. Going by the page, not by the order, keeps a
 * misordered outline (an appendix listed first) from taking over the pages before it.
 */
internal fun List<OutlineEntry>.sectionIndexOf(page: Int): Int? {
    var best: Int? = null
    var bestPage = -1
    for (entry in this) {
        val entryPage = entry.pageIndex ?: continue
        if (entryPage in bestPage..page) {
            best = entry.id
            bestPage = entryPage
        }
    }
    return best
}

/** [OutlineEntry.id] of every entry that contains [id], from its parent up to the top. */
internal fun List<OutlineEntry>.ancestorIds(id: Int): List<Int> {
    val ancestors = ArrayList<Int>()
    var parent = getOrNull(id)?.parentId
    while (parent != null) {
        ancestors += parent
        parent = getOrNull(parent)?.parentId
    }
    return ancestors
}

/**
 * The entries the drawer lists: those with no collapsed entry above them, in document order.
 *
 * @param expanded [OutlineEntry.id] of the entries whose sub-sections are shown.
 */
internal fun List<OutlineEntry>.visibleEntries(expanded: Set<Int>): List<OutlineEntry> {
    val visible = ArrayList<OutlineEntry>(size)
    var hiddenBelowLevel: Int? = null
    for (entry in this) {
        val hidden = hiddenBelowLevel
        if (hidden != null && entry.level > hidden) continue
        hiddenBelowLevel = if (entry.hasChildren && entry.id !in expanded) entry.level else null
        visible += entry
    }
    return visible
}
