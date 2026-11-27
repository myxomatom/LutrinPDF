// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.settings.DocumentColorMode
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfModelsTest {
    @Test
    fun `links expose their bounds`() {
        val bounds = PdfRect(0f, 0f, 10f, 10f)
        val links: List<PdfLink> =
            listOf(PdfLink.Internal(bounds, PdfDestination(2)), PdfLink.External(bounds, "https://example.org/"))

        assertThat(links.map { it.bounds }).containsExactly(bounds, bounds)
    }

    @Test
    fun `a destination without coordinates shows the top of its page`() {
        val destination = PdfDestination(pageIndex = 3)

        assertThat(destination.x).isNull()
        assertThat(destination.y).isNull()
    }

    @Test
    fun `a destination keeps the position it targets`() {
        val destination = PdfDestination(pageIndex = 3, y = 421f)

        assertThat(destination.y).isEqualTo(421f)
        assertThat(destination.x).isNull()
    }

    @Test
    fun `a destination rejects a negative page`() {
        assertThrows(IllegalArgumentException::class.java) { PdfDestination(pageIndex = -1) }
    }

    @Test
    fun `an outline entry without destination may point outside the document`() {
        val entry = OutlineItem("Site", destination = null, uri = "https://example.org/")

        assertThat(entry.destination).isNull()
        assertThat(entry.children).isEmpty()
    }

    @Test
    fun `metadata defaults to unknown fields`() {
        val metadata = PdfMetadata(pageCount = 3)

        assertThat(metadata.title).isNull()
        assertThat(metadata.isEncrypted).isFalse()
    }

    @Test
    fun `render options draw the original colours by default`() {
        assertThat(RenderOptions().colorMode).isEqualTo(DocumentColorMode.NORMAL)
        assertThat(RenderOptions.ORIGINAL).isEqualTo(RenderOptions())
    }

    @Test
    fun `render options with another colour mode are another rendering`() {
        val night = RenderOptions(DocumentColorMode.NIGHT)

        assertThat(night).isNotEqualTo(RenderOptions.ORIGINAL)
        assertThat(night).isEqualTo(RenderOptions(DocumentColorMode.NIGHT))
    }

    @Test
    fun `render options are not turned by default, and a turned rendering is another rendering`() {
        val turned = RenderOptions(rotation = PageRotation.QUARTER)

        assertThat(RenderOptions.ORIGINAL.rotation).isEqualTo(PageRotation.NONE)
        assertThat(turned).isNotEqualTo(RenderOptions.ORIGINAL)
        assertThat(turned).isNotEqualTo(RenderOptions(rotation = PageRotation.HALF))
    }
}
