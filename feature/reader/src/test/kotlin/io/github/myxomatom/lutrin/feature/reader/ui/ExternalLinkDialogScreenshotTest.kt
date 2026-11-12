// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
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
 * Screenshot references of the external link confirmation (V7) over the reader, with the link of
 * the mock-up: light, dark, font at 200 % and French. The dialog is another window: the large
 * font comes from the configuration, not from `LocalDensity`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ExternalLinkDialogScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, darkTheme: Boolean = false) {
        val sizes = List(PAGES) { A4 }
        val state =
            ReaderUiState.Ready(
                title = "Contrat de location",
                pageSizes = sizes,
                renderer = PageRenderer(FakePdfDocument(pageSizes = sizes), sizes, Dispatchers.Unconfined),
            )
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) {
                ReaderScreen(state, onBack = {}, links = ReaderLinks(externalLink = LINK))
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun light() = capture("reader_link_light")

    @Test
    fun dark() = capture("reader_link_dark", darkTheme = true)

    @Test
    @Config(fontScale = 2f)
    fun largeFont() = capture("reader_link_light_font200")

    @Test
    @Config(qualifiers = "+fr")
    fun french() = capture("reader_link_light_fr")

    private companion object {
        const val PAGES = 8
        const val LINK = "https://exemple.org/conditions-generales"
    }
}
