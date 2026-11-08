// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.testing.TestFiles
import io.github.myxomatom.lutrin.feature.reader.R as ReaderR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * « Ouvrir avec » and « Partager » end to end (F01, ADR 0014): the real activity, intent filters
 * and MuPDF engine, on documents served by [TestDocumentProvider]. No network needed.
 */
@RunWith(AndroidJUnit4::class)
class OpenWithInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun view(asset: String): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(TestDocumentProvider.uriOf(asset), PDF)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun send(asset: String): Intent = Intent(Intent.ACTION_SEND)
        .setType(PDF)
        .putExtra(Intent.EXTRA_STREAM, TestDocumentProvider.uriOf(asset))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun launch(intent: Intent): ActivityScenario<MainActivity> =
        ActivityScenario.launch(intent.setClass(targetContext, MainActivity::class.java))

    private fun waitForDescription(description: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            hasNodes { composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes() }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) { hasNodes { composeRule.onAllNodesWithText(text).fetchSemanticsNodes() } }
    }

    /** False while the activity has no Compose hierarchy yet (the lookup then throws). */
    private fun hasNodes(fetch: () -> List<*>): Boolean = try {
        fetch().isNotEmpty()
    } catch (@Suppress("SwallowedException") _: IllegalStateException) {
        false
    }

    @Test
    fun openWithAndShareOfferLutrinForPdf() {
        val packageManager = targetContext.packageManager
        listOf(view(TestFiles.Pdf.SIMPLE), send(TestFiles.Pdf.SIMPLE)).forEach { intent ->
            val handlers = packageManager.queryIntentActivities(intent.setPackage(targetContext.packageName), 0)

            assertThat(handlers.map { it.activityInfo.name }).containsExactly(MainActivity::class.java.name)
        }
    }

    @Test
    fun openWithShowsTheDocumentAtTheRequestedPage() {
        launch(view(TestFiles.Pdf.NESTED_OUTLINE).putExtra(EXTRA_PAGE, 3)).use {
            waitForDescription(targetContext.getString(ReaderR.string.reader_document, 3, NESTED_OUTLINE_PAGES))
        }
    }

    @Test
    fun shareShowsTheDocument() {
        launch(send(TestFiles.Pdf.SIMPLE)).use {
            waitForDescription(targetContext.getString(ReaderR.string.reader_document, 1, 1))
        }
    }

    @Test
    fun aFileThatIsNotAPdfShowsTheErrorScreen() {
        launch(view(TestFiles.Pdf.NOT_A_PDF)).use {
            waitForText(targetContext.getString(ReaderR.string.reader_error_unsupported))
        }
    }

    @Test
    fun aMissingDocumentShowsTheErrorScreen() {
        launch(view("pdfs/does_not_exist.pdf")).use {
            waitForText(targetContext.getString(ReaderR.string.reader_error_not_accessible))
        }
    }

    private companion object {
        const val PDF = "application/pdf"
        const val NESTED_OUTLINE_PAGES = 6
        const val TIMEOUT_MS = 15_000L
    }
}
