// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.myxomatom.lutrin.core.testing.TestFiles
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CJK text whose fonts are not embedded (ADR 0025) is drawn with real glyphs by the Android
 * library, which compiles no CJK font: MuPDF takes the device's CJK font from `/system/fonts`
 * (Noto CJK or Droid Sans Fallback, present on the emulators of the CI).
 */
@RunWith(AndroidJUnit4::class)
class CjkFallbackFontInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun drawsCjkTextWhoseFontsAreNotEmbeddedWithTheSystemFonts() {
        CjkFallbackFontCheck.verify(copyAsset(TestFiles.Pdf.CJK_NOT_EMBEDDED).absolutePath)
    }

    /** Copies a test asset (packaged in the test APK) into the cache of the app under test. */
    private fun copyAsset(path: String): File =
        File(instrumentation.targetContext.cacheDir, path.substringAfterLast('/')).apply {
            instrumentation.context.assets.open(path).use { input -> outputStream().use { input.copyTo(it) } }
        }
}
