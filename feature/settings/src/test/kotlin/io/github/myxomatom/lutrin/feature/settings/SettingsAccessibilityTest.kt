// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.myxomatom.lutrin.core.domain.settings.AppLanguage
import io.github.myxomatom.lutrin.core.testing.assertControlsAreAccessible
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * E3 against the accessibility rules of `AGENTS.md` §11 (F18): every control has a name for
 * TalkBack and a touch target of 48 dp, at the normal size of the font and at 200 %, and in the
 * confirmation dialog of « Vider les récents ».
 */
@RunWith(AndroidJUnit4::class)
// Tall, so that every row is composed and placed: the audit measures the controls where they are.
@Config(qualifiers = "w360dp-h3000dp-xhdpi")
class SettingsAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(recentCount: Int = 0) {
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                SettingsScreen(
                    state = SettingsUiState(
                        loading = false,
                        recentCount = recentCount,
                        appLanguage = AppLanguage.SYSTEM,
                    ),
                    dynamicColorSupported = true,
                    onDynamicColorChange = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun `every control of the settings is named and large enough`() {
        show()

        composeRule.assertControlsAreAccessible()
    }

    @Test
    @Config(fontScale = 2f)
    fun `every control stays named and large enough at 200 percent`() {
        show()

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the confirmation to clear the recents is accessible`() {
        show(recentCount = RECENT_COUNT)
        composeRule.onNodeWithText("Clear recent documents").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Clear recent documents?").assertExists()
        composeRule.assertControlsAreAccessible()
    }

    private companion object {
        const val RECENT_COUNT = 3
    }
}
