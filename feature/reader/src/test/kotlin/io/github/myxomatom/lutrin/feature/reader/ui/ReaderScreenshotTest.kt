// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
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

    private fun ready(): ReaderUiState {
        val document = FakePdfDocument()
        return ReaderUiState.Ready(
            "Contrat de location",
            document.pageSizes,
            PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
        )
    }

    @Test
    fun reader() = capture("reader_light", ready())

    @Test
    fun readerDark() = capture("reader_dark", ready(), darkTheme = true)

    @Test
    fun error() = capture("reader_error_light", ReaderUiState.Error(ReaderError.UNREADABLE))

    @Test
    @Config(qualifiers = "+fr")
    fun errorFrench() = capture("reader_error_fr", ReaderUiState.Error(ReaderError.UNSUPPORTED_FORMAT))
}
