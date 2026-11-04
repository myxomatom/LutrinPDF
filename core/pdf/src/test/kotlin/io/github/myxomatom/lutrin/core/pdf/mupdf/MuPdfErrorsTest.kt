// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import kotlinx.coroutines.CancellationException
import org.junit.Test

class MuPdfErrorsTest {
    @Test
    fun `releasingOnFailure does not release on success`() {
        var released = false

        val result = releasingOnFailure(release = { released = true }) { 42 }

        assertThat(result).isEqualTo(42)
        assertThat(released).isFalse()
    }

    @Test
    fun `releasingOnFailure releases and rethrows any throwable`() {
        listOf(
            PdfError.Unreadable(),
            CancellationException("cancelled"),
            IllegalStateException("unexpected"),
            OutOfMemoryError("simulated"),
        ).forEach { failure ->
            var released = false

            val error = runCatching { releasingOnFailure<Int>(release = { released = true }) { throw failure } }

            assertThat(error.exceptionOrNull()).isSameInstanceAs(failure)
            assertThat(released).isTrue()
        }
    }

    @Test
    fun `releasingOnFailure keeps the original failure when releasing fails too`() {
        val failure = IllegalStateException("original")
        val releaseFailure = IllegalArgumentException("release")

        val error = runCatching { releasingOnFailure<Int>(release = { throw releaseFailure }) { throw failure } }

        assertThat(error.exceptionOrNull()).isSameInstanceAs(failure)
        assertThat(failure.suppressed.toList()).containsExactly(releaseFailure)
    }

    @Test
    fun `translates native errors into Unreadable`() {
        val error = runCatching { translatingMuPdfErrors<Int> { throw IllegalStateException("native") } }

        assertThat(error.exceptionOrNull()).isInstanceOf(PdfError.Unreadable::class.java)
    }
}
