// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.AbortException
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import kotlinx.coroutines.CancellationException

/**
 * Runs MuPDF code, translating its failures: an aborted operation becomes a
 * [CancellationException], any other native error a [PdfError.Unreadable].
 *
 * Catching [RuntimeException] is deliberate: MuPDF's JNI layer reports every native error
 * (damaged object, unsupported feature, out of memory in the C heap) as a plain
 * [RuntimeException]. [PdfError] is a checked exception and passes through unchanged.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> translatingMuPdfErrors(block: () -> T): T = try {
    block()
} catch (error: AbortException) {
    throw CancellationException("MuPDF operation aborted").apply { initCause(error) }
} catch (error: RuntimeException) {
    throw PdfError.Unreadable(error)
}
