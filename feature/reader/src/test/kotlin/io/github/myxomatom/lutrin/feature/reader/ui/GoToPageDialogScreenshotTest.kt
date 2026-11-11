// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot references of « Aller à la page » (V3): on page 5 of 8 like the mock-up, and with
 * the error of a page past the last; light, dark, font at 200 % and French. The dialog is
 * another window: the large font comes from the configuration, not from `LocalDensity`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class GoToPageDialogScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, typed: String? = null, darkTheme: Boolean = false, indicator: String) {
        val sizes = List(PAGES) { A4 }
        val state =
            ReaderUiState.Ready(
                title = "Contrat de location",
                pageSizes = sizes,
                renderer = PageRenderer(FakePdfDocument(pageSizes = sizes), sizes, Dispatchers.Unconfined),
                currentPage = CURRENT_PAGE,
            )
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) { ReaderScreen(state, onBack = {}) }
        }
        composeRule.onNodeWithContentDescription(indicator).performClick()
        typed?.let { composeRule.onNode(hasSetTextAction()).performTextReplacement(it) }
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun light() = capture("reader_goto_light", indicator = EN)

    @Test
    fun dark() = capture("reader_goto_dark", darkTheme = true, indicator = EN)

    @Test
    @Config(fontScale = 2f)
    fun largeFont() = capture("reader_goto_light_font200", indicator = EN)

    @Test
    @Config(qualifiers = "+fr")
    fun french() = capture("reader_goto_light_fr", indicator = FR)

    @Test
    fun outOfRange() = capture("reader_goto_error_light", typed = "12", indicator = EN)

    @Test
    fun outOfRangeDark() = capture("reader_goto_error_dark", typed = "12", darkTheme = true, indicator = EN)

    @Test
    @Config(fontScale = 2f)
    fun outOfRangeLargeFont() = capture("reader_goto_error_light_font200", typed = "12", indicator = EN)

    @Test
    @Config(qualifiers = "+fr")
    fun outOfRangeFrench() = capture("reader_goto_error_light_fr", typed = "12", indicator = FR)

    private companion object {
        const val PAGES = 8
        const val CURRENT_PAGE = 4
        const val EN = "Page 5 of 8"
        const val FR = "Page 5 sur 8"
    }
}
