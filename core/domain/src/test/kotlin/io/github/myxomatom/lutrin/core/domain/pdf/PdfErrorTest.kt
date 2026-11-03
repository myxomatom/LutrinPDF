// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PdfErrorTest {
    @Test
    fun `page out of range reports both values`() {
        val error = PdfError.PageOutOfRange(pageIndex = 12, pageCount = 10)

        assertThat(error.pageIndex).isEqualTo(12)
        assertThat(error.pageCount).isEqualTo(10)
        assertThat(error).hasMessageThat().contains("12")
    }

    @Test
    fun `errors keep their cause`() {
        val cause = IllegalStateException("native")

        assertThat(PdfError.Unreadable(cause)).hasCauseThat().isSameInstanceAs(cause)
        assertThat(PdfError.NotAccessible(cause)).hasCauseThat().isSameInstanceAs(cause)
    }

    @Test
    fun `messages never expose document data`() {
        val errors =
            listOf(
                PdfError.PasswordRequired(),
                PdfError.IncorrectPassword(),
                PdfError.UnsupportedFormat(),
                PdfError.Unreadable(),
                PdfError.NotAccessible(),
                PdfError.Closed(),
            )

        errors.forEach { assertThat(it.message).doesNotContain("/") }
    }
}
