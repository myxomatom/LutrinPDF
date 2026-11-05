// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile from the main journeys: start-up on the library, then
 * reading the large document in both modes. Needs a device on Android 13+ (or a rooted
 * Android 9+). Run with `./gradlew :app:generateBaselineProfile` (ADR 0012); the profile is
 * written under `app/src/release/generated/baselineProfiles/` and must be committed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    /** Collects the classes and methods used during each journey. */
    @get:Rule
    val rule = BaselineProfileRule()

    /** Start-up: also written to the startup profile, which orders the DEX files. */
    @Test
    fun startup() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
    }

    /** Opening and reading a document, in continuous and in page-by-page mode. */
    @Test
    fun reading() = rule.collect(packageName = TARGET_PACKAGE) {
        pressHome()
        openLargeDocument(ReadingMode.PAGED)
        swipePages(waitForPages())
        pressHome()
        // Ends on the default mode, so that the benchmarks that follow start from it.
        openLargeDocument(ReadingMode.CONTINUOUS)
        flingPages(waitForPages())
    }
}
