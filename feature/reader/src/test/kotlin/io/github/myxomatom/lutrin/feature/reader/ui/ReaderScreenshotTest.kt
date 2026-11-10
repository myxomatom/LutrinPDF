// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.testing.A4
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

    private fun capture(name: String, state: ReaderUiState, darkTheme: Boolean = false, fontScale: Float = 1f) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LutrinTheme(darkTheme = darkTheme, dynamicColor = false) { ReaderScreen(state, onBack = {}) }
            }
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

    /**
     * The reader of the mockup E2-Lecteur-Curseur: on page 3 of 8, the slider dragged to page 6
     * and held there, the preview card above the thumb.
     */
    private fun captureDragging(name: String, darkTheme: Boolean) {
        val document = FakePdfDocument(List(MOCKUP_PAGES) { A4 })
        val state =
            ReaderUiState.Ready(
                "Contrat de location",
                document.pageSizes,
                PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
                currentPage = MOCKUP_START_PAGE,
            )
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) { ReaderScreen(state, onBack = {}) }
        }
        composeRule.onNodeWithContentDescription("Go to page").performTouchInput {
            // The thumb travels from half a thumb after the start of the slider to half a thumb before its end.
            fun thumbAt(page: Int) =
                Offset(THUMB_RADIUS_PX + (width - 2 * THUMB_RADIUS_PX) * page / (MOCKUP_PAGES - 1), centerY)
            val start = thumbAt(MOCKUP_START_PAGE)
            // The slider only follows the finger once it has moved past the touch slop.
            val target = thumbAt(MOCKUP_AIMED_PAGE) + Offset(TOUCH_SLOP_PX, 0f)
            down(start)
            val step = (target - start) / DRAG_STEPS.toFloat()
            repeat(DRAG_STEPS) { moveBy(step) }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        composeRule.onNodeWithContentDescription("Go to page").performTouchInput { cancel() }
    }

    @Test
    fun reader() = capture("reader_light", ready())

    @Test
    fun readerDragging() = captureDragging("reader_dragging_light", darkTheme = false)

    @Test
    fun readerDraggingDark() = captureDragging("reader_dragging_dark", darkTheme = true)

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

    // V8 for a document of the recent list: « Retirer des récents » above the way back (ADR 0016).

    private val recentError = ReaderUiState.Error(ReaderError.NOT_ACCESSIBLE, inRecents = true)

    @Test
    fun errorRecent() = capture("reader_error_recent_light", recentError)

    @Test
    fun errorRecentDark() = capture("reader_error_recent_dark", recentError, darkTheme = true)

    @Test
    fun errorRecentLargeFont() = capture("reader_error_recent_light_font200", recentError, fontScale = 2f)

    @Test
    @Config(qualifiers = "+fr")
    fun errorRecentFrench() = capture("reader_error_recent_fr", recentError)

    private companion object {
        /** The document of the mockup: 8 pages, read on page 3, the slider aimed at page 6. */
        const val MOCKUP_PAGES = 8
        const val MOCKUP_START_PAGE = 2
        const val MOCKUP_AIMED_PAGE = 5

        /** Half the 20 dp thumb, at the xhdpi density of these captures. */
        const val THUMB_RADIUS_PX = 20f

        /** Touch slop at xhdpi (8 dp), lost at the start of a drag. */
        const val TOUCH_SLOP_PX = 16f

        /** Moves of the drag: the slider ignores a single jump (touch slop). */
        const val DRAG_STEPS = 20
    }
}
