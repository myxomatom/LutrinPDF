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
 * Start-up time of the app on the library (E1), the launcher entry point.
 *
 * Run on a device: `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest`.
 * [coldStartupWithoutProfile] against [coldStartup] shows what the Baseline Profile brings.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    /** Runs and measures the app. */
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Process started from scratch, code compiled as installed (with the Baseline Profile if any). */
    @Test
    fun coldStartup() = startup(StartupMode.COLD, CompilationMode.DEFAULT)

    /** Process started from scratch, nothing compiled ahead of time. */
    @Test
    fun coldStartupWithoutProfile() = startup(StartupMode.COLD, CompilationMode.None())

    /** Process alive, activity created again. */
    @Test
    fun warmStartup() = startup(StartupMode.WARM, CompilationMode.DEFAULT)

    private fun startup(mode: StartupMode, compilation: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = mode,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
