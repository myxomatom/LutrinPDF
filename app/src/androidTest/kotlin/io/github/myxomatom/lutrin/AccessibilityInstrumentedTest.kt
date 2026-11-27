// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.myxomatom.lutrin.core.testing.TestFiles
import io.github.myxomatom.lutrin.feature.about.R as AboutR
import io.github.myxomatom.lutrin.feature.library.R as LibraryR
import io.github.myxomatom.lutrin.feature.reader.R as ReaderR
import io.github.myxomatom.lutrin.feature.settings.R as SettingsR
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Accessibility Test Framework checks (F18, `AGENTS.md` §5.1) on the screens of the app, with
 * the real engine and the test documents of `core:testing`: the library with a recent document,
 * the reader, the search (V1), the table of contents (V2), the display options (V6) and the
 * properties (V5), the settings (E3) and the page « À propos » (E4), the dialogs to go to a page
 * (V3), to enter a password (V4) and to confirm an external link (V7), and the error screen (V8).
 *
 * Each test asserts that the screen is shown before checking it, so that a check never runs on a
 * blank page. A check that fails names the view and the rule (touch target, name, contrast...).
 */
@OptIn(ExperimentalTestApi::class) // performCustomAccessibilityActionWithLabel
@RunWith(AndroidJUnit4::class)
class AccessibilityInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun enableChecks() {
        composeRule.enableAccessibilityChecks()
    }

    /** Opens [asset] on its first page (a previous test may have saved another one, ADR 0015). */
    private fun launch(asset: String): ActivityScenario<MainActivity> = ActivityScenario.launch(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(TestDocumentProvider.uriOf(asset), PDF)
            .putExtra(EXTRA_PAGE, 1)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setClass(targetContext, MainActivity::class.java),
    )

    private fun text(id: Int, vararg arguments: Any): String = targetContext.getString(id, *arguments)

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(text).assertExists()
    }

    private fun waitForDescription(description: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Runs the checks on every window of the app: the screen and its dialogs, menus and sheets. */
    private fun checkEveryWindow() {
        composeRule.waitForIdle()
        val roots = composeRule.onAllNodes(isRoot())
        repeat(roots.fetchSemanticsNodes().size) { index -> roots[index].tryPerformAccessibilityChecks() }
    }

    private fun waitForTheDocument(pages: Int) = waitForDescription(text(ReaderR.string.reader_document, 1, pages))

    private fun openMenuEntry(entry: Int) {
        waitForDescription(text(ReaderR.string.reader_more_options))
        composeRule.onNodeWithContentDescription(text(ReaderR.string.reader_more_options)).performClick()
        composeRule.onNodeWithText(text(entry)).performClick()
    }

    @Test
    fun libraryWithARecentDocumentIsAccessible() {
        // Opening a document puts it in the recents; the library then shows it in its resume card.
        launch(TestFiles.Pdf.SIMPLE).use { waitForTheDocument(pages = 1) }

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText(text(LibraryR.string.library_resume))

            checkEveryWindow()
        }
    }

    @Test
    fun readerIsAccessible() {
        launch(TestFiles.Pdf.LINKS).use {
            waitForTheDocument(pages = LINKS_PAGES)

            checkEveryWindow()
        }
    }

    @Test
    fun searchWithResultsIsAccessible() {
        launch(TestFiles.Pdf.MANY_HITS).use {
            waitForDescription(text(ReaderR.string.reader_search_open))
            composeRule.onNodeWithContentDescription(text(ReaderR.string.reader_search_open)).performClick()
            composeRule.onNode(hasSetTextAction()).performTextInput("lutrin")
            composeRule.onNode(hasSetTextAction()).performImeAction()
            waitForText(text(ReaderR.string.reader_search_counter, 1, MANY_HITS))

            checkEveryWindow()
        }
    }

    @Test
    fun tableOfContentsIsAccessible() {
        launch(TestFiles.Pdf.NESTED_OUTLINE).use {
            waitForDescription(text(ReaderR.string.reader_outline_title))
            composeRule.onNodeWithContentDescription(text(ReaderR.string.reader_outline_title)).performClick()
            waitForText(text(ReaderR.string.reader_outline_title))

            checkEveryWindow()
        }
    }

    @Test
    fun displayOptionsAreAccessible() {
        launch(TestFiles.Pdf.SIMPLE).use {
            openMenuEntry(ReaderR.string.reader_options_title)
            waitForText(text(ReaderR.string.reader_options_colors))

            checkEveryWindow()
        }
    }

    @Test
    fun propertiesAreAccessible() {
        launch(TestFiles.Pdf.SIMPLE).use {
            openMenuEntry(ReaderR.string.reader_menu_properties)
            waitForText(text(ReaderR.string.reader_properties_pages))

            checkEveryWindow()
        }
    }

    @Test
    fun goToPageDialogIsAccessible() {
        launch(TestFiles.Pdf.LINKS).use {
            val indicator = text(ReaderR.string.reader_page_slider_value, 1, LINKS_PAGES)
            waitForDescription(indicator)
            composeRule.onNodeWithContentDescription(indicator).performClick()
            waitForText(text(ReaderR.string.reader_goto_label))

            checkEveryWindow()
        }
    }

    @Test
    fun externalLinkDialogIsAccessible() {
        launch(TestFiles.Pdf.LINKS).use {
            val document = text(ReaderR.string.reader_document, 1, LINKS_PAGES)
            val label = text(ReaderR.string.reader_link_action_external, EXTERNAL)
            composeRule.waitUntil(TIMEOUT_MS) {
                val nodes = runCatching {
                    composeRule.onAllNodesWithContentDescription(document).fetchSemanticsNodes()
                }.getOrDefault(emptyList())
                nodes.any { node ->
                    node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == label }
                }
            }
            composeRule.onNodeWithContentDescription(document).performCustomAccessibilityActionWithLabel(label)
            waitForText(text(ReaderR.string.reader_link_title))

            checkEveryWindow()
        }
    }

    @Test
    fun passwordDialogIsAccessible() {
        launch(TestFiles.Pdf.ENCRYPTED).use {
            waitForText(text(ReaderR.string.reader_password_title))

            checkEveryWindow()
        }
    }

    @Test
    fun errorScreenIsAccessible() {
        launch(TestFiles.Pdf.CORRUPTED_GARBAGE).use {
            waitForText(text(ReaderR.string.reader_error_title))

            checkEveryWindow()
        }
    }

    @Test
    fun settingsAreAccessible() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForDescription(text(LibraryR.string.library_settings))
            composeRule.onNodeWithContentDescription(text(LibraryR.string.library_settings)).performClick()
            waitForText(text(SettingsR.string.settings_section_appearance))

            checkEveryWindow()
        }
    }

    @Test
    fun aboutPageIsAccessible() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForDescription(text(LibraryR.string.library_settings))
            composeRule.onNodeWithContentDescription(text(LibraryR.string.library_settings)).performClick()
            waitForText(text(SettingsR.string.settings_about))
            composeRule.onNodeWithText(text(SettingsR.string.settings_about)).performScrollTo().performClick()
            waitForText(text(AboutR.string.about_no_data_title))

            checkEveryWindow()
        }
    }

    private companion object {
        const val PDF = "application/pdf"
        const val LINKS_PAGES = 4
        const val MANY_HITS = 800
        const val EXTERNAL = "https://example.org/"
        const val TIMEOUT_MS = 15_000L
    }
}
