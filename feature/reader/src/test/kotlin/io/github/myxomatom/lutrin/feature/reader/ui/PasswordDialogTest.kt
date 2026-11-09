// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The password dialog (V4, F10, ADR 0017), shown by the reader for an encrypted document. */
@RunWith(AndroidJUnit4::class)
class PasswordDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val submitted = mutableListOf<String>()
    private var backs = 0
    private var state by mutableStateOf(ReaderUiState.PasswordRequired("Relevé de compte"))

    private val field get() = composeRule.onNode(hasSetTextAction())

    /** The text drawn in the field, as laid out: dots while the password is masked. */
    private fun drawnText(): String {
        val layouts = mutableListOf<TextLayoutResult>()
        field.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first().layoutInput.text.text
    }

    private fun show() {
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                ReaderScreen(
                    state = state,
                    onBack = { backs++ },
                    onPasswordSubmit = { submitted += it.toString() },
                )
            }
        }
    }

    @Test
    fun `asks for the password of the named document`() {
        show()

        composeRule.onNode(isDialog()).assertIsDisplayed()
        composeRule.onNodeWithText("Protected document").assertIsDisplayed()
        composeRule.onNodeWithText(
            "“Relevé de compte” is encrypted. Enter its password to open it.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("The password is never saved.").assertIsDisplayed()
        field.assertIsFocused()
    }

    @Test
    fun `the field is a masked password field without suggestions`() {
        show()

        field.performTextInput("lutrin")

        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertThat(drawnText()).doesNotContain("lutrin")
        assertThat(drawnText()).hasLength("lutrin".length)
        val options = PasswordDialogDefaults.KeyboardOptions
        assertThat(options.keyboardType).isEqualTo(KeyboardType.Password)
        assertThat(options.autoCorrectEnabled).isFalse()
        assertThat(options.imeAction).isEqualTo(ImeAction.Done)
    }

    @Test
    fun `the eye shows then hides the password`() {
        show()
        field.performTextInput("lutrin")

        composeRule.onNodeWithContentDescription("Show password").performClick()
        assertThat(drawnText()).isEqualTo("lutrin")

        composeRule.onNodeWithContentDescription("Hide password").performClick()
        assertThat(drawnText()).doesNotContain("lutrin")
    }

    @Test
    fun `the eye is a 48 dp touch target`() {
        show()

        composeRule.onNodeWithContentDescription("Show password").assertTouchWidthIsEqualTo(48.dp)
        composeRule.onNodeWithContentDescription("Show password").assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun `Open is enabled once something is typed, and submits it`() {
        show()
        composeRule.onNodeWithText("Open").assertIsNotEnabled()

        field.performTextInput("lutrin")
        composeRule.onNodeWithText("Open").assertIsEnabled().performClick()

        assertThat(submitted).containsExactly("lutrin")
    }

    @Test
    fun `the keyboard action submits the password`() {
        show()
        field.performTextInput("lutrin")

        field.performImeAction()

        assertThat(submitted).containsExactly("lutrin")
    }

    @Test
    fun `the keyboard action does nothing while the field is empty`() {
        show()

        field.performImeAction()

        assertThat(submitted).isEmpty()
    }

    @Test
    fun `nothing is submitted while a password is checked`() {
        state = state.copy(isChecking = true)
        show()
        field.performTextInput("lutrin")

        field.performImeAction()

        composeRule.onNodeWithText("Open").assertIsNotEnabled()
        assertThat(submitted).isEmpty()
    }

    @Test
    fun `a wrong password shows an error and keeps the text, selected`() {
        show()
        field.performTextInput("wrong")
        field.performImeAction()

        state = state.copy(isIncorrect = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Incorrect password. Try again.").assertIsDisplayed()
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, "wrong".length)))
        field.assertIsFocused()
    }

    @Test
    fun `the error is announced by screen readers`() {
        state = state.copy(isIncorrect = true)
        show()

        composeRule
            .onNodeWithText("Incorrect password. Try again.")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `the error is hidden while the next password is checked`() {
        state = state.copy(isIncorrect = true, isChecking = true)
        show()

        composeRule.onNodeWithText("Incorrect password. Try again.").assertDoesNotExist()
    }

    @Test
    fun `a new attempt after a wrong password submits the new text`() {
        state = state.copy(isIncorrect = true)
        show()
        field.performTextInput("lutrin")

        composeRule.onNodeWithText("Open").performClick()

        assertThat(submitted).containsExactly("lutrin")
    }

    @Test
    fun `Cancel leaves the reader like Back`() {
        show()

        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(backs).isEqualTo(1)
        assertThat(submitted).isEmpty()
    }

    @Test
    fun `the back gesture leaves the reader`() {
        show()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun `the back arrow behind the dialog is the same way out`() {
        show()

        composeRule.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun `a dialog shown again starts empty, as after a rotation`() {
        var asked by mutableStateOf(true)
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                ReaderScreen(state = if (asked) state else ReaderUiState.Loading, onBack = {})
            }
        }
        field.performTextInput("lutrin")

        asked = false
        composeRule.waitForIdle()
        asked = true
        composeRule.waitForIdle()

        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange.Zero))
        composeRule.onNodeWithText("Open").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the dialog is translated into French`() {
        show()

        composeRule.onNodeWithText("Document protégé").assertIsDisplayed()
        composeRule
            .onNodeWithText("« Relevé de compte » est chiffré. Saisissez son mot de passe pour l’ouvrir.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Mot de passe").assertExists()
        composeRule.onNodeWithText("Le mot de passe n’est jamais enregistré.").assertIsDisplayed()
        composeRule.onNodeWithText("Annuler").assertIsDisplayed()
        composeRule.onNodeWithText("Ouvrir").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Afficher le mot de passe").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the French error is the one of the mock-up`() {
        state = state.copy(isIncorrect = true)
        show()

        composeRule.onNodeWithText("Mot de passe incorrect. Réessayez.").assertIsDisplayed()
    }
}
