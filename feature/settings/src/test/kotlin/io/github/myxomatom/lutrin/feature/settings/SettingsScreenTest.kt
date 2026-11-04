// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<Boolean>()
    private var backClicks = 0

    private fun show(state: SettingsUiState, supported: Boolean = true) {
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                SettingsScreen(
                    state = state,
                    dynamicColorSupported = supported,
                    onDynamicColorChange = { changes += it },
                    onBack = { backClicks++ },
                )
            }
        }
    }

    @Test
    fun `shows the dynamic colour switch on by default`() {
        show(SettingsUiState(loading = false))

        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Dynamic colours").assertIsDisplayed()
        composeRule.onNodeWithText("Use the colours of your wallpaper").assertIsDisplayed()
        composeRule.onNodeWithText("Dynamic colours").assertIsOn()
    }

    @Test
    fun `tapping the row turns dynamic colours off`() {
        show(SettingsUiState(loading = false, dynamicColor = true))

        composeRule.onNodeWithText("Dynamic colours").performClick()

        assertThat(changes).containsExactly(false)
    }

    @Test
    fun `tapping the row turns dynamic colours back on`() {
        show(SettingsUiState(loading = false, dynamicColor = false))

        composeRule.onNodeWithText("Dynamic colours").assertIsOff().performClick()

        assertThat(changes).containsExactly(true)
    }

    @Test
    fun `before Android 12 the switch is disabled and explains why`() {
        show(SettingsUiState(loading = false, dynamicColor = true), supported = false)

        composeRule.onNodeWithText("Available from Android 12").assertIsDisplayed()
        composeRule.onNodeWithText("Dynamic colours").assertIsNotEnabled().assertIsOff().performClick()
        assertThat(changes).isEmpty()
    }

    @Test
    fun `shows nothing to change while loading`() {
        show(SettingsUiState(loading = true))

        composeRule.onNodeWithText("Dynamic colours").assertDoesNotExist()
    }

    @Test
    fun `back leaves the settings`() {
        show(SettingsUiState(loading = false))

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backClicks).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `is translated in French`() {
        show(SettingsUiState(loading = false))

        composeRule.onNodeWithText("Paramètres").assertIsDisplayed()
        composeRule.onNodeWithText("Couleurs dynamiques").assertIsDisplayed()
    }
}
