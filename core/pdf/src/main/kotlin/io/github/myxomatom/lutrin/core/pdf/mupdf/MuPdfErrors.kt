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

/**
 * Runs [block] and, if it fails with any [Throwable] (business error, cancellation, native
 * failure, out of memory…), runs [release] before rethrowing the original failure. A failure of
 * [release] is attached to it as a suppressed exception rather than replacing it.
 *
 * Used to free native documents, threads and file descriptors whose owner was never returned.
 *
 * @return the result of [block].
 */
@Suppress("TooGenericExceptionCaught") // Nothing is swallowed: the failure is always rethrown.
internal inline fun <T> releasingOnFailure(release: () -> Unit, block: () -> T): T = try {
    block()
} catch (error: Throwable) {
    try {
        release()
    } catch (releaseError: Throwable) {
        error.addSuppressed(releaseError)
    }
    throw error
}
