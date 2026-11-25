// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.print

import android.net.Uri
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import kotlinx.coroutines.CoroutineDispatcher

/**
 * A document to print (F15, ADR 0026), prepared by the reader's ViewModel and handed to the
 * system print service by the screen.
 *
 * @property name name of the document, without extension: the name of the print job, and of the
 *   file « Enregistrer au format PDF » suggests (with « .pdf »).
 * @property source what is sent to the printer.
 * @property dispatcher where the document is copied or written, off the main thread.
 */
internal class PrintJob(val name: String, val source: PrintSource, val dispatcher: CoroutineDispatcher)

/** What is sent to the printer (ADR 0026). */
internal sealed interface PrintSource {
    /** Number of pages of the document. */
    val pageCount: Int

    /**
     * The PDF file itself, copied as is from [uri]: the printout is what the file holds, without
     * any display option of the reader. The system print service keeps the pages the user chose.
     *
     * @property uri the document, readable by the app.
     */
    data class Original(val uri: Uri, override val pageCount: Int) : PrintSource

    /**
     * A document opened with its password: its pages are drawn into images, one at a time, and
     * the images make the PDF sent to the printer. The decrypted PDF is never written anywhere.
     *
     * @property document the open document, which draws the pages on its own thread.
     * @property pageSizes size of every page, rotation of the file applied, in points.
     */
    class Rendered(val document: PdfDocument, val pageSizes: List<PageSize>) : PrintSource {
        override val pageCount: Int get() = pageSizes.size
    }
}
