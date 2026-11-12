// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchHit
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
import io.github.myxomatom.lutrin.core.domain.pdf.SearchPageResult
import io.github.myxomatom.lutrin.core.domain.pdf.SelectionMode
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakePdfDocumentTest {
    private val rect = PdfRect(10f, 10f, 50f, 22f)
    private val area = PdfQuad.of(rect)
    private val origin = PdfPoint(0f, 0f)

    @Test
    fun `is empty and allows copying by default`() = runTest {
        val document = FakePdfDocument()

        assertThat(document.outline()).isEmpty()
        assertThat(document.links(0)).isEmpty()
        assertThat(document.selectText(0, origin, origin).isEmpty).isTrue()
        assertThat(document.isOnText(0, origin)).isFalse()
        assertThat(document.search("lutrin").toList().flatMap { it.hits }).isEmpty()
        assertThat(document.canCopyText).isTrue()
    }

    @Test
    fun `returns the outline, links, text areas and selection set by the test`() = runTest {
        val outline = listOf(OutlineItem("Chapitre", PdfDestination(2, y = 100f)))
        val link = PdfLink.Internal(rect, PdfDestination(1))
        val selection = TextSelection(1, listOf(area), "mot")
        val document = FakePdfDocument().apply {
            this.outline = outline
            links = mapOf(0 to listOf(link))
            selections = mapOf(1 to selection)
            textAreas = mapOf(1 to listOf(rect))
            canCopyText = false
        }

        assertThat(document.outline()).isEqualTo(outline)
        assertThat(document.links(0)).containsExactly(link)
        assertThat(document.links(1)).isEmpty()
        assertThat(document.selectText(1, origin, origin, SelectionMode.Words)).isEqualTo(selection)
        assertThat(document.selectionRequests.single())
            .isEqualTo(FakePdfDocument.SelectionRequest(1, origin, origin, SelectionMode.Words))
        assertThat(document.isOnText(1, PdfPoint(20f, 15f))).isTrue()
        assertThat(document.isOnText(1, origin)).isFalse()
        assertThat(document.canCopyText).isFalse()
    }

    @Test
    fun `reports every page of a search, in order, with its hits`() = runTest {
        val hits = listOf(SearchHit(2, listOf(area)), SearchHit(0, listOf(area)))
        val document = FakePdfDocument().apply {
            searchHits = { query -> if (query == "lutrin") hits else emptyList() }
        }

        val pages = document.search("lutrin").toList()

        assertThat(pages.map { it.pageIndex }).containsExactly(0, 1, 2).inOrder()
        assertThat(pages.map { it.hits.size }).containsExactly(1, 0, 1).inOrder()
        assertThat(document.searches.single()).isEqualTo(FakePdfDocument.Search("lutrin", SearchOptions()))
        assertThat(document.search("autre").toList().flatMap { it.hits }).isEmpty()
    }

    @Test
    fun `starts the search at the given page and wraps around`() = runTest {
        val document = FakePdfDocument(List(4) { A4 })

        val pages = document.search("lutrin", startPage = 2).toList()

        assertThat(pages.map { it.pageIndex }).containsExactly(2, 3, 0, 1).inOrder()
        assertThat(document.searches.single().startPage).isEqualTo(2)
    }

    @Test
    fun `stops the search at the maximum number of hits`() = runTest {
        val document = FakePdfDocument().apply { searchHits = { List(3) { SearchHit(it, listOf(area)) } } }

        val pages = document.search("lutrin", SearchOptions(maxHits = 2)).toList()

        assertThat(pages.map { it.pageIndex }).containsExactly(0, 1).inOrder()
        assertThat(pages.last().isLimitReached).isTrue()
    }

    @OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy and runCurrent of the test scheduler.
    @Test
    fun `a search delay shows the pages one after the other and lets the search be cancelled`() = runTest {
        val document = FakePdfDocument().apply { searchPageDelay = 10.milliseconds }
        val received = mutableListOf<SearchPageResult>()

        val collection = launch { document.search("lutrin").collect { received += it } }
        advanceTimeBy(15)
        runCurrent()
        val afterFirstPage = received.map { it.pageIndex }
        collection.cancel()
        advanceTimeBy(100)

        assertThat(afterFirstPage).containsExactly(0)
        assertThat(received.map { it.pageIndex }).containsExactly(0)
    }

    @Test
    fun `a blank query emits nothing`() = runTest {
        assertThat(FakePdfDocument().search("  ").toList()).isEmpty()
    }

    @Test
    fun `rejects a page out of range and calls after close`() = runTest {
        val document = FakePdfDocument()

        assertThat(runCatching { document.links(3) }.exceptionOrNull())
            .isInstanceOf(PdfError.PageOutOfRange::class.java)
        assertThat(runCatching { document.pageSize(3) }.exceptionOrNull())
            .isInstanceOf(PdfError.PageOutOfRange::class.java)
        assertThat(runCatching { document.selectText(-1, origin, origin) }.exceptionOrNull())
            .isInstanceOf(PdfError.PageOutOfRange::class.java)
        assertThat(runCatching { document.search("x", startPage = 3).toList() }.exceptionOrNull())
            .isInstanceOf(PdfError.PageOutOfRange::class.java)
        document.close()
        assertThat(runCatching { document.outline() }.exceptionOrNull()).isInstanceOf(PdfError.Closed::class.java)
        assertThat(runCatching { document.pageSize(0) }.exceptionOrNull()).isInstanceOf(PdfError.Closed::class.java)
        assertThat(runCatching { document.isOnText(0, origin) }.exceptionOrNull())
            .isInstanceOf(PdfError.Closed::class.java)
        // Like the real engine, even a blank query reports the closed document.
        assertThat(runCatching { document.search(" ").toList() }.exceptionOrNull())
            .isInstanceOf(PdfError.Closed::class.java)
    }
}
