// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.ui.R
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** The bundled title font is the file whose provenance is recorded in `docs/dependencies.md`. */
@RunWith(AndroidJUnit4::class)
class TitleFontTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun fontBytes(): ByteArray = context.resources.openRawResource(R.font.fraunces_semibold).use { it.readBytes() }

    @Test
    fun `bundled font is the unmodified Fraunces 600 file from expo-google-fonts 0_4_1`() {
        val digest = MessageDigest.getInstance("SHA-256").digest(fontBytes())

        assertThat(digest.joinToString("") { "%02x".format(it) }).isEqualTo(FRAUNCES_SEMIBOLD_SHA256)
    }

    @Test
    fun `bundled font is a static TrueType file small enough for the apk`() {
        val bytes = fontBytes()

        // TrueType scaler type 0x00010000: a static font, not a variable one (no fvar check needed
        // here, the checksum above pins the exact file).
        assertThat(bytes.take(TRUETYPE_MAGIC.size)).isEqualTo(TRUETYPE_MAGIC.toList())
        assertThat(bytes.size).isLessThan(MAX_FONT_BYTES)
    }

    @Test
    fun `titles use the Fraunces family and body text the system font`() {
        assertThat(LutrinTypography.titleLarge.fontFamily).isEqualTo(LutrinTitleFontFamily)
        assertThat(LutrinTypography.headlineSmall.fontFamily).isEqualTo(LutrinTitleFontFamily)
        assertThat(LutrinTypography.headlineLarge.fontFamily).isEqualTo(LutrinTitleFontFamily)
        assertThat(LutrinTypography.displayLarge.fontFamily).isEqualTo(LutrinTitleFontFamily)
        assertThat(LutrinTypography.titleLargeCompact.fontFamily).isEqualTo(LutrinTitleFontFamily)
        assertThat(LutrinTypography.titleMediumSerif.fontFamily).isEqualTo(LutrinTitleFontFamily)
        assertThat(LutrinTypography.titleMedium.fontFamily).isEqualTo(Typography().titleMedium.fontFamily)
        assertThat(LutrinTypography.bodyLarge.fontFamily).isEqualTo(Typography().bodyLarge.fontFamily)
        assertThat(LutrinTypography.labelLarge.fontFamily).isEqualTo(Typography().labelLarge.fontFamily)
    }

    private companion object {
        val TRUETYPE_MAGIC = byteArrayOf(0x00, 0x01, 0x00, 0x00)
        const val MAX_FONT_BYTES = 100 * 1024
        const val FRAUNCES_SEMIBOLD_SHA256 = "9f03c0fe70d1670d22f392a138ac02eceedc5eee5f1cd8d7486ba2aed843b8aa"
    }
}
