// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import org.junit.Test

class ReaderOutlineTest {
    private fun item(
        title: String,
        page: Int? = null,
        y: Float? = null,
        uri: String? = null,
        kids: List<OutlineItem> = emptyList(),
    ) = OutlineItem(title, page?.let { PdfDestination(it, y = y) }, uri, kids)

    /** The outline of the mock-up V2, with a third level under « 2.2 ». */
    private val tree =
        listOf(
            item("1. Objet", page = 0),
            item(
                "2. Durée",
                page = 1,
                kids =
                    listOf(
                        item("2.1 Date", page = 1, y = 300f),
                        item("2.2 Renouvellement", page = 1, y = 500f, kids = listOf(item("2.2.1 Tacite", page = 2))),
                    ),
            ),
            item("3. Loyer", page = 3),
        )

    @Test
    fun `an empty outline flattens to nothing`() {
        assertThat(flattenOutline(emptyList(), pageCount = 8)).isEmpty()
        assertThat(ReaderOutline().isEmpty).isTrue()
    }

    @Test
    fun `flattens a three level tree in document order with levels and parents`() {
        val entries = flattenOutline(tree, pageCount = 8)

        assertThat(entries.map { it.title })
            .containsExactly("1. Objet", "2. Durée", "2.1 Date", "2.2 Renouvellement", "2.2.1 Tacite", "3. Loyer")
            .inOrder()
        assertThat(entries.map { it.level }).containsExactly(0, 0, 1, 1, 2, 0).inOrder()
        assertThat(entries.map { it.parentId }).containsExactly(null, null, 1, 1, 3, null).inOrder()
        assertThat(entries.map { it.hasChildren }).containsExactly(false, true, false, true, false, false).inOrder()
        assertThat(entries.map { it.id }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
    }

    @Test
    fun `keeps the position of a destination`() {
        val entries = flattenOutline(tree, pageCount = 8)

        assertThat(entries[2].target).isEqualTo(OutlineTarget.Page(1, 300f))
        assertThat(entries[0].target).isEqualTo(OutlineTarget.Page(0, 0f))
    }

    @Test
    fun `an entry without destination or address leads nowhere`() {
        val entries = flattenOutline(listOf(item("Titre seul")), pageCount = 2)

        assertThat(entries.single().target).isNull()
        assertThat(entries.single().pageIndex).isNull()
    }

    @Test
    fun `an entry with an address only leads outside the document`() {
        val entries = flattenOutline(listOf(item("Site", uri = "https://example.org/")), pageCount = 2)

        assertThat(entries.single().target).isEqualTo(OutlineTarget.External("https://example.org/"))
    }

    @Test
    fun `any scheme is kept as it is read, the link dialog decides`() {
        val entries = flattenOutline(listOf(item("Script", uri = "javascript:alert(1)")), pageCount = 2)

        assertThat(entries.single().target).isEqualTo(OutlineTarget.External("javascript:alert(1)"))
    }

    @Test
    fun `a blank address is no address`() {
        assertThat(flattenOutline(listOf(item("Vide", uri = "  ")), pageCount = 2).single().target).isNull()
    }

    @Test
    fun `a destination past the last page is dropped`() {
        val entries = flattenOutline(listOf(item("Trop loin", page = 5)), pageCount = 3)

        assertThat(entries.single().target).isNull()
    }

    @Test
    fun `a destination wins over an address`() {
        val entries = flattenOutline(listOf(item("Les deux", page = 1, uri = "https://example.org/")), pageCount = 3)

        assertThat(entries.single().target).isEqualTo(OutlineTarget.Page(1, 0f))
    }

    @Test
    fun `a very deep outline does not overflow the stack`() {
        var deepest = item("niveau 5000", page = 0)
        for (level in 4999 downTo 0) deepest = item("niveau $level", page = 0, kids = listOf(deepest))

        val entries = flattenOutline(listOf(deepest), pageCount = 1)

        assertThat(entries).hasSize(5001)
        assertThat(entries.last().level).isEqualTo(5000)
    }

    @Test
    fun `the section of a page is the last entry on or before it`() {
        val entries = flattenOutline(tree, pageCount = 8)

        assertThat(entries.sectionIndexOf(0)).isEqualTo(0)
        assertThat(entries.sectionIndexOf(1)).isEqualTo(3)
        assertThat(entries.sectionIndexOf(2)).isEqualTo(4)
        assertThat(entries.sectionIndexOf(7)).isEqualTo(5)
    }

    @Test
    fun `a page before the first entry has no section`() {
        val entries = flattenOutline(listOf(item("Chapitre", page = 2)), pageCount = 5)

        assertThat(entries.sectionIndexOf(1)).isNull()
        assertThat(ReaderOutline(entries).withCurrentSection(1).currentSection).isNull()
    }

    @Test
    fun `entries without a page are never a section`() {
        val entries = flattenOutline(listOf(item("Site", uri = "https://example.org/"), item("Seul")), pageCount = 5)

        assertThat(entries.sectionIndexOf(3)).isNull()
    }

    @Test
    fun `a misordered outline does not take the pages before it`() {
        // An appendix listed first but on the last page must not be the section of page 2.
        val entries = flattenOutline(listOf(item("Annexe", page = 7), item("Chapitre", page = 1)), pageCount = 8)

        assertThat(entries.sectionIndexOf(2)).isEqualTo(1)
    }

    @Test
    fun `the section title follows the page and skips a blank title`() {
        val outline = ReaderOutline(flattenOutline(listOf(item("Intro", page = 0), item(" ", page = 3)), pageCount = 5))

        assertThat(outline.sectionTitleAt(2)).isEqualTo("Intro")
        assertThat(outline.sectionTitleAt(4)).isNull()
        assertThat(ReaderOutline().sectionTitleAt(0)).isNull()
    }

    @Test
    fun `lists only the entries whose parents are expanded`() {
        val entries = flattenOutline(tree, pageCount = 8)

        assertThat(entries.visibleEntries(emptySet()).map { it.id }).containsExactly(0, 1, 5).inOrder()
        assertThat(entries.visibleEntries(setOf(1)).map { it.id }).containsExactly(0, 1, 2, 3, 5).inOrder()
        assertThat(entries.visibleEntries(setOf(1, 3)).map { it.id }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
    }

    @Test
    fun `an expanded entry under a collapsed one stays hidden`() {
        val entries = flattenOutline(tree, pageCount = 8)

        assertThat(entries.visibleEntries(setOf(3)).map { it.id }).containsExactly(0, 1, 5).inOrder()
    }

    @Test
    fun `finds the ancestors from the parent up`() {
        val entries = flattenOutline(tree, pageCount = 8)

        assertThat(entries.ancestorIds(4)).containsExactly(3, 1).inOrder()
        assertThat(entries.ancestorIds(0)).isEmpty()
        assertThat(entries.ancestorIds(99)).isEmpty()
    }

    @Test
    fun `revealing the current section expands its path and keeps what was expanded`() {
        val entries = flattenOutline(tree, pageCount = 8)
        val outline = ReaderOutline(entries, expanded = setOf(1)).withCurrentSection(2)

        assertThat(outline.revealingCurrentSection().expanded).containsExactly(1, 3)
        assertThat(ReaderOutline(entries).revealingCurrentSection().expanded).isEmpty()
    }

    @Test
    fun `revealing a current section with sub-sections expands it too`() {
        val entries = flattenOutline(tree, pageCount = 8)

        // On page 1 the section is « 2.2 », which holds « 2.2.1 ».
        val outline = ReaderOutline(entries).withCurrentSection(1)

        assertThat(outline.currentSection).isEqualTo(3)
        assertThat(outline.revealingCurrentSection().expanded).containsExactly(1, 3)
    }

    @Test
    fun `toggling expands then collapses an entry with sub-sections`() {
        val outline = ReaderOutline(flattenOutline(tree, pageCount = 8))

        val expanded = outline.toggling(1)
        assertThat(expanded.expanded).containsExactly(1)
        assertThat(expanded.toggling(1).expanded).isEmpty()
    }

    @Test
    fun `toggling a leaf or an unknown entry changes nothing`() {
        val outline = ReaderOutline(flattenOutline(tree, pageCount = 8))

        assertThat(outline.toggling(0)).isEqualTo(outline)
        assertThat(outline.toggling(99)).isEqualTo(outline)
    }
}
