// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot references of the floating bar with its print button (F15, ADR 0026), in place of
 * the fit button of the mockup E2: allowed and forbidden, light and dark, large font, French.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class PrintScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, canPrint: Boolean = true, darkTheme: Boolean = false, fontScale: Float = 1f) {
        val document = FakePdfDocument()
        val state =
            ReaderUiState.Ready(
                "Contrat de location",
                document.pageSizes,
                PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
                canPrint = canPrint,
            )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LutrinTheme(darkTheme = darkTheme, dynamicColor = false) { ReaderScreen(state, onBack = {}) }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun printBar() = capture("print_bar_light")

    @Test
    fun printBarDark() = capture("print_bar_dark", darkTheme = true)

    @Test
    fun printBarLargeFont() = capture("print_bar_light_font200", fontScale = 2f)

    @Test
    @Config(qualifiers = "+fr")
    fun printBarFrench() = capture("print_bar_fr")

    @Test
    fun printForbidden() = capture("print_bar_forbidden_light", canPrint = false)

    @Test
    fun printForbiddenDark() = capture("print_bar_forbidden_dark", canPrint = false, darkTheme = true)
}
