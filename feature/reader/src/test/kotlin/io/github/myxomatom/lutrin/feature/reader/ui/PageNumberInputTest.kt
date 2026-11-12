// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The validation behind « Aller à la page » (V3, F05). */
class PageNumberInputTest {
    @Test
    fun `a number inside the document is the zero-based page`() {
        assertThat(parsePageNumber("1", pageCount = 8)).isEqualTo(PageNumberInput.Valid(0))
        assertThat(parsePageNumber("5", pageCount = 8)).isEqualTo(PageNumberInput.Valid(4))
        assertThat(parsePageNumber("8", pageCount = 8)).isEqualTo(PageNumberInput.Valid(7))
    }

    @Test
    fun `blank text is empty`() {
        assertThat(parsePageNumber("", pageCount = 8)).isEqualTo(PageNumberInput.Empty)
        assertThat(parsePageNumber("  ", pageCount = 8)).isEqualTo(PageNumberInput.Empty)
    }

    @Test
    fun `zero is out of range`() {
        assertThat(parsePageNumber("0", pageCount = 8)).isEqualTo(PageNumberInput.OutOfRange)
        assertThat(parsePageNumber("000", pageCount = 8)).isEqualTo(PageNumberInput.OutOfRange)
    }

    @Test
    fun `a number past the last page is out of range`() {
        assertThat(parsePageNumber("9", pageCount = 8)).isEqualTo(PageNumberInput.OutOfRange)
        assertThat(parsePageNumber("2", pageCount = 1)).isEqualTo(PageNumberInput.OutOfRange)
    }

    @Test
    fun `a number too long for an Int is out of range`() {
        assertThat(parsePageNumber("99999999999999", pageCount = 8)).isEqualTo(PageNumberInput.OutOfRange)
    }

    @Test
    fun `leading zeros and spaces around the number are tolerated`() {
        assertThat(parsePageNumber(" 007 ", pageCount = 8)).isEqualTo(PageNumberInput.Valid(6))
    }

    @Test
    fun `something that is not a number is out of range`() {
        assertThat(parsePageNumber("abc", pageCount = 8)).isEqualTo(PageNumberInput.OutOfRange)
        assertThat(parsePageNumber("-3", pageCount = 8)).isEqualTo(PageNumberInput.OutOfRange)
    }

    @Test
    fun `the only page of a one-page document is 1`() {
        assertThat(parsePageNumber("1", pageCount = 1)).isEqualTo(PageNumberInput.Valid(0))
    }
}
