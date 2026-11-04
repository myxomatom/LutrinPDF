// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot references of the settings screen (E3), light, dark and French. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, darkTheme: Boolean = false) {
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) {
                SettingsScreen(
                    state = SettingsUiState(loading = false, dynamicColor = true),
                    dynamicColorSupported = true,
                    onDynamicColorChange = {},
                    onBack = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun light() = capture("settings_light")

    @Test
    fun dark() = capture("settings_dark", darkTheme = true)

    @Test
    @Config(qualifiers = "+fr")
    fun french() = capture("settings_light_fr")
}
