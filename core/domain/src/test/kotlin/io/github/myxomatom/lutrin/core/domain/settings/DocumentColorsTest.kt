// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.settings

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class DocumentColorsTest {
    @Test
    fun `the document keeps its original colours by default, even in the dark theme`() {
        val colors = DocumentColors()

        assertThat(colors.chosen).isNull()
        assertThat(colors.followDarkTheme).isFalse()
        assertThat(colors.modeFor(darkTheme = false)).isEqualTo(DocumentColorMode.NORMAL)
        assertThat(colors.modeFor(darkTheme = true)).isEqualTo(DocumentColorMode.NORMAL)
    }

    @Test
    fun `following the dark theme turns the document to night in the dark theme only`() {
        val colors = DocumentColors(followDarkTheme = true)

        assertThat(colors.modeFor(darkTheme = true)).isEqualTo(DocumentColorMode.NIGHT)
        assertThat(colors.modeFor(darkTheme = false)).isEqualTo(DocumentColorMode.NORMAL)
    }

    @Test
    fun `an explicit choice wins over the theme, whether it is followed or not`() {
        DocumentColorMode.entries.forEach { mode ->
            listOf(true, false).forEach { follow ->
                val colors = DocumentColors(chosen = mode, followDarkTheme = follow)

                assertThat(colors.modeFor(darkTheme = true)).isEqualTo(mode)
                assertThat(colors.modeFor(darkTheme = false)).isEqualTo(mode)
            }
        }
    }

    @Test
    fun `the app follows the system theme by default`() {
        assertThat(AppSettings().appTheme).isEqualTo(AppTheme.SYSTEM)
        assertThat(AppSettings.DEFAULT_APP_THEME).isEqualTo(AppTheme.SYSTEM)
        assertThat(AppSettings().documentColors).isEqualTo(DocumentColors())
    }

    @Test
    fun `the app theme is dark when forced dark, or when it follows a dark system`() {
        assertThat(AppTheme.LIGHT.isDark(systemDark = true)).isFalse()
        assertThat(AppTheme.LIGHT.isDark(systemDark = false)).isFalse()
        assertThat(AppTheme.DARK.isDark(systemDark = true)).isTrue()
        assertThat(AppTheme.DARK.isDark(systemDark = false)).isTrue()
        assertThat(AppTheme.SYSTEM.isDark(systemDark = true)).isTrue()
        assertThat(AppTheme.SYSTEM.isDark(systemDark = false)).isFalse()
    }

    @Test
    fun `normal mode keeps white paper and black ink`() {
        assertThat(DocumentColorMode.NORMAL.paper).isEqualTo(0xFFFFFFFF.toInt())
        assertThat(DocumentColorMode.NORMAL.ink).isEqualTo(0xFF000000.toInt())
        assertThat(DocumentColorMode.NORMAL.isDark).isFalse()
        assertThat(DocumentColorMode.SEPIA.isDark).isFalse()
        assertThat(DocumentColorMode.NIGHT.isDark).isTrue()
    }

    @Test
    fun `paper and ink of night and sepia are the design tokens`() {
        val tokens = findTokensFile().readText()

        assertThat(DocumentColorMode.NIGHT.paper).isEqualTo(token(tokens, "pageNight"))
        assertThat(DocumentColorMode.NIGHT.ink).isEqualTo(token(tokens, "textNight"))
        assertThat(DocumentColorMode.SEPIA.paper).isEqualTo(token(tokens, "pageSepia"))
        assertThat(DocumentColorMode.SEPIA.ink).isEqualTo(token(tokens, "textSepia"))
        assertThat(DocumentColorMode.NORMAL.paper).isEqualTo(token(tokens, "pageNormal"))
    }

    @Test
    fun `ink keeps at least the AA contrast on its paper`() {
        DocumentColorMode.entries.forEach { mode ->
            assertThat(contrast(mode.paper, mode.ink)).isAtLeast(AA_CONTRAST)
        }
    }

    /** Opaque ARGB colour of a `"name": "#RRGGBB"` token of the design tokens. */
    private fun token(tokens: String, name: String): Int {
        val hex = checkNotNull(Regex("\"$name\"\\s*:\\s*\"#([0-9A-Fa-f]{6})\"").find(tokens)) { "No token $name" }
        return (0xFF000000 or hex.groupValues[1].toLong(HEX)).toInt()
    }

    private fun findTokensFile(): File {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "docs/design/design-tokens.json")
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        error("docs/design/design-tokens.json not found above ${File("").absolutePath}")
    }

    /** WCAG contrast ratio of two opaque colours. */
    private fun contrast(first: Int, second: Int): Double {
        val (light, dark) = listOf(luminance(first), luminance(second)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = (color shr shift and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private companion object {
        const val HEX = 16
        const val AA_CONTRAST = 4.5
    }
}
