// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention.mupdf

import java.io.InputStream
import java.io.OutputStream

/**
 * Kotlin port of MuPDF's `scripts/hexdump.sh`, which turns a binary resource (font,
 * hyphenation patterns) into a C source embedding it as a byte array.
 *
 * The output is byte for byte the one of the script (`od -v -An -tx1 | sed …`): a comment
 * line, the array declaration, one quoted string literal per 16 input bytes written as
 * lowercase `\xhh` escapes, then the size constant. Lines end with `\n` on every platform.
 * Porting it lets the build run without `bash`, `od` and `sed` (Windows, ADR 0013).
 */
internal object HexDump {
    /** Number of input bytes per output line, as printed by `od -tx1`. */
    private const val BYTES_PER_LINE = 16

    private const val HEADER = "// This is an automatically generated file. Do not edit.\n"

    /** `\xhh` escape of every byte value, so that the hot loop only copies bytes. */
    private val ESCAPES: Array<ByteArray> =
        Array(BYTE_VALUES) { value -> "\\x%02x".format(value).toByteArray(Charsets.US_ASCII) }

    /**
     * C identifier suffix the script derives from [fileName]: every `.` and `-` becomes `_`
     * (`NimbusSans-Bold.cff` gives `NimbusSans_Bold_cff`). Other characters are kept as is,
     * like the script's `sed 's/[.-]/_/g'`.
     */
    fun symbolName(fileName: String): String = fileName.replace('.', '_').replace('-', '_')

    /**
     * Writes to [output] the C source embedding every byte of [input] under the symbols
     * `_binary_<symbol>` and `_binary_<symbol>_size`. Neither stream is closed.
     *
     * @param symbol identifier suffix, normally [symbolName] of the input file name.
     * @throws java.io.IOException if reading or writing fails.
     */
    fun write(input: InputStream, symbol: String, output: OutputStream) {
        output.write(ascii(HEADER + "const unsigned char _binary_$symbol[] =\n"))
        val chunk = ByteArray(BYTES_PER_LINE)
        while (true) {
            val count = input.readNBytes(chunk, 0, BYTES_PER_LINE)
            if (count == 0) break
            writeLine(chunk, count, output)
        }
        output.write(ascii(";\nconst unsigned int _binary_${symbol}_size = sizeof(_binary_$symbol) - 1;\n"))
    }

    private fun writeLine(chunk: ByteArray, count: Int, output: OutputStream) {
        output.write(QUOTE)
        for (index in 0 until count) {
            output.write(ESCAPES[chunk[index].toInt() and BYTE_MASK])
        }
        output.write(QUOTE)
        output.write(NEWLINE)
    }

    private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.US_ASCII)
}

private const val BYTE_VALUES = 256
private const val BYTE_MASK = 0xff
private const val QUOTE = '"'.code
private const val NEWLINE = '\n'.code
