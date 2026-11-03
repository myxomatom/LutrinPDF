// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfGeometryTest {
    @Test
    fun `page size reports its orientation`() {
        assertThat(PageSize(842f, 595f).isLandscape).isTrue()
        assertThat(PageSize(595f, 842f).isLandscape).isFalse()
    }

    @Test
    fun `page size rejects non positive dimensions`() {
        assertThrows(IllegalArgumentException::class.java) { PageSize(0f, 10f) }
        assertThrows(IllegalArgumentException::class.java) { PageSize(10f, -1f) }
    }

    @Test
    fun `rect computes its size and containment`() {
        val rect = PdfRect(10f, 20f, 110f, 70f)

        assertThat(rect.width).isEqualTo(100f)
        assertThat(rect.height).isEqualTo(50f)
        assertThat(rect.isEmpty).isFalse()
        assertThat(rect.contains(10f, 70f)).isTrue()
        assertThat(rect.contains(9f, 30f)).isFalse()
        assertThat(PdfRect(1f, 1f, 1f, 5f).isEmpty).isTrue()
    }

    @Test
    fun `rect rejects inverted coordinates`() {
        assertThrows(IllegalArgumentException::class.java) { PdfRect(10f, 0f, 5f, 10f) }
        assertThrows(IllegalArgumentException::class.java) { PdfRect(0f, 10f, 5f, 5f) }
    }

    @Test
    fun `enclosing rect covers every point`() {
        val rect = PdfRect.enclosing(listOf(3f to 9f, 1f to 4f, 7f to 2f))

        assertThat(rect).isEqualTo(PdfRect(1f, 2f, 7f, 9f))
    }

    @Test
    fun `enclosing rect needs points`() {
        assertThrows(IllegalArgumentException::class.java) { PdfRect.enclosing(emptyList()) }
    }
}
