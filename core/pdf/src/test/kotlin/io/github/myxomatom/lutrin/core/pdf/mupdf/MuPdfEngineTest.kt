// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MuPdfEngineTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val expectedPageCounts =
        mapOf(
            "pdfs/simple_1page.pdf" to 1,
            "pdfs/large_600pages.pdf" to 600,
            "pdfs/nested_outline.pdf" to 6,
            "pdfs/links_internal_external.pdf" to 4,
            "pdfs/form_acroform.pdf" to 1,
            "pdfs/scanned_images_only.pdf" to 2,
            "pdfs/mixed_page_sizes.pdf" to 5,
            "pdfs/non_latin_text.pdf" to 1,
        )

    @Test
    fun `opens every valid PDF of the test set with its page count`() = runTest {
        expectedPageCounts.forEach { (path, pages) ->
            openTestPdf(path).use { document -> assertThat(document.pageCount).isEqualTo(pages) }
        }
    }

    @Test
    fun `repairs a truncated PDF instead of crashing`() = runTest {
        openTestPdf("pdfs/corrupted_truncated.pdf").use { assertThat(it.pageCount).isEqualTo(1) }
    }

    @Test
    fun `reports a damaged PDF as unreadable`() = runTest {
        val result = engine().open(TestAssets.source("pdfs/corrupted_garbage.pdf"))

        assertThat(result.exceptionOrNull()).isInstanceOf(PdfError.Unreadable::class.java)
    }

    @Test
    fun `reports a renamed non PDF file as unsupported`() = runTest {
        val result = engine().open(TestAssets.source("pdfs/not_a_pdf.pdf"))

        assertThat(result.exceptionOrNull()).isInstanceOf(PdfError.UnsupportedFormat::class.java)
    }

    @Test
    fun `reports a missing file as not accessible`() = runTest {
        val result = engine().open(DocumentSource.FromFile(File(folder.root, "absent.pdf")))

        assertThat(result.exceptionOrNull()).isInstanceOf(PdfError.NotAccessible::class.java)
    }

    @Test
    fun `asks for the password of an encrypted PDF`() = runTest {
        val result = engine().open(TestAssets.source("pdfs/encrypted_password_lutrin.pdf"))

        assertThat(result.exceptionOrNull()).isInstanceOf(PdfError.PasswordRequired::class.java)
    }

    @Test
    fun `rejects a wrong password`() = runTest {
        val result = engine().open(TestAssets.source("pdfs/encrypted_password_lutrin.pdf"), password = "wrong")

        assertThat(result.exceptionOrNull()).isInstanceOf(PdfError.IncorrectPassword::class.java)
    }

    @Test
    fun `opens an encrypted PDF with the right password`() = runTest {
        openTestPdf("pdfs/encrypted_password_lutrin.pdf", password = "lutrin").use { document ->
            assertThat(document.metadata.isEncrypted).isTrue()
            assertThat(document.textAt(0, wholePage)).contains("Document chiffré")
        }
    }

    @Test
    fun `opens a document from memory`() = runTest {
        val bytes = TestAssets.file("pdfs/links_internal_external.pdf").readBytes()

        engine().open(DocumentSource.FromBytes(bytes)).getOrThrow().use { assertThat(it.pageCount).isEqualTo(4) }
    }

    @Test
    fun `rejects bytes that are not a PDF`() = runTest {
        val result = engine().open(DocumentSource.FromBytes("hello".toByteArray()))

        assertThat(result.exceptionOrNull()).isInstanceOf(PdfError.UnsupportedFormat::class.java)
    }

    @Test
    fun `releases the document thread when opening fails`() = runTest {
        val dispatchers = RecordingDispatcherFactory()

        engine(dispatchers).open(TestAssets.source("pdfs/corrupted_garbage.pdf"))
        engine(dispatchers).open(TestAssets.source("pdfs/encrypted_password_lutrin.pdf"))

        assertThat(dispatchers.created).hasSize(2)
        dispatchers.created.forEach { assertThat(it.executor.isShutdownExecutor()).isTrue() }
    }

    private fun java.util.concurrent.Executor.isShutdownExecutor() =
        (this as java.util.concurrent.ExecutorService).isShutdown
}
