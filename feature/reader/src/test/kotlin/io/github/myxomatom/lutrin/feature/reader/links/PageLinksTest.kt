// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.links

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PageLinksTest {
    private val dispatcher = StandardTestDispatcher()
    private val link = PdfLink.Internal(PdfRect(0f, 0f, 10f, 10f), PdfDestination(1))
    private val document = FakePdfDocument().apply { links = mapOf(0 to listOf(link)) }

    @Test
    fun `reads the links of a page once, then from the cache`() = runTest(dispatcher) {
        val links = PageLinks(document, dispatcher)

        assertThat(links.linksOf(0)).containsExactly(link)
        document.links = emptyMap()

        assertThat(links.linksOf(0)).containsExactly(link)
    }

    @Test
    fun `keeps the most recently used pages only`() = runTest(dispatcher) {
        val links = PageLinks(document, dispatcher, capacity = 2)

        links.linksOf(0)
        links.linksOf(1)
        links.linksOf(0)
        links.linksOf(2)

        assertThat(links.cached(0)).isNotNull()
        assertThat(links.cached(1)).isNull()
        assertThat(links.cached(2)).isNotNull()
    }

    @Test
    fun `a page outside the document has no link and is not cached`() = runTest(dispatcher) {
        val links = PageLinks(document, dispatcher)

        assertThat(links.linksOf(9)).isEmpty()
        assertThat(links.cached(9)).isNull()
    }

    @Test
    fun `a closed document has no link`() = runTest(dispatcher) {
        val links = PageLinks(document, dispatcher)
        document.close()

        assertThat(links.linksOf(0)).isEmpty()
    }

    @Test
    fun `a reader without document has no link`() = runTest(dispatcher) {
        assertThat(PageLinks(null, dispatcher).linksOf(0)).isEmpty()
    }
}
