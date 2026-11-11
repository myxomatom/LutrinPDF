// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** « Aller à la page » (V3, F05): opened by the page indicator of the floating bar. */
@RunWith(AndroidJUnit4::class)
class GoToPageDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun ready(scrollMode: ScrollMode = ScrollMode.CONTINUOUS, pageCount: Int = PAGES): ReaderUiState.Ready {
        val sizes = List(pageCount) { A4 }
        return ReaderUiState.Ready(
            title = "Contrat de location",
            pageSizes = sizes,
            renderer = PageRenderer(FakePdfDocument(pageSizes = sizes), sizes, Dispatchers.Unconfined),
            scrollMode = scrollMode,
        )
    }

    private fun show(initial: ReaderUiState.Ready) {
        var state by mutableStateOf(initial)
        composeRule.setContent { Connected(state) { state = it } }
    }

    /** The reader wired like the ViewModel: the reported page comes back in the state. */
    @Composable
    private fun Connected(state: ReaderUiState.Ready, onState: (ReaderUiState.Ready) -> Unit) {
        LutrinTheme(dynamicColor = false) {
            ReaderScreen(
                state = state,
                onBack = {},
                onPageChange = { onState(state.copy(currentPage = it)) },
                onScrollModeChange = { onState(state.copy(scrollMode = it)) },
            )
        }
    }

    private val indicator get() = composeRule.onNodeWithContentDescription("Page 1 of $PAGES")
    private val field get() = composeRule.onNode(hasSetTextAction())
    private val goButton get() = composeRule.onNodeWithText("Go")

    private fun openDialog() {
        indicator.performClick()
        composeRule.waitForIdle()
    }

    private fun assertDocumentOn(page: Int) {
        composeRule.onNodeWithContentDescription("Document, page $page of $PAGES").assertIsDisplayed()
    }

    @Test
    fun `tapping the page indicator opens the dialog on the current page, in focus`() {
        show(ready())

        openDialog()

        composeRule.onNode(isDialog()).assertIsDisplayed()
        composeRule.onNodeWithText("Go to page").assertIsDisplayed()
        composeRule.onNodeWithText("Page number").assertIsDisplayed()
        composeRule.onNodeWithText("Between 1 and $PAGES").assertIsDisplayed()
        field.assertTypedText("1")
        field.assertIsFocused()
        goButton.assertIsEnabled()
    }

    @Test
    fun `the page indicator is a button with a spoken action and a 48 dp target`() {
        show(ready())

        indicator.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        indicator.assertHasClickAction()
        indicator.assertHeightIsAtLeast(48.dp)
        indicator.assertWidthIsAtLeast(48.dp)
        indicator.assert(
            SemanticsMatcher("has the click label Go to a page") {
                SemanticsActions.OnClick in it.config && it.config[SemanticsActions.OnClick].label == "Go to a page"
            },
        )
        composeRule.onNodeWithText("1 / $PAGES").assertIsDisplayed()
    }

    @Test
    fun `typing a page and tapping Go shows it, continuous scroll`() {
        show(ready())
        openDialog()

        field.performTextReplacement("5")
        goButton.performClick()
        composeRule.waitForIdle()

        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithText("5 / $PAGES").assertIsDisplayed()
        assertDocumentOn(page = 5)
    }

    @Test
    fun `typing a page and tapping Go shows it, page by page`() {
        show(ready(ScrollMode.PAGED))
        openDialog()

        field.performTextReplacement("7")
        goButton.performClick()
        composeRule.waitForIdle()

        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithText("7 / $PAGES").assertIsDisplayed()
        assertDocumentOn(page = 7)
    }

    @Test
    fun `the keyboard action goes to the page`() {
        show(ready())
        openDialog()

        field.performTextReplacement("3")
        field.performImeAction()
        composeRule.waitForIdle()

        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertDocumentOn(page = 3)
    }

    @Test
    fun `an empty field disables Go, says so and announces it`() {
        show(ready())
        openDialog()

        field.performTextClearance()

        goButton.assertIsNotEnabled()
        composeRule.onNodeWithText("Enter a page number").assertIsDisplayed()
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        composeRule.onNode(
            hasText("Enter a page number") and
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `zero disables Go and asks for a page between 1 and the last`() {
        show(ready())
        openDialog()

        field.performTextReplacement("0")

        goButton.assertIsNotEnabled()
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        composeRule.onNode(
            hasText("Between 1 and $PAGES") and
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `a page past the last disables Go and the keyboard action does nothing`() {
        show(ready())
        openDialog()

        field.performTextReplacement("${PAGES + 1}")
        field.performImeAction()
        composeRule.waitForIdle()

        goButton.assertIsNotEnabled()
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        composeRule.onNode(isDialog()).assertIsDisplayed()
        composeRule.onNodeWithText("1 / $PAGES").assertIsDisplayed()
    }

    @Test
    fun `the last page is accepted and a valid page has no error`() {
        show(ready())
        openDialog()

        field.performTextReplacement("$PAGES")

        goButton.assertIsEnabled()
        field.assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }

    @Test
    fun `only digits are accepted`() {
        show(ready())
        openDialog()

        field.performTextReplacement("")
        field.performTextInput("4a-b")

        field.assertTypedText("4")
    }

    @Test
    fun `Cancel closes the dialog and leaves the document where it is`() {
        show(ready())
        openDialog()
        field.performTextReplacement("4")

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertDocumentOn(page = 1)
    }

    @Test
    fun `the back gesture hides the keyboard then closes the dialog`() {
        show(ready())
        openDialog()

        // The keyboard is open on the focused field: the first back hides it, as on a device.
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNode(isDialog()).assertIsDisplayed()
        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertDocumentOn(page = 1)
    }

    @Test
    fun `a document of one page offers page 1 only`() {
        show(ready(pageCount = 1))

        composeRule.onNodeWithContentDescription("Page 1 of 1").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Between 1 and 1").assertIsDisplayed()
        goButton.assertIsEnabled()
        field.performTextReplacement("2")
        goButton.assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the dialog is translated into French`() {
        show(ready())

        composeRule.onNodeWithContentDescription("Page 1 sur $PAGES").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Aller à la page").assertIsDisplayed()
        composeRule.onNodeWithText("Numéro de page").assertIsDisplayed()
        composeRule.onNodeWithText("Entre 1 et $PAGES").assertIsDisplayed()
        composeRule.onNodeWithText("Annuler").assertIsDisplayed()
        field.performTextClearance()
        composeRule.onNodeWithText("Saisissez un numéro de page").assertIsDisplayed()
    }

    private companion object {
        const val PAGES = 8
    }
}
