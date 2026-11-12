// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Helpers of the engine that need no native library, so these tests run everywhere. */
class MuPdfHelpersTest {
    @Test
    fun `reads the size of a FitR area from a MuPDF link URI`() {
        assertThat(viewRectSize("#page=2&viewrect=200,100,150,80")).isEqualTo(150f to 80f)
        assertThat(viewRectSize("file:other.pdf#page=3&viewrect=1.5,2,3.25,4")).isEqualTo(3.25f to 4f)
    }

    @Test
    fun `no size for other destinations or an empty area`() {
        assertThat(viewRectSize("#page=2&zoom=nan,0,421")).isNull()
        assertThat(viewRectSize("#nameddest=chapitre-3")).isNull()
        assertThat(viewRectSize("#page=2&viewrect=200,100,0,80")).isNull()
        assertThat(viewRectSize("#page=2&viewrect=200,100,nan,80")).isNull()
    }

    @Test
    fun `search order starts at the given page and wraps around`() {
        assertThat(searchOrder(startPage = 2, pageCount = 4)).containsExactly(2, 3, 0, 1).inOrder()
        assertThat(searchOrder(startPage = 0, pageCount = 3)).containsExactly(0, 1, 2).inOrder()
    }
}
