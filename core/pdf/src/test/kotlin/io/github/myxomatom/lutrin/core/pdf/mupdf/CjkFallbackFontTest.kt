// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import org.junit.Before
import org.junit.Test

/**
 * CJK fallback font (ADR 0025) in the desktop library, built with the font flags of the Android
 * build and without any system font loader: the text can only be drawn with the embedded
 * DroidSansFallback. Runs on Linux only, like every engine JVM test (ADR 0013).
 */
class CjkFallbackFontTest {
    @Before
    fun requireHostEngine() = assumeHostEngine()

    @Test
    fun `draws CJK text whose fonts are not embedded with the embedded fallback font`() {
        CjkFallbackFontCheck.verify(TestAssets.file(CjkFallbackFontCheck.PATH).absolutePath)
    }
}
