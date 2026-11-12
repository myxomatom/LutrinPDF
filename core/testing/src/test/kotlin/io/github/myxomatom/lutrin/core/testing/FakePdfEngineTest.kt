// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakePdfEngineTest {
    private val source = DocumentSource.FromFile(File("fake.pdf"))

    @Test
    fun `opens anything without a password by default`() = runTest {
        val engine = FakePdfEngine()

        assertThat(engine.open(source).isSuccess).isTrue()
        assertThat(engine.givenPasswords).containsExactly(null)
    }

    @Test
    fun `each opening gives an open document, like the real engine`() = runTest {
        val document = FakePdfDocument()
        val engine = FakePdfEngine(Result.success(document))
        engine.open(source).getOrThrow().close()

        val reopened = engine.open(source).getOrThrow()

        assertThat(document.isClosed).isFalse()
        assertThat(reopened.pageSize(0)).isEqualTo(A4)
    }

    @Test
    fun `closing one of two openings leaves the other one open`() = runTest {
        val document = FakePdfDocument()
        val engine = FakePdfEngine(Result.success(document))
        val thumbnail = engine.open(source).getOrThrow()
        val reader = engine.open(source).getOrThrow()

        thumbnail.close()
        val stillOpen = !document.isClosed
        reader.close()

        assertThat(stillOpen).isTrue()
        assertThat(document.isClosed).isTrue()
    }

    @Test
    fun `an encrypted fake asks for its password and refuses another one`() = runTest {
        val engine = FakePdfEngine(password = "lutrin")

        assertThat(engine.open(source).exceptionOrNull()).isInstanceOf(PdfError.PasswordRequired::class.java)
        assertThat(engine.open(source, "other").exceptionOrNull()).isInstanceOf(PdfError.IncorrectPassword::class.java)
        assertThat(engine.open(source, "lutrin").isSuccess).isTrue()
        assertThat(engine.givenPasswords).containsExactly(null, "other", "lutrin").inOrder()
    }
}
