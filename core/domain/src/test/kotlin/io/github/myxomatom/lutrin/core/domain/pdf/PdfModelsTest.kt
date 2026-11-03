// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PdfModelsTest {
    @Test
    fun `search is insensitive to case and accents by default`() {
        val options = SearchOptions()

        assertThat(options.matchCase).isFalse()
        assertThat(options.matchAccents).isFalse()
    }

    @Test
    fun `links expose their bounds`() {
        val bounds = PdfRect(0f, 0f, 10f, 10f)
        val links: List<PdfLink> = listOf(PdfLink.Internal(bounds, 2), PdfLink.External(bounds, "https://example.org/"))

        assertThat(links.map { it.bounds }).containsExactly(bounds, bounds)
    }

    @Test
    fun `metadata defaults to unknown fields`() {
        val metadata = PdfMetadata(pageCount = 3)

        assertThat(metadata.title).isNull()
        assertThat(metadata.isEncrypted).isFalse()
    }
}
