// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.testing.TestFiles
import io.github.myxomatom.lutrin.feature.reader.R as ReaderR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Printing end to end (F15, ADR 0026), with the real engine: the print button opens the print
 * dialog of the system (another app, seen through UiAutomator), and a document whose author
 * forbids printing explains why instead. No printer is needed: the dialog opens with « Save as
 * PDF » or with no printer at all, and the test closes it.
 */
@RunWith(AndroidJUnit4::class)
class PrintInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun launch(asset: String): ActivityScenario<MainActivity> = ActivityScenario.launch(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(TestDocumentProvider.uriOf(asset), PDF)
            .putExtra(EXTRA_PAGE, 1)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setClass(targetContext, MainActivity::class.java),
    )

    private val print: String get() = targetContext.getString(ReaderR.string.reader_print)

    private fun waitForPrintButton() = composeRule.waitUntil(TIMEOUT_MS) {
        composeRule.onAllNodesWithContentDescription(print).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun printButtonOpensThePrintDialogOfTheSystem() {
        launch(TestFiles.Pdf.SIMPLE).use {
            waitForPrintButton()

            composeRule.onNodeWithContentDescription(print).performClick()

            val shown = device.wait(Until.hasObject(By.pkg(PRINT_SPOOLER)), TIMEOUT_MS)
            assertThat(shown).isTrue()
            // Closing the dialog cancels the job: nothing is printed.
            device.pressBack()
            device.wait(Until.gone(By.pkg(PRINT_SPOOLER)), TIMEOUT_MS)
        }
    }

    @Test
    fun aDocumentWhoseAuthorForbidsPrintingExplainsWhy() {
        launch(TestFiles.Pdf.OWNER_PASSWORD_ONLY).use {
            waitForPrintButton()

            composeRule.onNodeWithContentDescription(print).performClick()

            val explanation = targetContext.getString(ReaderR.string.reader_print_forbidden)
            composeRule.waitUntil(TIMEOUT_MS) {
                composeRule.onAllNodesWithText(explanation).fetchSemanticsNodes().isNotEmpty()
            }
            assertThat(device.hasObject(By.pkg(PRINT_SPOOLER))).isFalse()
        }
    }

    private companion object {
        const val PDF = "application/pdf"
        const val TIMEOUT_MS = 15_000L

        /** Package of the print dialog of AOSP, also used by the emulator images. */
        const val PRINT_SPOOLER = "com.android.printspooler"
    }
}
