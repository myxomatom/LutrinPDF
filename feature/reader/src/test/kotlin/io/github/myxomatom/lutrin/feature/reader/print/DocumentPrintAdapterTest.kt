// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.print

import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentInfo
import android.print.RecordingLayoutCallback
import android.print.RecordingWriteCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FAKE_PAGE_COLOR
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Printing (F15, ADR 0026): the original file copied as is, a protected document drawn page by
 * page into images, cancellation and failures, each answered exactly once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DocumentPrintAdapterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val uri = Uri.parse("content://com.example.provider/document/rapport.pdf")
    private val original = "%PDF-1.7 the original bytes".repeat(10_000).toByteArray()
    private val document = FakePdfDocument(pageSizes = listOf(A4, A4, PageSize(842f, 595f)))
    private val output by lazy { folder.newFile("printed.pdf") }
    private val attributes = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()

    private fun adapter(source: PrintSource, openInput: (Uri) -> InputStream? = { ByteArrayInputStream(original) }) =
        DocumentPrintAdapter(PrintJob("Rapport", source, dispatcher), openInput)

    private fun originalSource() = PrintSource.Original(uri, pageCount = 3)

    private fun renderedSource(of: PdfDocument = document) = PrintSource.Rendered(of, document.pageSizes)

    private fun DocumentPrintAdapter.write(
        pages: Array<PageRange>,
        signal: CancellationSignal = CancellationSignal(),
        into: File = output,
    ): RecordingWriteCallback {
        val callback = RecordingWriteCallback()
        val destination = ParcelFileDescriptor.open(into, ParcelFileDescriptor.MODE_READ_WRITE)
        onWrite(pages, destination, signal, callback)
        return callback
    }

    private fun TestScope.writeAndWait(adapter: DocumentPrintAdapter, vararg pages: PageRange): RecordingWriteCallback =
        adapter.write(arrayOf(*pages)).also { advanceUntilIdle() }

    @Test
    fun `describes the document, named after it, with its pages`() {
        val callback = RecordingLayoutCallback()

        adapter(originalSource()).onLayout(null, attributes, CancellationSignal(), callback, null)

        val info = checkNotNull(callback.info)
        assertThat(callback.answers).containsExactly("finished")
        assertThat(info.name).isEqualTo("Rapport.pdf")
        assertThat(info.pageCount).isEqualTo(3)
        assertThat(info.contentType).isEqualTo(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
        assertThat(callback.changed).isTrue()
    }

    @Test
    fun `a new paper does not change the pages`() {
        val adapter = adapter(renderedSource())
        val callback = RecordingLayoutCallback()
        val letter = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.NA_LETTER).build()

        adapter.onLayout(null, attributes, CancellationSignal(), RecordingLayoutCallback(), null)
        adapter.onLayout(attributes, letter, CancellationSignal(), callback, null)

        assertThat(callback.changed).isFalse()
        assertThat(callback.info?.pageCount).isEqualTo(3)
    }

    @Test
    fun `a cancelled layout is reported as such`() {
        val callback = RecordingLayoutCallback()
        val signal = CancellationSignal().apply { cancel() }

        adapter(originalSource()).onLayout(null, attributes, signal, callback, null)

        assertThat(callback.answers).containsExactly("cancelled")
    }

    @Test
    fun `sends the original file as is, every page`() = runTest(dispatcher) {
        val callback = writeAndWait(adapter(originalSource()), PageRange.ALL_PAGES)

        assertThat(callback.answers).containsExactly("finished")
        assertThat(callback.pages).containsExactly(PageRange.ALL_PAGES)
        assertThat(output.readBytes()).isEqualTo(original)
    }

    @Test
    fun `sends the whole original file for a range, and lets the print service keep the range`() = runTest(dispatcher) {
        val callback = writeAndWait(adapter(originalSource()), PageRange(1, 1))

        assertThat(callback.answers).containsExactly("finished")
        assertThat(callback.pages).containsExactly(PageRange.ALL_PAGES)
        assertThat(output.readBytes()).isEqualTo(original)
    }

    @Test
    fun `fails when the file can no longer be read`() = runTest(dispatcher) {
        val all = PageRange.ALL_PAGES
        val missing = writeAndWait(adapter(originalSource()) { throw FileNotFoundException("gone") }, all)
        val revoked = writeAndWait(adapter(originalSource()) { throw SecurityException("revoked") }, all)
        val empty = writeAndWait(adapter(originalSource()) { null }, all)

        assertThat(missing.answers).containsExactly("failed")
        assertThat(revoked.answers).containsExactly("failed")
        assertThat(empty.answers).containsExactly("failed")
    }

    @Test
    fun `fails when the file breaks while it is copied`() = runTest(dispatcher) {
        val breaking = object : InputStream() {
            override fun read(): Int = throw java.io.IOException("disk error")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw java.io.IOException("disk error")
        }

        val callback = writeAndWait(adapter(originalSource()) { breaking }, PageRange.ALL_PAGES)

        assertThat(callback.answers).containsExactly("failed")
        assertThat(callback.error.toString()).contains("disk error")
    }

    @Test
    fun `fails when the print service gives a destination that cannot be written`() = runTest(dispatcher) {
        val readOnly = folder.newFile("read-only.pdf")
        val callback = RecordingWriteCallback()
        val destination = ParcelFileDescriptor.open(readOnly, ParcelFileDescriptor.MODE_READ_ONLY)

        adapter(originalSource()).onWrite(arrayOf(PageRange.ALL_PAGES), destination, CancellationSignal(), callback)
        advanceUntilIdle()

        assertThat(callback.answers).containsExactly("failed")
    }

    @Test
    fun `draws every page of a protected document into images, at 150 dpi`() = runTest(dispatcher) {
        val callback = writeAndWait(adapter(renderedSource()), PageRange.ALL_PAGES)

        assertThat(callback.answers).containsExactly("finished")
        assertThat(callback.pages).containsExactly(PageRange(0, 2))
        assertThat(document.renders.map { it.pageIndex }).containsExactly(0, 1, 2).inOrder()
        assertThat(document.renders.map { it.scale }.distinct()).containsExactly(PRINT_DPI / 72f)
        assertThat(document.renders.first().region).isEqualTo(PdfRect(0f, 0f, A4.width, A4.height))
        val printed = PrintedPdf(output.readBytes())
        assertThat(printed.hasConsistentCrossReferences).isTrue()
        assertThat(printed.declaredPageCount).isEqualTo(3)
        assertThat(printed.mediaBoxes).containsExactly("0 0 595 842", "0 0 595 842", "0 0 842 595").inOrder()
        // 595 points at 150 dpi: 1 240 pixels.
        assertThat(printed.images.first().width).isEqualTo(1240)
        assertThat(printed.images.map { it.pixel(10, 10) }.distinct()).containsExactly(FAKE_PAGE_COLOR and 0xFFFFFF)
    }

    @Test
    fun `draws only the pages asked for, and reports them`() = runTest(dispatcher) {
        val callback = writeAndWait(adapter(renderedSource()), PageRange(2, 2), PageRange(0, 0))

        assertThat(callback.answers).containsExactly("finished")
        assertThat(callback.pages).containsExactly(PageRange(0, 0), PageRange(2, 2)).inOrder()
        assertThat(document.renders.map { it.pageIndex }).containsExactly(0, 2).inOrder()
        assertThat(PrintedPdf(output.readBytes()).declaredPageCount).isEqualTo(2)
    }

    @Test
    fun `fails without drawing when no page asked for exists`() = runTest(dispatcher) {
        val callback = writeAndWait(adapter(renderedSource()), PageRange(5, 9))

        assertThat(callback.answers).containsExactly("failed")
        assertThat(document.renders).isEmpty()
    }

    @Test
    fun `fails when the protected document was closed meanwhile`() = runTest(dispatcher) {
        val adapter = adapter(renderedSource())
        document.close()

        val callback = writeAndWait(adapter, PageRange.ALL_PAGES)

        assertThat(callback.answers).containsExactly("failed")
    }

    @Test
    fun `a write cancelled before it starts draws nothing and is reported cancelled`() = runTest(dispatcher) {
        val signal = CancellationSignal()
        val callback = adapter(renderedSource()).write(arrayOf(PageRange.ALL_PAGES), signal)

        signal.cancel()
        advanceUntilIdle()

        assertThat(callback.answers).containsExactly("cancelled")
        assertThat(document.renders).isEmpty()
    }

    @Test
    fun `a write already cancelled is answered at once`() {
        val signal = CancellationSignal().apply { cancel() }

        val callback = adapter(renderedSource()).write(arrayOf(PageRange.ALL_PAGES), signal)

        assertThat(callback.answers).containsExactly("cancelled")
    }

    @Test
    fun `cancelling during the drawing stops before the next page`() = runTest(dispatcher) {
        val signal = CancellationSignal()
        val cancellingOnFirstPage = object : PdfDocument by document {
            override suspend fun renderPage(
                index: Int,
                target: Bitmap,
                region: PdfRect,
                scale: Float,
                nightMode: Boolean,
            ) {
                document.renderPage(index, target, region, scale, nightMode)
                signal.cancel()
            }
        }

        val callback = adapter(renderedSource(cancellingOnFirstPage)).write(arrayOf(PageRange.ALL_PAGES), signal)
        advanceUntilIdle()

        assertThat(callback.answers).containsExactly("cancelled")
        assertThat(document.renders.map { it.pageIndex }).containsExactly(0)
    }

    @Test
    fun `cancelling during the copy stops it`() = runTest(dispatcher) {
        val signal = CancellationSignal()
        val cancellingStream = object : InputStream() {
            private val bytes = ByteArrayInputStream(original)

            override fun read(): Int = bytes.read()

            override fun read(b: ByteArray, off: Int, len: Int): Int = bytes.read(b, off, len).also { signal.cancel() }
        }

        val callback = adapter(originalSource()) { cancellingStream }.write(arrayOf(PageRange.ALL_PAGES), signal)
        advanceUntilIdle()

        assertThat(callback.answers).containsExactly("cancelled")
        assertThat(output.length()).isLessThan(original.size.toLong())
    }

    @Test
    fun `a print dialog closed during the write cancels it`() = runTest(dispatcher) {
        val adapter = adapter(renderedSource())
        val callback = adapter.write(arrayOf(PageRange.ALL_PAGES))

        adapter.onFinish()
        advanceUntilIdle()

        assertThat(callback.answers).containsExactly("cancelled")
        assertThat(document.renders).isEmpty()
    }

    @Test
    fun `prints at 150 dpi, lower for a page that would need too many pixels`() {
        assertThat(printScale(A4.width, A4.height)).isEqualTo(PRINT_DPI / 72f)
        // A0 at 150 dpi would be 35 million pixels.
        val scale = printScale(2384f, 3370f)
        assertThat(scale).isLessThan(PRINT_DPI / 72f)
        assertThat(2384f * scale * 3370f * scale).isWithin(1_000f).of(MAX_PRINT_PIXELS.toFloat())
    }

    @Test
    fun `turns the ranges asked for into pages that exist, and back into ranges`() {
        assertThat(selectedPages(arrayOf(PageRange.ALL_PAGES), 3)).containsExactly(0, 1, 2).inOrder()
        assertThat(selectedPages(arrayOf(PageRange(4, 6), PageRange(0, 1), PageRange(1, 2)), 5))
            .containsExactly(0, 1, 2, 4)
            .inOrder()
        assertThat(selectedPages(arrayOf(PageRange(7, 9)), 5)).isEmpty()
        assertThat(listOf(0, 1, 2, 4, 6, 7).toPageRanges().toList())
            .containsExactly(PageRange(0, 2), PageRange(4, 4), PageRange(6, 7))
            .inOrder()
    }
}
