// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot references of the welcome screen (light, dark, large font, French). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class LutrinAppScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, darkTheme: Boolean, fontScale: Float = 1f) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LutrinTheme(darkTheme = darkTheme, dynamicColor = false) { WelcomeScreen(onOpenDocument = {}) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun light() = capture("welcome_light", darkTheme = false)

    @Test
    fun dark() = capture("welcome_dark", darkTheme = true)

    @Test
    fun largeFont() = capture("welcome_light_font200", darkTheme = false, fontScale = 2f)

    @Test
    @Config(qualifiers = "+fr")
    fun french() = capture("welcome_light_fr", darkTheme = false)
}
