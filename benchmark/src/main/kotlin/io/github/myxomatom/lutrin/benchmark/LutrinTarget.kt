// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** Application id of the app under test (the benchmark builds have no suffix). */
const val TARGET_PACKAGE = "io.github.myxomatom.lutrin"

/**
 * Entry point of the app's benchmark builds that opens the large test document in the
 * reader (`BenchmarkReaderActivity`, ADR 0012). Absent from the release build.
 */
private const val READER_ACTIVITY = "$TARGET_PACKAGE.BenchmarkReaderActivity"

/** Extra of [READER_ACTIVITY]: the scroll mode to store before opening the document. */
private const val EXTRA_SCROLL_MODE = "scrollMode"

/** Longest wait for the document to appear, in milliseconds. */
private const val DOCUMENT_TIMEOUT_MS = 30_000L

/** Number of flings or swipes of one scrolling journey. */
private const val GESTURES = 8

/** Share of the screen width left untouched on each side of a gesture (navigation gestures). */
private const val GESTURE_MARGIN_DIVISOR = 5

/**
 * Share of the screen height left untouched at the bottom of a gesture: the floating bar of the
 * reader (ADR 0018) lies over the pages there, and a fling started on it would move the slider.
 */
private const val BOTTOM_MARGIN_DIVISOR = 4

/** Share of the page width covered by a page-by-page swipe. */
private const val SWIPE_PERCENT = 0.8f

/**
 * Reading modes of the app (`ScrollMode` of `core:domain`, mirrored by name since this module
 * cannot depend on the app's code).
 */
enum class ReadingMode {
    /** Pages one below the other. */
    CONTINUOUS,

    /** One page per screen, horizontal swipes. */
    PAGED,
}

/**
 * Starts the app on the large test document (600 pages, `core:testing`) and waits for its
 * launch to complete. The document is shown once [waitForPages] returns.
 *
 * @param mode reading mode stored before the document opens; `null` keeps the stored one.
 */
fun MacrobenchmarkScope.openLargeDocument(mode: ReadingMode?) {
    startActivityAndWait { intent ->
        intent.setClassName(TARGET_PACKAGE, READER_ACTIVITY)
        mode?.let { intent.putExtra(EXTRA_SCROLL_MODE, it.name) }
    }
}

/**
 * Waits until the pages of the open document are on screen.
 *
 * @return the scrollable area holding the pages.
 * @throws IllegalStateException if the document does not appear within 30 seconds.
 */
fun MacrobenchmarkScope.waitForPages(): UiObject2 =
    checkNotNull(device.wait(Until.findObject(By.scrollable(true)), DOCUMENT_TIMEOUT_MS)) {
        "The document did not open: is the benchmark build of the app installed?"
    }

/** Flings the pages of the continuous mode down, several times, above the floating bar. */
fun MacrobenchmarkScope.flingPages(pages: UiObject2) {
    setReadingGestureMargins(pages)
    repeat(GESTURES) {
        pages.fling(Direction.DOWN)
        device.waitForIdle()
    }
}

/** Swipes to the following pages in page-by-page mode, several times, above the floating bar. */
fun MacrobenchmarkScope.swipePages(pages: UiObject2) {
    setReadingGestureMargins(pages)
    repeat(GESTURES) {
        // Pages always run left to right (ADR 0011): swiping left shows the next one.
        pages.swipe(Direction.LEFT, SWIPE_PERCENT)
        device.waitForIdle()
    }
}

/** Keeps gestures off the screen edges (system gestures) and off the floating bar at the bottom. */
private fun MacrobenchmarkScope.setReadingGestureMargins(pages: UiObject2) {
    val margin = device.displayWidth / GESTURE_MARGIN_DIVISOR
    pages.setGestureMargins(margin, margin, margin, device.displayHeight / BOTTOM_MARGIN_DIVISOR)
}
