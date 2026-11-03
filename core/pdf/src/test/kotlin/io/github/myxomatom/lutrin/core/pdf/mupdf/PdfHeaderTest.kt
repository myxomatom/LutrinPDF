// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import org.junit.Test

class PdfHeaderTest {
    @Test
    fun `detects a header at the start`() {
        assertThat(hasPdfHeader("%PDF-1.7\n".toByteArray())).isTrue()
    }

    @Test
    fun `detects a header after leading bytes within the first kilobyte`() {
        assertThat(hasPdfHeader(ByteArray(1000) + "%PDF-2.0".toByteArray())).isTrue()
    }

    @Test
    fun `ignores a header beyond the first kilobyte`() {
        assertThat(hasPdfHeader(ByteArray(1020) + "%PDF-2.0".toByteArray())).isFalse()
    }

    @Test
    fun `rejects other content`() {
        assertThat(hasPdfHeader("Ceci est un fichier texte".toByteArray())).isFalse()
        assertThat(hasPdfHeader(ByteArray(0))).isFalse()
        assertThat(hasPdfHeader("%PDF".toByteArray())).isFalse()
    }

    @Test
    fun `reads up to the requested count`() {
        assertThat(ByteArrayInputStream(ByteArray(10)).readUpTo(4)).hasLength(4)
        assertThat(ByteArrayInputStream(ByteArray(3)).readUpTo(4)).hasLength(3)
    }
}
