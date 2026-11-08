// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Time to open the large test document (600 pages) from a cold start, up to its first pages
 * rendered.
 *
 * Read `timeToFullDisplayMs`: the reader reports the activity fully drawn once the pages
 * first shown have their rendering (`PdfPage`, ADR 0012). `timeToInitialDisplayMs` only
 * covers the first frame, before the document is read.
 */
@RunWith(AndroidJUnit4::class)
class OpenDocumentBenchmark {
    /** Runs and measures the app. */
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Cold start of the reader on the large document, in continuous mode. */
    @Test
    fun openLargeDocumentCold() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = {
            // Stores the continuous mode (and copies the document) before the process is killed.
            openLargeDocument(ReadingMode.CONTINUOUS)
            waitForPages()
        },
    ) {
        openLargeDocument(mode = null)
        waitForPages()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
