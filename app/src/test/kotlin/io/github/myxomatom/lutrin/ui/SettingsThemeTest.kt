// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.AppTheme
import io.github.myxomatom.lutrin.core.ui.theme.LocalDarkTheme
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDarkColorScheme
import io.github.myxomatom.lutrin.core.ui.theme.LutrinLightColorScheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The theme of the app follows the setting of E3 (ADR 0028), the system bars too. */
@RunWith(AndroidJUnit4::class)
class SettingsThemeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var darkTheme: Boolean? = null
    private var isLightScheme: Boolean? = null

    private fun show(theme: AppTheme) {
        composeRule.setContent {
            SettingsTheme(AppSettings(dynamicColor = false, appTheme = theme)) {
                darkTheme = LocalDarkTheme.current
                isLightScheme = MaterialTheme.colorScheme == LutrinLightColorScheme
            }
        }
        composeRule.waitForIdle()
    }

    private fun lightStatusBarIcons(): Boolean {
        val window = composeRule.activity.window
        return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
    }

    @Test
    fun `the system theme is followed by default`() {
        show(AppSettings().appTheme)

        assertThat(darkTheme).isFalse()
        assertThat(isLightScheme).isTrue()
    }

    @Test
    @Config(qualifiers = "night")
    fun `the system theme is followed when it is dark`() {
        show(AppTheme.SYSTEM)

        assertThat(darkTheme).isTrue()
        assertThat(isLightScheme).isFalse()
    }

    @Test
    fun `a forced dark theme wins over a light system, status bar icons included`() {
        show(AppTheme.DARK)

        assertThat(darkTheme).isTrue()
        assertThat(isLightScheme).isFalse()
        assertThat(lightStatusBarIcons()).isFalse()
    }

    @Test
    @Config(qualifiers = "night")
    fun `a forced light theme wins over a dark system, status bar icons included`() {
        show(AppTheme.LIGHT)

        assertThat(darkTheme).isFalse()
        assertThat(isLightScheme).isTrue()
        assertThat(lightStatusBarIcons()).isTrue()
    }

    @Test
    fun `the dark scheme is the Lutrin one without dynamic colours`() {
        var scheme: Any? = null
        composeRule.setContent {
            SettingsTheme(AppSettings(dynamicColor = false, appTheme = AppTheme.DARK)) {
                scheme = MaterialTheme.colorScheme
            }
        }
        composeRule.waitForIdle()

        assertThat(scheme).isEqualTo(LutrinDarkColorScheme)
    }
}
