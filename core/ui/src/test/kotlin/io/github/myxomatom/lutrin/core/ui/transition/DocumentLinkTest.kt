// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.transition

import androidx.compose.runtime.saveable.SaverScope
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocumentLinkTest {
    @Test
    fun `nothing is linked at first`() {
        assertThat(DocumentLink().linked).isNull()
        assertThat(DocumentLink().opening).isNull()
    }

    @Test
    fun `a tapped document is not linked while its reader comes in`() {
        val link = DocumentLink().tapped(DOCUMENT)

        assertThat(link.linked).isNull()
        assertThat(link.opening).isEqualTo(DOCUMENT)
    }

    @Test
    fun `a tap unlinks the document linked before, even when it is the same one`() {
        val link = DocumentLink(linked = DOCUMENT).tapped(DOCUMENT)

        assertThat(link.linked).isNull()
    }

    @Test
    fun `the tapped document is linked once its reader is settled`() {
        val link = DocumentLink().tapped(DOCUMENT).settled(onLibrary = false)

        assertThat(link).isEqualTo(DocumentLink(linked = DOCUMENT))
    }

    @Test
    fun `the link survives the return to the library, which shrinks the page into it`() {
        val link = DocumentLink().tapped(DOCUMENT).settled(onLibrary = false).settled(onLibrary = true)

        assertThat(link.linked).isEqualTo(DOCUMENT)
    }

    @Test
    fun `back to the library before the reader settles links nothing`() {
        val link = DocumentLink().tapped(DOCUMENT).settled(onLibrary = true)

        assertThat(link).isEqualTo(DocumentLink())
        assertThat(link.settled(onLibrary = false).linked).isNull()
    }

    @Test
    fun `another screen settling keeps the current link`() {
        val link = DocumentLink(linked = DOCUMENT)

        assertThat(link.settled(onLibrary = false)).isEqualTo(link)
    }

    @Test
    fun `a reader opened another way links nothing`() {
        val link = DocumentLink(linked = DOCUMENT).tapped(OTHER).openedElsewhere().settled(onLibrary = false)

        assertThat(link).isEqualTo(DocumentLink())
    }

    @Test
    fun `the link is saved and restored`() {
        val saverScope = SaverScope { true }
        val links = listOf(DocumentLink(), DocumentLink(opening = DOCUMENT), DocumentLink(linked = OTHER))

        links.forEach { link ->
            val saved = with(DocumentLink.Saver) { saverScope.save(link) }
            assertThat(saved?.let(DocumentLink.Saver::restore)).isEqualTo(link)
        }
    }

    private companion object {
        const val DOCUMENT = "document-1"
        const val OTHER = "document-2"
    }
}
