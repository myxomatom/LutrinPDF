// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Keeps the theme and `docs/design/design-tokens.json` in sync: a value changed on one side and not
 * on the other fails here (`AGENTS.md` §11). Robolectric is only needed for `org.json`.
 */
@RunWith(AndroidJUnit4::class)
class DesignTokensTest {
    private val tokens: JSONObject = JSONObject(findTokensFile().readText())

    private fun findTokensFile(): File {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "docs/design/design-tokens.json")
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        error("docs/design/design-tokens.json not found above ${File("").absolutePath}")
    }

    private fun roles(scheme: ColorScheme): Map<String, Color> = mapOf(
        "primary" to scheme.primary,
        "onPrimary" to scheme.onPrimary,
        "primaryContainer" to scheme.primaryContainer,
        "onPrimaryContainer" to scheme.onPrimaryContainer,
        "inversePrimary" to scheme.inversePrimary,
        "secondary" to scheme.secondary,
        "onSecondary" to scheme.onSecondary,
        "secondaryContainer" to scheme.secondaryContainer,
        "onSecondaryContainer" to scheme.onSecondaryContainer,
        "tertiary" to scheme.tertiary,
        "onTertiary" to scheme.onTertiary,
        "tertiaryContainer" to scheme.tertiaryContainer,
        "onTertiaryContainer" to scheme.onTertiaryContainer,
        "background" to scheme.background,
        "onBackground" to scheme.onBackground,
        "surface" to scheme.surface,
        "onSurface" to scheme.onSurface,
        "surfaceVariant" to scheme.surfaceVariant,
        "onSurfaceVariant" to scheme.onSurfaceVariant,
        "inverseSurface" to scheme.inverseSurface,
        "inverseOnSurface" to scheme.inverseOnSurface,
        "error" to scheme.error,
        "onError" to scheme.onError,
        "errorContainer" to scheme.errorContainer,
        "onErrorContainer" to scheme.onErrorContainer,
        "outline" to scheme.outline,
        "outlineVariant" to scheme.outlineVariant,
        "surfaceBright" to scheme.surfaceBright,
        "surfaceDim" to scheme.surfaceDim,
        "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
    )

    private fun assertSchemeMatchesTokens(scheme: ColorScheme, section: String) {
        val tokenColors = tokens.getJSONObject("color").getJSONObject(section)
        val schemeRoles = roles(scheme)
        // Every role of the scheme has a token, and the other way round (except "scrim" and notes).
        val tokenRoles = tokenColors.keys().asSequence().filterNot { it.startsWith("$") || it == "scrim" }.toSet()
        assertThat(tokenRoles).containsExactlyElementsIn(schemeRoles.keys)
        schemeRoles.forEach { (role, color) ->
            val expected = Color(tokenColors.getString(role).removePrefix("#").toLong(HEX_RADIX) or OPAQUE)
            assertThat(color).isEqualTo(expected)
        }
    }

    @Test
    fun `light scheme has exactly the colours of the light tokens`() {
        assertSchemeMatchesTokens(LutrinLightColorScheme, "light")
    }

    @Test
    fun `dark scheme has exactly the colours of the dark tokens`() {
        assertSchemeMatchesTokens(LutrinDarkColorScheme, "dark")
    }

    @Test
    fun `tertiary roles are the warm amber accent in both schemes`() {
        assertThat(LutrinLightColorScheme.tertiary).isEqualTo(Color(0xFF7C5800))
        assertThat(LutrinLightColorScheme.tertiaryContainer).isEqualTo(Color(0xFFFFDEA6))
        assertThat(LutrinDarkColorScheme.tertiary).isEqualTo(Color(0xFFF5BF48))
        assertThat(LutrinDarkColorScheme.tertiaryContainer).isEqualTo(Color(0xFF5D4200))
    }

    private fun styles(typography: Typography): Map<String, TextStyle> = mapOf(
        "displayLarge" to typography.displayLarge,
        "headlineLarge" to typography.headlineLarge,
        "headlineSmall" to typography.headlineSmall,
        "titleLarge" to typography.titleLarge,
        "titleLargeCompact" to typography.titleLargeCompact,
        "titleMediumSerif" to typography.titleMediumSerif,
        "titleMedium" to typography.titleMedium,
        "bodyLarge" to typography.bodyLarge,
        "bodyMedium" to typography.bodyMedium,
        "bodySmall" to typography.bodySmall,
        "labelLarge" to typography.labelLarge,
    )

    @Test
    fun `every typography token matches its text style`() {
        val tokenStyles = tokens.getJSONObject("typography")
        val styles = styles(LutrinTypography)
        val tokenNames = tokenStyles.keys().asSequence().filter { tokenStyles.optJSONObject(it) != null }.toSet()
        assertThat(tokenNames).containsExactlyElementsIn(styles.keys)

        styles.forEach { (name, style) ->
            val token = tokenStyles.getJSONObject(name)
            assertThat(style.fontSize.value).isEqualTo(token.getInt("size").toFloat())
            assertThat(style.lineHeight.value).isEqualTo(token.getInt("lineHeight").toFloat())
            assertThat(style.fontWeight).isEqualTo(FontWeight(token.getInt("weight")))
            val expectedFamily: FontFamily? =
                if (token.optString("family") == "Fraunces") LutrinTitleFontFamily else SYSTEM_FAMILY
            assertThat(style.fontFamily).isEqualTo(expectedFamily)
        }
    }

    @Test
    fun `list thumbnail token is 52 by 68 dp`() {
        val thumbnail = tokens.getJSONObject("spacing").getJSONObject("listThumbnail")

        assertThat(thumbnail.getInt("width")).isEqualTo(52)
        assertThat(thumbnail.getInt("height")).isEqualTo(68)
    }

    private companion object {
        /** The Material 3 default: the system font, whichever one the device uses. */
        val SYSTEM_FAMILY: FontFamily? = Typography().bodyLarge.fontFamily
        const val HEX_RADIX = 16
        const val OPAQUE = 0xFF000000
    }
}
