// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import android.util.Log
import javax.inject.Inject

/**
 * Receives the failures of releasing a document's native resources. Nobody waits for
 * [io.github.myxomatom.lutrin.core.pdf.PdfDocument.close]: thrown on the document thread, such a
 * failure would be uncaught and crash the app, while the document is already gone for the user.
 */
internal fun interface ReleaseFailureReporter {
    /**
     * Reports one failed release step; the other steps still run.
     *
     * @param error what the step threw.
     */
    fun report(error: Exception)
}

/**
 * Runs one release step, reporting its failure instead of letting it escape: uncaught on the
 * document thread, it would crash the app. Errors such as [OutOfMemoryError] are not caught.
 */
@Suppress("TooGenericExceptionCaught") // MuPDF throws plain RuntimeException; close() also throws IOException.
internal inline fun ReleaseFailureReporter.reporting(step: () -> Unit) {
    try {
        step()
    } catch (error: Exception) {
        report(error)
    }
}

/**
 * Writes release failures to the system log as a warning. Only the exception type is logged:
 * MuPDF messages may quote document data or file paths (`AGENTS.md` §1.8).
 */
internal class LogReleaseFailureReporter @Inject constructor() : ReleaseFailureReporter {
    override fun report(error: Exception) {
        Log.w(TAG, "Failed to release a document's native resources: ${error.javaClass.name}")
    }

    private companion object {
        const val TAG = "Lutrin"
    }
}
