// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot references of the settings screen (E3), light, dark and French, with an empty recent
 * list (« Vider les récents » disabled); and the confirmation of « Vider les récents » (ADR 0016),
 * light, dark, large font and French.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(recentCount: Int, darkTheme: Boolean) {
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) {
                SettingsScreen(
                    state = SettingsUiState(loading = false, dynamicColor = true, recentCount = recentCount),
                    dynamicColorSupported = true,
                    onDynamicColorChange = {},
                    onBack = {},
                )
            }
        }
    }

    private fun capture(name: String, darkTheme: Boolean = false) {
        show(recentCount = 0, darkTheme = darkTheme)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    /** The confirmation dialog of « Vider les récents »; the dialog is a separate window. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureClear(name: String, label: String, darkTheme: Boolean = false) {
        show(recentCount = RECENT_COUNT, darkTheme = darkTheme)
        // In large fonts the row is below the fold: scroll to it, as a user would.
        composeRule.onNodeWithText(label).performScrollTo().performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun light() = capture("settings_light")

    @Test
    fun dark() = capture("settings_dark", darkTheme = true)

    @Test
    @Config(qualifiers = "+fr")
    fun french() = capture("settings_light_fr")

    @Test
    fun clearRecents() = captureClear("settings_clear_recents_light", CLEAR_EN)

    @Test
    fun clearRecentsDark() = captureClear("settings_clear_recents_dark", CLEAR_EN, darkTheme = true)

    // The dialog is another window: the font scale is the configuration's, not LocalDensity's.
    @Test
    @Config(fontScale = 2f)
    fun clearRecentsLargeFont() = captureClear("settings_clear_recents_light_font200", CLEAR_EN)

    @Test
    @Config(qualifiers = "+fr")
    fun clearRecentsFrench() = captureClear("settings_clear_recents_light_fr", CLEAR_FR)

    private companion object {
        const val RECENT_COUNT = 6
        const val CLEAR_EN = "Clear recent documents"
        const val CLEAR_FR = "Vider les récents"
    }
}
