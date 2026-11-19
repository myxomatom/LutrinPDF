// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
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
 * Screenshot references of the text selection (F08, ADR 0024): two lines selected with their
 * handles and the action bar, light, dark, large font, French, right to left, and with the copy
 * forbidden. The action bar is a popup: the whole screen is captured.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class SelectionScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val document = FakePdfDocument(List(PAGES) { A4 })

    private val firstLine = PdfQuad.of(PdfRect(180f, 300f, 520f, 316f))
    private val secondLine = PdfQuad.of(PdfRect(72f, 320f, 300f, 336f))

    /** From the middle of a line to the middle of the next, in reading order. */
    private val leftToRight =
        TextSelection(0, listOf(firstLine, secondLine), "bail\nde location", PdfPoint(180f, 316f), PdfPoint(300f, 336f))

    /** The same lines read right to left (Arabic): the selection starts on the right. */
    private val rightToLeft =
        TextSelection(0, listOf(firstLine, secondLine), "مكتبة\nكتاب", PdfPoint(520f, 316f), PdfPoint(72f, 336f))

    private fun capture(
        name: String,
        selection: TextSelection = leftToRight,
        darkTheme: Boolean = false,
        canCopy: Boolean = true,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        val state = ReaderUiState.Ready(
            "Contrat de location",
            document.pageSizes,
            PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
            canCopyText = canCopy,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                LutrinTheme(darkTheme = darkTheme, dynamicColor = false) {
                    ReaderScreen(state, onBack = {
                    }, selection = ReaderSelection(selection = selection, canCopy = canCopy))
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun light() = capture("selection_light")

    @Test
    fun dark() = capture("selection_dark", darkTheme = true)

    @Test
    @Config(fontScale = 2f)
    fun largeFont() = capture("selection_light_font200")

    @Test
    @Config(qualifiers = "+fr")
    fun french() = capture("selection_light_fr")

    @Test
    fun rightToLeft() = capture("selection_light_rtl", selection = rightToLeft, direction = LayoutDirection.Rtl)

    @Test
    fun copyForbidden() = capture("selection_light_copy_forbidden", canCopy = false)

    private companion object {
        const val PAGES = 3
    }
}
