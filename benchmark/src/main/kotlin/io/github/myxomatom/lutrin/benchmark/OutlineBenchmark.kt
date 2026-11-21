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
 * Frame timing of the table of contents (F06, ADR 0021): opening the drawer of the large test
 * document (12 parts, 60 chapters) and jumping to a distant part.
 */
@RunWith(AndroidJUnit4::class)
class OutlineBenchmark {
    /** Runs and measures the app. */
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Opens the drawer, then jumps to the last part. */
    @Test
    fun openAndJump() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = ITERATIONS,
        setupBlock = {
            // A fresh process each time: the jump of the previous iteration left the document far away.
            killProcess()
            pressHome()
            openLargeDocument(ReadingMode.CONTINUOUS)
            waitForPages()
            waitForOutlineButton()
        },
    ) {
        jumpToFarOutlineEntry()
    }

    private companion object {
        const val ITERATIONS = 5
    }
}
