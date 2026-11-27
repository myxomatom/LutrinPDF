// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageRotation
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** « Tourner » of the top bar of the reader (F27, ADR 0032), and its place in the menu when the bar lacks room. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ReaderRotateActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var turns = 0

    private fun showBar(rotation: PageRotation = PageRotation.NONE, darkTheme: Boolean = false) {
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) {
                ReaderTopBar(
                    title = "Contrat de location",
                    onBack = {},
                    menu = ReaderMenu({}, {}, {}, MenuRotation(rotation) { turns++ }),
                    onOutline = {},
                    onSearch = {},
                )
            }
        }
    }

    @Test
    fun `the bar shows « Tourner » with the angle now shown and a 48 dp target`() {
        showBar(PageRotation.QUARTER)

        composeRule.onNodeWithContentDescription("Rotate pages, now at 90°")
            .assertTouchWidthIsEqualTo(48.dp)
            .performClick()

        assertThat(turns).isEqualTo(1)
    }

    @Test
    fun `with room in the bar, the menu has no « Tourner »`() {
        showBar()

        composeRule.onNodeWithContentDescription("More options").performClick()

        composeRule.onNodeWithText("Display").assertExists()
        composeRule.onNodeWithText("Rotate pages (0°)").assertDoesNotExist()
    }

    @Test
    @Config(fontScale = 2f)
    fun `with a very large font, « Tourner » moves into the menu`() {
        showBar(PageRotation.HALF)

        composeRule.onNodeWithContentDescription("Rotate pages, now at 180°").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Rotate pages (180°)").performClick()

        assertThat(turns).isEqualTo(1)
        composeRule.onNodeWithText("Display").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w280dp-h640dp-xhdpi")
    fun `on a narrow screen, « Tourner » moves into the menu`() {
        showBar()

        composeRule.onNodeWithContentDescription("Rotate pages, now at 0°").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Rotate pages (0°)").assertExists()
    }

    @Test
    fun `« Tourner » fits in the bar unless the title would lose its room`() {
        assertThat(rotationFitsInBar(360.dp, fontScale = 1f, actionCount = 4)).isTrue()
        assertThat(rotationFitsInBar(360.dp, fontScale = 1.3f, actionCount = 4)).isTrue()
        assertThat(rotationFitsInBar(360.dp, fontScale = 2f, actionCount = 4)).isFalse()
        assertThat(rotationFitsInBar(280.dp, fontScale = 1f, actionCount = 4)).isFalse()
        assertThat(rotationFitsInBar(600.dp, fontScale = 2f, actionCount = 4)).isTrue()
    }

    @Test
    fun captureBar() {
        showBar()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/reader_rotate_bar_light.png")
    }

    @Test
    fun captureBarDark() {
        showBar(darkTheme = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/reader_rotate_bar_dark.png")
    }

    @Test
    @Config(fontScale = 2f)
    fun captureMenuLargeFont() = captureMenu("reader_rotate_menu_light_font200", darkTheme = false)

    @Test
    @Config(fontScale = 2f)
    fun captureMenuLargeFontDark() = captureMenu("reader_rotate_menu_dark_font200", darkTheme = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureMenu(name: String, darkTheme: Boolean) {
        showBar(PageRotation.QUARTER, darkTheme)
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }
}
