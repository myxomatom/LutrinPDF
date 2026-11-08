// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiObject2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while reading the large test document (600 pages). M2 asks for 60 frames per
 * second (`ROADMAP.md`): `frameDurationCpuMs` P90 under 16 ms and few `frameOverrunMs` above 0.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    /** Runs and measures the app. */
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Flings down the continuous scroll (F03). */
    @Test
    fun continuousScroll() = measureReading(ReadingMode.CONTINUOUS) { flingPages(it) }

    /** Swipes from page to page (F03, ADR 0011). */
    @Test
    fun pageByPage() = measureReading(ReadingMode.PAGED) { swipePages(it) }

    private fun measureReading(mode: ReadingMode, gestures: MacrobenchmarkScope.(UiObject2) -> Unit) =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                openLargeDocument(mode)
            },
        ) {
            gestures(waitForPages())
        }

    private companion object {
        const val ITERATIONS = 5
    }
}
