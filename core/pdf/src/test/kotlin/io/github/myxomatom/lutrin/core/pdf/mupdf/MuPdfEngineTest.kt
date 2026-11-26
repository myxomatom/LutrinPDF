// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MuPdfEngineTest {
    @Before
    fun requireHostEngine() = assumeHostEngine()

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
            assertThat(document.allText()).contains("Document chiffré")
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

    @Test
    fun `releases the document thread when opening fails unexpectedly`() = runTest {
        val dispatchers = RecordingDispatcherFactory()
        val failingFile =
            object : File(TestAssets.file("pdfs/simple_1page.pdf").path) {
                override fun canRead(): Boolean = error("Simulated unexpected failure")
            }

        val error = runCatching { engine(dispatchers).open(DocumentSource.FromFile(failingFile)) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        dispatchers.awaitTermination()
        assertThat(dispatchers.created.single().executor.isShutdownExecutor()).isTrue()
    }

    @Test
    fun `closing a document releases its file`() = runBlocking<Unit> {
        val dispatchers = RecordingDispatcherFactory()
        val file = copyOfTestPdf("pdfs/simple_1page.pdf")

        val document = engine(dispatchers).open(DocumentSource.FromFile(file)).getOrThrow()
        // MuPDF keeps the file open while the document is: the check below is meaningful.
        assertThat(openDescriptorsTo(file)).isGreaterThan(0)
        document.close()

        dispatchers.awaitTermination()
        assertThat(openDescriptorsTo(file)).isEqualTo(0)
    }

    @Test
    fun `releases the document when the caller is cancelled while it is being opened`() = runBlocking<Unit> {
        val dispatchers = RecordingDispatcherFactory()
        val file = copyOfTestPdf("pdfs/simple_1page.pdf")
        lateinit var opening: Job
        // Cancels the caller from the document thread, while the native document is being built.
        val cancellingFile =
            object : File(file.path) {
                override fun canRead(): Boolean = super.canRead().also { opening.cancel() }
            }
        opening =
            launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
                engine(dispatchers).open(DocumentSource.FromFile(cancellingFile))
            }

        opening.start()
        opening.join()

        assertThat(opening.isCancelled).isTrue()
        dispatchers.awaitTermination()
        assertThat(dispatchers.created.single().executor.isShutdownExecutor()).isTrue()
        assertThat(openDescriptorsTo(file)).isEqualTo(0)
    }

    // page_tree_count_mismatch.pdf declares /Count 3 but its page tree holds 2 pages (A1, ADR 0031).

    @Test
    fun `opens a PDF whose page count exceeds its page tree`() = runTest {
        openTestPdf(COUNT_MISMATCH).use { assertThat(it.pageCount).isEqualTo(3) }
    }

    @Test
    fun `reports a page missing from the page tree as unreadable`() = runTest {
        openTestPdf(COUNT_MISMATCH).use { document ->
            val failures =
                listOf<suspend () -> Unit>(
                    { document.pageSize(2) },
                    { document.renderPixels(2, RenderSpec(wholePage, 0.1f, 60, 84, nightMode = false)) },
                    { document.links(2) },
                    { document.allText(2) },
                ).map { call -> runCatching { call() }.exceptionOrNull() }

            failures.forEach { assertThat(it).isInstanceOf(PdfError.Unreadable::class.java) }
        }
    }

    @Test
    fun `the document stays usable after an unreadable page`() = runTest {
        val dispatchers = RecordingDispatcherFactory()
        val reported = RecordingReleaseFailures()
        val opened = engine(dispatchers, reported).open(TestAssets.source(COUNT_MISMATCH))
        val document = opened.getOrThrow() as MuPdfDocument
        runCatching { document.pageSize(2) }

        val size = document.pageSize(0)
        val pixels = document.renderPixels(1, RenderSpec(wholePage, 0.5f, 298, 421, nightMode = false))
        val text = document.allText(1)
        document.close()

        assertThat(size).isEqualTo(PageSize(595f, 842f))
        assertThat(pixels.any { it != WHITE }).isTrue()
        assertThat(text).contains("Page 2")
        dispatchers.awaitTermination()
        assertThat(reported.failures).isEmpty()
    }

    private fun copyOfTestPdf(path: String): File = TestAssets.file(path).copyTo(File(folder.root, File(path).name))

    private fun java.util.concurrent.Executor.isShutdownExecutor() =
        (this as java.util.concurrent.ExecutorService).isShutdown

    private companion object {
        const val COUNT_MISMATCH = "pdfs/page_tree_count_mismatch.pdf"

        /** Opaque white, 0xFFFFFFFF. */
        const val WHITE = -1
    }
}
