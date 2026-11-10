// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot references of the password dialog (V4, ADR 0017): empty, and after a wrong password
 * with the typed text kept and selected; light, dark, font at 200 % and French. The dialog is
 * another window: the large font comes from the configuration, not from `LocalDensity`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class PasswordDialogScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val asked = ReaderUiState.PasswordRequired("Relevé de compte")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, incorrect: Boolean = false, darkTheme: Boolean = false) {
        var state by mutableStateOf(asked)
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) { ReaderScreen(state, onBack = {}) }
        }
        if (incorrect) {
            // Ten characters, like the mock-up; then the reader answers « incorrect ».
            composeRule.onNode(hasSetTextAction()).performTextInput("motdepasse")
            state = asked.copy(isIncorrect = true)
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun light() = capture("reader_password_light")

    @Test
    fun dark() = capture("reader_password_dark", darkTheme = true)

    @Test
    @Config(fontScale = 2f)
    fun largeFont() = capture("reader_password_light_font200")

    @Test
    @Config(qualifiers = "+fr")
    fun french() = capture("reader_password_light_fr")

    @Test
    fun incorrect() = capture("reader_password_error_light", incorrect = true)

    @Test
    fun incorrectDark() = capture("reader_password_error_dark", incorrect = true, darkTheme = true)

    @Test
    @Config(fontScale = 2f)
    fun incorrectLargeFont() = capture("reader_password_error_light_font200", incorrect = true)

    @Test
    @Config(qualifiers = "+fr")
    fun incorrectFrench() = capture("reader_password_error_light_fr", incorrect = true)
}
