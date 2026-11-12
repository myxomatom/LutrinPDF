// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasCategories
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.myxomatom.lutrin.core.testing.TestFiles
import io.github.myxomatom.lutrin.feature.reader.R as ReaderR
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Links end to end (F09, V7, ADR 0022), on `links_internal_external.pdf` opened by the real
 * engine: an internal link through its TalkBack action, and the intent of a confirmed external
 * link, stubbed by Espresso Intents so that no browser starts (the test needs no network).
 */
@OptIn(ExperimentalTestApi::class) // performCustomAccessibilityActionWithLabel
@RunWith(AndroidJUnit4::class)
class LinksInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = Intents.init()

    @After
    fun tearDown() = Intents.release()

    /**
     * Opens the document on its first page. The page asked for is required: without it, the
     * reader resumes the page saved by a previous test (page 3 after the internal link, ADR 0015).
     */
    private fun launchLinks(): ActivityScenario<MainActivity> = ActivityScenario.launch(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(TestDocumentProvider.uriOf(TestFiles.Pdf.LINKS), PDF)
            .putExtra(EXTRA_PAGE, 1)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setClass(targetContext, MainActivity::class.java),
    )

    private fun document(page: Int): String = targetContext.getString(ReaderR.string.reader_document, page, PAGES)

    /** Waits for the document area of [page] to offer the link action [label] (links are read on demand). */
    private fun waitForLinkAction(page: Int, label: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            val nodes = runCatching {
                composeRule.onAllNodesWithContentDescription(document(page)).fetchSemanticsNodes()
            }.getOrDefault(emptyList())
            nodes.any { node ->
                node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == label }
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun internalLinkActionShowsItsPage() {
        launchLinks().use {
            val label = targetContext.getString(ReaderR.string.reader_link_action_page, 3)
            waitForLinkAction(1, label)

            composeRule.onNodeWithContentDescription(document(1)).performCustomAccessibilityActionWithLabel(label)

            composeRule.waitUntil(TIMEOUT_MS) {
                composeRule.onAllNodesWithContentDescription(document(3)).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun confirmedExternalLinkIsHandedToABrowsableActivity() {
        val matcher = allOf(hasAction(Intent.ACTION_VIEW), hasData(EXTERNAL.toUri()), hasCategories(setOf(BROWSABLE)))
        intending(matcher).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        launchLinks().use {
            val label = targetContext.getString(ReaderR.string.reader_link_action_external, EXTERNAL)
            waitForLinkAction(1, label)

            composeRule.onNodeWithContentDescription(document(1)).performCustomAccessibilityActionWithLabel(label)
            waitForText(EXTERNAL)
            composeRule.onNodeWithText(targetContext.getString(ReaderR.string.reader_link_open)).performClick()

            composeRule.waitForIdle()
            intended(matcher)
        }
    }

    private companion object {
        const val PDF = "application/pdf"
        const val PAGES = 4
        const val EXTERNAL = "https://example.org/"
        const val BROWSABLE = Intent.CATEGORY_BROWSABLE
        const val TIMEOUT_MS = 15_000L
    }
}
