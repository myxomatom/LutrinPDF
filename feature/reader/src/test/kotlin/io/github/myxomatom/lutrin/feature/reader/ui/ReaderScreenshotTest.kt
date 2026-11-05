// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderError
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot references of the reader (E2) and of its error screen (V8). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ReaderScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, state: ReaderUiState, darkTheme: Boolean = false) {
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) { ReaderScreen(state, onBack = {}) }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun ready(scrollMode: ScrollMode = ScrollMode.CONTINUOUS): ReaderUiState {
        val document = FakePdfDocument()
        return ReaderUiState.Ready(
            "Contrat de location",
            document.pageSizes,
            PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
            scrollMode,
        )
    }

    /** The display options sheet (V6) over the reader; the sheet is a separate window. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureOptions(name: String, darkTheme: Boolean) {
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) { ReaderScreen(ready(), onBack = {}) }
        }
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun reader() = capture("reader_light", ready())

    @Test
    fun readerDark() = capture("reader_dark", ready(), darkTheme = true)

    @Test
    fun readerPaged() = capture("reader_paged_light", ready(ScrollMode.PAGED))

    @Test
    fun options() = captureOptions("reader_options_light", darkTheme = false)

    @Test
    fun optionsDark() = captureOptions("reader_options_dark", darkTheme = true)

    @Test
    fun error() = capture("reader_error_light", ReaderUiState.Error(ReaderError.UNREADABLE))

    @Test
    @Config(qualifiers = "+fr")
    fun errorFrench() = capture("reader_error_fr", ReaderUiState.Error(ReaderError.UNSUPPORTED_FORMAT))
}
