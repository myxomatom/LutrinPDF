// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention.mupdf

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import org.junit.Test

class HexDumpTest {
    private fun dump(bytes: ByteArray, symbol: String = "x_bin"): String {
        val output = ByteArrayOutputStream()
        HexDump.write(bytes.inputStream(), symbol, output)
        return output.toString(Charsets.US_ASCII)
    }

    private fun expected(symbol: String, vararg lines: String): String = buildString {
        append("// This is an automatically generated file. Do not edit.\n")
        append("const unsigned char _binary_$symbol[] =\n")
        lines.forEach { append(it).append('\n') }
        append(";\n")
        append("const unsigned int _binary_${symbol}_size = sizeof(_binary_$symbol) - 1;\n")
    }

    @Test
    fun `symbolName replaces dots and dashes with underscores`() {
        assertThat(HexDump.symbolName("NimbusSans-BoldItalic.cff")).isEqualTo("NimbusSans_BoldItalic_cff")
        assertThat(HexDump.symbolName("hyph-all.zip")).isEqualTo("hyph_all_zip")
    }

    @Test
    fun `symbolName keeps every other character`() {
        assertThat(HexDump.symbolName("a_b+c d.e")).isEqualTo("a_b+c d_e")
    }

    @Test
    fun `empty input gives an array without data line`() {
        assertThat(dump(ByteArray(0))).isEqualTo(expected("x_bin"))
    }

    @Test
    fun `one byte gives one line with a lowercase escape`() {
        assertThat(dump(byteArrayOf(0xAB.toByte()))).isEqualTo(expected("x_bin", "\"\\xab\""))
    }

    @Test
    fun `exactly sixteen bytes fill a single line`() {
        val bytes = ByteArray(16) { it.toByte() }

        assertThat(dump(bytes)).isEqualTo(
            expected("x_bin", "\"\\x00\\x01\\x02\\x03\\x04\\x05\\x06\\x07\\x08\\x09\\x0a\\x0b\\x0c\\x0d\\x0e\\x0f\""),
        )
    }

    @Test
    fun `seventeen bytes wrap onto a second short line`() {
        val bytes = ByteArray(17) { 0xFF.toByte() }

        assertThat(dump(bytes)).isEqualTo(expected("x_bin", "\"" + "\\xff".repeat(16) + "\"", "\"\\xff\""))
    }

    @Test
    fun `every byte value is escaped with two hex digits`() {
        val bytes = ByteArray(256) { it.toByte() }

        val lines = dump(bytes).lines()

        assertThat(lines.filter { it.startsWith("\"") }).hasSize(16)
        assertThat(lines[2]).startsWith("\"\\x00\\x01")
        assertThat(lines[17]).endsWith("\\xfe\\xff\"")
    }

    @Test
    fun `output uses LF line endings only`() {
        assertThat(dump(ByteArray(40) { 13 })).doesNotContain("\r\n")
    }

    @Test
    fun `symbol is used in the array and size declarations`() {
        val text = dump(byteArrayOf(1), symbol = "Dingbats_cff")

        assertThat(text).contains("const unsigned char _binary_Dingbats_cff[] =\n")
        assertThat(text).endsWith("const unsigned int _binary_Dingbats_cff_size = sizeof(_binary_Dingbats_cff) - 1;\n")
    }
}
