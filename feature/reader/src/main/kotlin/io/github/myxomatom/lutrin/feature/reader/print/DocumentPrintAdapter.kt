// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.print

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import androidx.core.graphics.createBitmap
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Resolution of the images of a printed protected document (ADR 0026). */
internal const val PRINT_DPI = 150f

/** Points per inch: the unit of PDF page sizes. */
private const val POINTS_PER_INCH = 72f

/**
 * Most pixels in the image of one printed page (32 MB in ARGB): a page larger than A2 is drawn
 * below [PRINT_DPI] rather than exhausting the memory.
 */
internal const val MAX_PRINT_PIXELS = 8_000_000

/** Size of the buffer that copies the original file. */
private const val COPY_BUFFER_SIZE = 64 * 1024

/** Extension of the file name given to the print service. */
private const val PDF_EXTENSION = ".pdf"

/**
 * Sends a document to the system print service (F15, ADR 0026).
 *
 * - [PrintSource.Original]: the PDF file is copied as is, page range included; the print service
 *   keeps the pages the user chose ([PageRange.ALL_PAGES] is reported as written).
 * - [PrintSource.Rendered]: only the chosen pages are drawn, one at a time at [PRINT_DPI], into a
 *   PDF of images ([ImagePdfWriter]); one bitmap is reused from page to page and recycled at the
 *   end. The decrypted PDF is never written.
 *
 * The layout never depends on the paper chosen: the print service fits each page to it. The
 * writing runs on [PrintJob.dispatcher] and stops when the print service cancels it (cancelled
 * write reported, no further page drawn). The callbacks are called once per request, from that
 * dispatcher. A file that can no longer be read, or a document closed meanwhile (the reader was
 * left), fails the write.
 *
 * @param job the document to print.
 * @param openInput opens the original file (`ContentResolver.openInputStream`); `null` when the
 *   provider has nothing to give.
 */
internal class DocumentPrintAdapter(private val job: PrintJob, private val openInput: (Uri) -> InputStream?) :
    PrintDocumentAdapter() {
    private val scope = CoroutineScope(SupervisorJob() + job.dispatcher)
    private var laidOut = false

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info =
            PrintDocumentInfo
                .Builder(job.name + PDF_EXTENSION)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(job.source.pageCount)
                .build()
        // Only the first layout changes anything: the pages do not depend on the paper.
        callback.onLayoutFinished(info, !laidOut)
        laidOut = true
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }
        // Exactly one answer per request, even when cancelled before the writing starts.
        val answered = AtomicBoolean(false)
        val answer = { reply: () -> Unit -> if (answered.compareAndSet(false, true)) reply() }
        val writing =
            scope.launch {
                try {
                    val written = FileOutputStream(destination.fileDescriptor).use { output -> write(pages, output) }
                    answer { callback.onWriteFinished(written) }
                } catch (failure: IOException) {
                    answer { callback.onWriteFailed(failure.message) }
                } catch (failure: PdfError) {
                    answer { callback.onWriteFailed(failure.message) }
                }
            }
        writing.invokeOnCompletion { cause -> if (cause != null) answer { callback.onWriteCancelled() } }
        cancellationSignal?.setOnCancelListener { writing.cancel() }
    }

    override fun onFinish() {
        scope.cancel()
    }

    /** Writes the requested [pages] into [output]; returns the pages written. */
    private suspend fun write(pages: Array<out PageRange>, output: OutputStream): Array<PageRange> =
        when (val source = job.source) {
            is PrintSource.Original -> {
                copyOriginal(source.uri, output)
                arrayOf(PageRange.ALL_PAGES)
            }

            is PrintSource.Rendered -> {
                val selected = selectedPages(pages, source.pageCount)
                if (selected.isEmpty()) throw IOException("No page to print among ${pages.toList()}")
                writeRendered(source, selected, output)
                selected.toPageRanges()
            }
        }

    private suspend fun copyOriginal(uri: Uri, output: OutputStream) {
        val input =
            try {
                openInput(uri)
            } catch (denied: SecurityException) {
                throw IOException("Permission to read the document was revoked", denied)
            } ?: throw FileNotFoundException("The provider gave no content")
        input.use {
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = it.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
            }
        }
    }

    private suspend fun writeRendered(source: PrintSource.Rendered, pages: List<Int>, output: OutputStream) {
        val writer = ImagePdfWriter(output)
        var bitmap: Bitmap? = null
        try {
            for (index in pages) {
                currentCoroutineContext().ensureActive()
                val size = source.pageSizes[index]
                val scale = printScale(size.width, size.height)
                val target = reusable(bitmap, ceil(size.width * scale).toInt(), ceil(size.height * scale).toInt())
                bitmap = target
                // Transparent areas of the page print as paper, not black.
                target.eraseColor(Color.WHITE)
                source.document.renderPage(index, target, PdfRect(0f, 0f, size.width, size.height), scale, false)
                writer.addPage(size.width, size.height, target.asPageImage())
            }
            writer.finish()
        } finally {
            bitmap?.recycle()
        }
    }
}

/**
 * Pixels per point of the image of a printed page of [width] × [height] points: [PRINT_DPI],
 * lowered for a page that would exceed [MAX_PRINT_PIXELS].
 */
internal fun printScale(width: Float, height: Float): Float {
    val scale = PRINT_DPI / POINTS_PER_INCH
    val pixels = width * scale * height * scale
    return if (pixels <= MAX_PRINT_PIXELS) scale else scale * sqrt(MAX_PRINT_PIXELS / pixels)
}

/**
 * The zero-based pages of [ranges] that exist among [pageCount] pages, in order, each once.
 * [PageRange.ALL_PAGES] selects them all.
 */
internal fun selectedPages(ranges: Array<out PageRange>, pageCount: Int): List<Int> =
    if (ranges.any { it == PageRange.ALL_PAGES }) {
        (0 until pageCount).toList()
    } else {
        ranges
            .flatMap { it.start..min(it.end, pageCount - 1) }
            .filter { it in 0 until pageCount }
            .distinct()
            .sorted()
    }

/** Sorted distinct pages as the shortest list of ranges: `[0, 1, 2, 5]` gives 0–2 and 5–5. */
internal fun List<Int>.toPageRanges(): Array<PageRange> {
    val ranges = mutableListOf<PageRange>()
    var start = first()
    var end = start
    for (page in drop(1)) {
        if (page == end + 1) {
            end = page
        } else {
            ranges += PageRange(start, end)
            start = page
            end = page
        }
    }
    ranges += PageRange(start, end)
    return ranges.toTypedArray()
}

/** [current] when it already has the size asked, otherwise a new bitmap (the current one is recycled). */
private fun reusable(current: Bitmap?, width: Int, height: Int): Bitmap {
    if (current != null && current.width == width && current.height == height) return current
    current?.recycle()
    return createBitmap(width, height)
}

/** This bitmap as the image of a page, read row by row. */
private fun Bitmap.asPageImage(): PageImage = PageImage(width, height) { row, pixels ->
    getPixels(pixels, 0, width, 0, row, width, 1)
}
