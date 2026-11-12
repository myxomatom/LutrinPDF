// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artifex.mupdf.fitz.Font
import io.github.myxomatom.lutrin.core.testing.TestFiles
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CJK fallback font (ADR 0025) in the Android library: CJK text whose fonts are not embedded is
 * drawn with real glyphs, with the device's fonts and with the font embedded in the app.
 *
 * MuPDF's Android build first looks for CJK fonts in `/system/fonts` (Noto CJK, Droid Sans
 * Fallback), then in the fonts embedded in the library. Emulators have system CJK fonts, so the
 * second part hides them to prove that the embedded DroidSansFallback is there and works, as on a
 * device without CJK fonts.
 */
@RunWith(AndroidJUnit4::class)
class CjkFallbackFontInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun drawsCjkTextWithTheSystemFontsThenWithTheEmbeddedFallbackFont() {
        val file = copyAsset(TestFiles.Pdf.CJK_NOT_EMBEDDED)

        // Default font lookup: system fonts first, then the embedded ones.
        CjkFallbackFontCheck.verify(file.absolutePath)

        // A Java font loader replaces the system font lookup of the whole process (MuPDF has no
        // way to restore it). Returning null finds nothing, so only the embedded fonts are left.
        // Harmless for the other tests of this process: their fonts are embedded or Base 14.
        Font.installFontLoader { _, _, _, _ -> null }
        CjkFallbackFontCheck.verify(file.absolutePath)
    }

    /** Copies a test asset (packaged in the test APK) into the cache of the app under test. */
    private fun copyAsset(path: String): File =
        File(instrumentation.targetContext.cacheDir, path.substringAfterLast('/')).apply {
            instrumentation.context.assets.open(path).use { input -> outputStream().use { input.copyTo(it) } }
        }
}
