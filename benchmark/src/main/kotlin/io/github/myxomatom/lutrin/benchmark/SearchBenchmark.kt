// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing of the search (F07, ADR 0023) in the large test document (600 pages): ten "next
 * result" presses, then scrolling while the matches are highlighted. Opening the bar and the
 * search itself are in the setup, so that the metric covers navigation and highlighting only.
 */
@RunWith(AndroidJUnit4::class)
class SearchBenchmark {
    /** Runs and measures the app. */
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Ten "next result" presses, then a fling over the highlighted pages. */
    @Test
    fun nextResultsThenScroll() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = ITERATIONS,
        setupBlock = {
            // A fresh process each time: the previous iteration left a search open.
            killProcess()
            pressHome()
            openLargeDocument(ReadingMode.CONTINUOUS)
            waitForPages()
            searchFirstResult()
        },
    ) {
        nextSearchResults()
        flingPages(waitForPages())
    }

    private companion object {
        const val ITERATIONS = 5
    }
}
