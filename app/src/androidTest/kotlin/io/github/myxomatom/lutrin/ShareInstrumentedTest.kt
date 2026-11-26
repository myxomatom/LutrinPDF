// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasFlag
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
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
 * Sharing and properties end to end (F15, F16, ADR 0027), on `simple_1page.pdf` opened by the real
 * engine through the content URI of the test provider: the intent of « Partager » is the one the
 * decision describes (the document as it is, not a copy), stubbed by Espresso Intents so that no
 * chooser opens; and the properties sheet shows what the engine read.
 */
@RunWith(AndroidJUnit4::class)
class ShareInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = Intents.init()

    @After
    fun tearDown() = Intents.release()

    private fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(TestDocumentProvider.uriOf(TestFiles.Pdf.SIMPLE), PDF)
            .putExtra(EXTRA_PAGE, 1)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setClass(targetContext, MainActivity::class.java),
    )

    private fun string(id: Int): String = targetContext.getString(id)

    /** Opens the « More options » menu once the document is shown. */
    private fun openMenu() {
        val label = string(ReaderR.string.reader_more_options)
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(label).performClick()
    }

    @Test
    fun shareHandsTheDocumentAsItIsToTheChooserOfTheSystem() {
        val chooser = hasAction(Intent.ACTION_CHOOSER)
        intending(chooser).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        launch().use {
            openMenu()

            composeRule.onNodeWithText(string(ReaderR.string.reader_menu_share)).performClick()

            composeRule.waitForIdle()
            val sent =
                allOf(
                    hasAction(Intent.ACTION_SEND),
                    hasType(PDF),
                    hasExtra(Intent.EXTRA_STREAM, TestDocumentProvider.uriOf(TestFiles.Pdf.SIMPLE)),
                    hasFlag(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            intended(allOf(chooser, hasExtra(Intent.EXTRA_INTENT, sent)))
        }
    }

    @Test
    fun propertiesShowWhatTheEngineReadAndOfferShareAndPrint() {
        launch().use {
            openMenu()

            composeRule.onNodeWithText(string(ReaderR.string.reader_menu_properties)).performClick()

            composeRule.waitUntil(TIMEOUT_MS) {
                composeRule.onAllNodesWithText(string(ReaderR.string.reader_properties_version))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Lutrin test suite").assertExists()
            composeRule.onNodeWithText("1.7").assertExists()
            composeRule.onNodeWithText(string(ReaderR.string.reader_share)).assertExists()
            composeRule.onNodeWithText(string(ReaderR.string.reader_print)).assertExists()
        }
    }

    private companion object {
        const val PDF = "application/pdf"
        const val TIMEOUT_MS = 15_000L
    }
}
