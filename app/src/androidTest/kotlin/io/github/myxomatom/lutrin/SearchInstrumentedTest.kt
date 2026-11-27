// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.myxomatom.lutrin.core.testing.TestFiles
import io.github.myxomatom.lutrin.feature.reader.R as ReaderR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The search end to end (F07, V1, ADR 0023), with the real engine: the 800 occurrences of
 * `search_many_hits.pdf`, and no occurrence in the scanned document, which has no text.
 */
@RunWith(AndroidJUnit4::class)
class SearchInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Opens [asset] on its first page: without the page asked for, the reader would resume the
     * page saved by a previous test (ADR 0015).
     */
    private fun launch(asset: String): ActivityScenario<MainActivity> = ActivityScenario.launch(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(TestDocumentProvider.uriOf(asset), PDF)
            .putExtra(EXTRA_PAGE, 1)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setClass(targetContext, MainActivity::class.java),
    )

    /** Waits for [text] to be on screen, then asserts it: a failure names the text, not a timeout. */
    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }

    /** Opens the search once the document is shown, types [query] and validates it. */
    private fun search(query: String) {
        val label = targetContext.getString(ReaderR.string.reader_search_open)
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(label).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(query)
        composeRule.onNode(hasSetTextAction()).performImeAction()
    }

    @Test
    fun everyOccurrenceOfAPageIsCounted() {
        launch(TestFiles.Pdf.MANY_HITS).use {
            search("lutrin")

            waitForText(targetContext.getString(ReaderR.string.reader_search_counter, 1, MANY_HITS))
            // The count is exact, not « 1 / 800+ »: the search finished without reaching its limit.
            composeRule.onAllNodesWithText(
                targetContext.getString(ReaderR.string.reader_search_counter_limit, 1, MANY_HITS),
            )
                .assertCountEquals(0)
        }
    }

    @Test
    fun aScannedDocumentHasNoResult() {
        launch(TestFiles.Pdf.SCANNED).use {
            search("lutrin")

            waitForText(targetContext.getString(ReaderR.string.reader_search_no_result))
            // No result, so no counter, and the hint says why: the document has no text to search.
            composeRule.onAllNodesWithText(targetContext.getString(ReaderR.string.reader_search_counter, 1, 0))
                .assertCountEquals(0)
            composeRule.onNodeWithText(targetContext.getString(ReaderR.string.reader_search_no_text_hint))
                .assertIsDisplayed()
        }
    }

    private companion object {
        const val PDF = "application/pdf"
        const val MANY_HITS = 800
        const val TIMEOUT_MS = 15_000L
    }
}
