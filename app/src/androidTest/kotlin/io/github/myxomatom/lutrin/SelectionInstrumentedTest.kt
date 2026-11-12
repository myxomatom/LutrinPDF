// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.myxomatom.lutrin.core.testing.TestFiles
import io.github.myxomatom.lutrin.feature.reader.R as ReaderR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The copy of text end to end (F08, ADR 0024), with the real engine and the real clipboard,
 * through « Copier le texte de la page », the TalkBack action of the document: the text of
 * `text_selection.pdf` is copied, the copy of `encrypted_owner_password_only.pdf` is refused.
 */
@RunWith(AndroidJUnit4::class)
class SelectionInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val copyPage = targetContext.getString(ReaderR.string.reader_selection_copy_page_action)
    private val hasCopyPageAction =
        SemanticsMatcher("has the action « $copyPage »") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == copyPage }
        }

    /** Opens [asset] on its first page (a previous test may have saved another one, ADR 0015). */
    private fun launch(asset: String): ActivityScenario<MainActivity> = ActivityScenario.launch(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(TestDocumentProvider.uriOf(asset), PDF)
            .putExtra(EXTRA_PAGE, 1)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setClass(targetContext, MainActivity::class.java),
    )

    private fun copyFirstPage() {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(hasCopyPageAction).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasCopyPageAction).performCustomAccessibilityActionWithLabel(copyPage)
    }

    /** Text on the clipboard, read on the main thread while the app has the focus. */
    private fun clipboardText(): String? {
        var text: String? = null
        instrumentation.runOnMainSync {
            val clipboard = targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        }
        return text
    }

    @Test
    fun theTextOfThePageIsCopied() {
        launch(TestFiles.Pdf.TEXT_SELECTION).use {
            copyFirstPage()

            composeRule.waitUntil(TIMEOUT_MS) { clipboardText().orEmpty().contains(ARABIC_WORD) }
        }
    }

    @Test
    fun theCopyForbiddenByTheAuthorIsRefused() {
        launch(TestFiles.Pdf.OWNER_PASSWORD_ONLY).use {
            copyFirstPage()

            val refused = targetContext.getString(ReaderR.string.reader_selection_copy_forbidden)
            composeRule.waitUntil(TIMEOUT_MS) {
                composeRule.onAllNodesWithText(refused).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private companion object {
        const val PDF = "application/pdf"
        const val TIMEOUT_MS = 15_000L

        /** A word of the Arabic sentence of `text_selection.pdf` (`core/testing/README.md`). */
        const val ARABIC_WORD = "مكتبة"
    }
}
