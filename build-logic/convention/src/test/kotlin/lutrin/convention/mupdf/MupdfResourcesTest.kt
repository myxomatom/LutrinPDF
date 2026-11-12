// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention.mupdf

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MupdfResourcesTest {
    @Test
    fun `generated source path appends the C extension like the Makefile rules`() {
        assertThat(
            generatedSourcePath("resources/fonts/urw/Dingbats.cff"),
        ).isEqualTo("resources/fonts/urw/Dingbats.cff.c")
        assertThat(generatedSourcePath("resources/hyphen/hyph-all.zip")).isEqualTo("resources/hyphen/hyph-all.zip.c")
    }

    @Test
    fun `generated source path rejects blank and absolute paths`() {
        assertThrows(IllegalArgumentException::class.java) { generatedSourcePath(" ") }
        assertThrows(IllegalArgumentException::class.java) { generatedSourcePath("/resources/x.cff") }
    }

    @Test
    fun `embedded resources cover the fonts and hyphenation patterns of make generate`() {
        assertThat(MUPDF_EMBEDDED_RESOURCES).containsAtLeast(
            "resources/fonts/urw/*.cff",
            "resources/hyphen/hyph-all.zip",
        )
        assertThat(MUPDF_EMBEDDED_RESOURCES.all { it.startsWith("resources/") }).isTrue()
    }

    @Test
    fun `embedded resources leave out the fallback fonts, like the Android font flags`() {
        // TOFU_NOTO, TOFU_CJK and TOFU_SIL (ADR 0025): CJK text uses the device's fonts.
        val leftOut =
            MUPDF_EMBEDDED_RESOURCES.filter {
                it.startsWith("resources/fonts/han/") ||
                    it.startsWith("resources/fonts/droid/") ||
                    it.startsWith("resources/fonts/noto/") ||
                    it.startsWith("resources/fonts/sil/")
            }
        assertThat(leftOut).isEmpty()
    }
}
