// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class LutrinThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun schemeInsideTheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
        lateinit var scheme: ColorScheme
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                scheme = MaterialTheme.colorScheme
            }
        }
        composeRule.waitForIdle()
        return scheme
    }

    @Test
    fun `light theme without dynamic colour uses the design tokens`() {
        val scheme = schemeInsideTheme(darkTheme = false, dynamicColor = false)

        assertThat(scheme).isEqualTo(LutrinLightColorScheme)
    }

    @Test
    fun `dark theme without dynamic colour uses the dark scheme`() {
        val scheme = schemeInsideTheme(darkTheme = true, dynamicColor = false)

        assertThat(scheme).isEqualTo(LutrinDarkColorScheme)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `dynamic colour is ignored before Android 12`() {
        assertThat(lutrinColorScheme(context, darkTheme = false, dynamicColor = true)).isEqualTo(LutrinLightColorScheme)
        assertThat(lutrinColorScheme(context, darkTheme = true, dynamicColor = true)).isEqualTo(LutrinDarkColorScheme)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `dynamic colour is used from Android 12`() {
        // ColorScheme has no structural equality: compare the key roles.
        assertThat(lutrinColorScheme(context, darkTheme = false, dynamicColor = true).roles())
            .isEqualTo(dynamicLightColorScheme(context).roles())
        assertThat(lutrinColorScheme(context, darkTheme = true, dynamicColor = true).roles())
            .isEqualTo(dynamicDarkColorScheme(context).roles())
        assertThat(lutrinColorScheme(context, darkTheme = false, dynamicColor = true).roles())
            .isNotEqualTo(LutrinLightColorScheme.roles())
    }

    private fun ColorScheme.roles() = listOf(primary, onPrimary, secondary, tertiary, surface, onSurface, error)

    @Test
    fun `typography and shapes come from the design tokens`() {
        lateinit var theme: Pair<androidx.compose.material3.Typography, androidx.compose.material3.Shapes>
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) { theme = MaterialTheme.typography to MaterialTheme.shapes }
        }
        composeRule.waitForIdle()

        assertThat(theme.first).isEqualTo(LutrinTypography)
        assertThat(theme.second).isEqualTo(LutrinShapes)
    }
}
