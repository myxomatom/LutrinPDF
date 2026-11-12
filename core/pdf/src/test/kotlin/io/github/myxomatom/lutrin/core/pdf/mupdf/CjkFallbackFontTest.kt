// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import org.junit.Before
import org.junit.Test

/**
 * Known limit of ADR 0025 in the desktop library, built with the font flags of the Android build
 * (no CJK font compiled in) and without any system font loader, like a device without CJK fonts:
 * CJK text whose fonts are not embedded is not drawn with CJK glyphs. Also proves that the check
 * of the instrumented test tells boxes from text. Runs on Linux only, like every engine JVM test
 * (ADR 0013).
 */
class CjkFallbackFontTest {
    @Before
    fun requireHostEngine() = assumeHostEngine()

    @Test
    fun `does not draw non-embedded CJK text with CJK glyphs when no CJK font is available`() {
        CjkFallbackFontCheck.verifyNoCjkFont(TestAssets.file(CjkFallbackFontCheck.PATH).absolutePath)
    }
}
