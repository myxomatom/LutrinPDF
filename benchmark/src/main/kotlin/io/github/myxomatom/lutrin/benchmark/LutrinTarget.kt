// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

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

/**
 * Word searched by the search journeys. Every page of the large test document starts with
 * "Contenu de la page N" then repeats a sentence, so it has well over a thousand hits
 * (`core/testing/scripts/generate_test_files.py`, `large()`).
 */
const val SEARCH_QUERY = "Contenu"

/** Number of "next result" presses of a search journey. */
const val NEXT_RESULT_PRESSES = 10

/** Title of the part of the large document's outline that is jumped to (page 551 of 600). */
private const val FAR_OUTLINE_ENTRY = "Partie 12"

/** Longest wait for the first search result, in milliseconds. */
private const val SEARCH_TIMEOUT_MS = 30_000L

/** Longest wait for a UI element of the reader, in milliseconds. */
private const val UI_TIMEOUT_MS = 5_000L

/** Divisors giving the middle of the width and the first third of the height. */
private const val HALF = 2
private const val THIRD = 3

/** Number of 5 ms steps of the long press that starts a text selection (about one second). */
private const val LONG_PRESS_STEPS = 200

/*
 * UiAutomator sees content descriptions, not Compose test tags (`testTagsAsResourceId` is off),
 * so each pattern accepts the English and the French string of `feature:reader`.
 */
private val SEARCH_OPEN = Pattern.compile("Search|Rechercher")
private val SEARCH_NEXT = Pattern.compile("Next result|Résultat suivant")

/*
 * The counter of the search bar ("1 / 1 000+") is described as "1 000 résultats ou plus" /
 * "1,000 results or more": the digits come first, which tells it from the "Résultat suivant" button.
 */
private val SEARCH_COUNTER = Pattern.compile("\\d.*(results?|résultats?).*")
private val OUTLINE_OPEN = Pattern.compile("Table of contents|Table des matières")

/**
 * Opens the search bar, types [SEARCH_QUERY], submits it and waits for the first result.
 *
 * @throws IllegalStateException if the bar or the first result does not appear.
 */
fun MacrobenchmarkScope.searchFirstResult() {
    checkNotNull(device.wait(Until.findObject(By.desc(SEARCH_OPEN)), UI_TIMEOUT_MS)) {
        "The Search button is missing: is the document open?"
    }.click()
    val field = checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), UI_TIMEOUT_MS)) {
        "The search field did not appear"
    }
    field.text = SEARCH_QUERY
    device.pressEnter()
    checkNotNull(device.wait(Until.findObject(By.desc(SEARCH_COUNTER)), SEARCH_TIMEOUT_MS)) {
        "No search result for \"$SEARCH_QUERY\""
    }
}

/**
 * Waits until the reader has loaded the outline of the open document: the table of contents
 * button only appears then, and the outline of a large document is read in the background.
 *
 * @throws IllegalStateException if the button does not appear within 30 seconds.
 */
fun MacrobenchmarkScope.waitForOutlineButton() {
    checkNotNull(device.wait(Until.findObject(By.desc(OUTLINE_OPEN)), DOCUMENT_TIMEOUT_MS)) {
        "The Table of contents button never appeared: does the document have an outline?"
    }
}

/** Moves to the following search result [NEXT_RESULT_PRESSES] times. */
fun MacrobenchmarkScope.nextSearchResults() {
    repeat(NEXT_RESULT_PRESSES) {
        checkNotNull(device.wait(Until.findObject(By.desc(SEARCH_NEXT)), UI_TIMEOUT_MS)) {
            "The Next result button is missing"
        }.click()
        device.waitForIdle()
    }
}

/**
 * Opens the table of contents drawer and jumps to a part at the end of the document; the
 * drawer closes on its own when an entry is chosen.
 *
 * @throws IllegalStateException if the button or the entry is missing.
 */
fun MacrobenchmarkScope.jumpToFarOutlineEntry() {
    checkNotNull(device.wait(Until.findObject(By.desc(OUTLINE_OPEN)), UI_TIMEOUT_MS)) {
        "The Table of contents button is missing: is the document open?"
    }.click()
    checkNotNull(device.wait(Until.findObject(By.descStartsWith(FAR_OUTLINE_ENTRY)), UI_TIMEOUT_MS)) {
        "The outline entry \"$FAR_OUTLINE_ENTRY\" is missing"
    }.click()
    device.waitForIdle()
}

/** Long-presses a point where the text of a page lies, to start a selection. */
fun MacrobenchmarkScope.longPressText() {
    val x = device.displayWidth / HALF
    val y = device.displayHeight / THIRD
    device.swipe(x, y, x, y, LONG_PRESS_STEPS)
    device.waitForIdle()
}
