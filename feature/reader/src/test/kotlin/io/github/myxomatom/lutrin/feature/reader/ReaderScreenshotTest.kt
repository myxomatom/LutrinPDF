// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot references of the reader (E2) and its error view (V8), light and dark. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ReaderScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, state: ReaderUiState, darkTheme: Boolean = false) {
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) {
                ReaderScreen(state = state, onBack = {}, loadPage = { _, _ -> null })
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val content = ReaderUiState.Content("Contrat de location", List(8) { PageSize(595f, 842f) })

    @Test
    fun content() = capture("reader_content_light", content)

    @Test
    fun contentDark() = capture("reader_content_dark", content, darkTheme = true)

    @Test
    fun error() = capture("reader_error_light", ReaderUiState.Error("scan_facture.pdf", ReaderError.UNREADABLE))

    @Test
    @Config(qualifiers = "+fr")
    fun errorFrench() = capture("reader_error_light_fr", ReaderUiState.Error("scan_facture.pdf", ReaderError.UNREADABLE))
}
