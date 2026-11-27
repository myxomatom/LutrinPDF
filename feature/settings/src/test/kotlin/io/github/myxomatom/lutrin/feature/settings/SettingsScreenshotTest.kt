// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.myxomatom.lutrin.core.domain.settings.AppLanguage
import io.github.myxomatom.lutrin.core.domain.settings.AppTheme
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot references of the settings screen (E3), light, dark and French, with an empty recent
 * list (« Vider les récents » disabled); and the confirmation of « Vider les récents » (ADR 0016),
 * light, dark, large font and French; the theme of the app forced dark, with documents following
 * it (ADR 0028), and the screen at 200 %. The language of the app (ADR 0029) is shown, as on Android 13 and
 * later; the reading section is captured scrolled to « Garder l'écran allumé ».
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        recentCount: Int,
        darkTheme: Boolean,
        ignoreDocumentRestrictions: Boolean = false,
        appTheme: AppTheme = AppTheme.SYSTEM,
        followDarkTheme: Boolean = false,
    ) {
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) {
                SettingsScreen(
                    state = SettingsUiState(
                        loading = false,
                        dynamicColor = true,
                        ignoreDocumentRestrictions = ignoreDocumentRestrictions,
                        recentCount = recentCount,
                        appTheme = appTheme,
                        followDarkTheme = followDarkTheme,
                        appLanguage = AppLanguage.SYSTEM,
                    ),
                    dynamicColorSupported = true,
                    onDynamicColorChange = {},
                    onBack = {},
                )
            }
        }
    }

    private fun capture(name: String, darkTheme: Boolean = false) {
        show(recentCount = 0, darkTheme = darkTheme)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    /** The confirmation dialog of « Vider les récents »; the dialog is a separate window. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureClear(name: String, label: String, darkTheme: Boolean = false) {
        show(recentCount = RECENT_COUNT, darkTheme = darkTheme)
        // In large fonts the row is below the fold: scroll to it, as a user would.
        composeRule.onNodeWithText(label).performScrollTo().performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun light() = capture("settings_light")

    @Test
    fun dark() = capture("settings_dark", darkTheme = true)

    @Test
    @Config(qualifiers = "+fr")
    fun french() = capture("settings_light_fr")

    /** « Ignorer les restrictions du document » turned on (ADR 0024, ADR 0026). */
    @Test
    fun documentRestrictionsIgnored() {
        show(recentCount = 0, darkTheme = false, ignoreDocumentRestrictions = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/settings_document_restrictions_ignored_light.png")
    }

    /** Dark theme forced, documents following it (ADR 0028). */
    @Test
    fun darkThemeForced() {
        show(recentCount = 0, darkTheme = true, appTheme = AppTheme.DARK, followDarkTheme = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/settings_theme_dark_follow.png")
    }

    /** At 200 %, the three theme buttons and the long summaries still fit (ADR 0028). */
    @Test
    @Config(fontScale = 2f)
    fun largeFont() = capture("settings_light_font200")

    /** The reading section, scrolled to its first row: « Garder l'écran allumé » (ADR 0029). */
    private fun captureReading(name: String, label: String, darkTheme: Boolean = false) {
        show(recentCount = 0, darkTheme = darkTheme)
        composeRule.onNodeWithText(label).performScrollTo()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun reading() = captureReading("settings_reading_light", KEEP_ON_EN)

    @Test
    fun readingDark() = captureReading("settings_reading_dark", KEEP_ON_EN, darkTheme = true)

    @Test
    @Config(qualifiers = "+fr")
    fun readingFrench() = captureReading("settings_reading_light_fr", KEEP_ON_FR)

    @Test
    @Config(fontScale = 2f)
    fun readingLargeFont() = captureReading("settings_reading_light_font200", KEEP_ON_EN)

    @Test
    fun clearRecents() = captureClear("settings_clear_recents_light", CLEAR_EN)

    @Test
    fun clearRecentsDark() = captureClear("settings_clear_recents_dark", CLEAR_EN, darkTheme = true)

    // The dialog is another window: the font scale is the configuration's, not LocalDensity's.
    @Test
    @Config(fontScale = 2f)
    fun clearRecentsLargeFont() = captureClear("settings_clear_recents_light_font200", CLEAR_EN)

    @Test
    @Config(qualifiers = "+fr")
    fun clearRecentsFrench() = captureClear("settings_clear_recents_light_fr", CLEAR_FR)

    private companion object {
        const val RECENT_COUNT = 6
        const val CLEAR_EN = "Clear recent documents"
        const val CLEAR_FR = "Vider les récents"
        const val KEEP_ON_EN = "Keep the screen on"
        const val KEEP_ON_FR = "Garder l’écran allumé"
    }
}
