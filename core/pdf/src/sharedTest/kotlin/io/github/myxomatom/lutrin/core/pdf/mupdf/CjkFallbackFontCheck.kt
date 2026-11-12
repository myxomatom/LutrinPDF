// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Font
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.StructuredTextWalker
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage

/**
 * Checks that the CJK text of `cjk_not_embedded.pdf`, whose fonts are not embedded, is drawn
 * with real glyphs and not with empty boxes (ADR 0025). Shared by the JVM test (desktop library,
 * which has the Android font set and no system font) and the instrumented test (Android library).
 *
 * Three checks, because each alone can be fooled:
 * - the fonts of the file are really not embedded (the file tests what it claims);
 * - every character is drawn with a font that has a glyph for it: when no CJK font is found,
 *   MuPDF falls back to a Latin font that has none, and draws boxes or wrong glyphs;
 * - every line leaves ink on the page.
 */
internal object CjkFallbackFontCheck {
    /**
     * Path of the test file in the assets of `core:testing` (`TestFiles.Pdf.CJK_NOT_EMBEDDED`,
     * which the JVM tests of this module cannot see).
     */
    const val PATH = "pdfs/cjk_not_embedded.pdf"

    /** A line of the test file: font resource, text, and band of the page it is drawn in (points). */
    data class Line(val font: String, val text: String, val top: Int, val bottom: Int)

    /** Lines of the test file, as written by `generate_test_files.py` (`CJK_LINES`). */
    val lines =
        listOf(
            Line("china-s", "简体中文汉字", top = 76, bottom = 105),
            Line("china-t", "繁體中文漢字", top = 136, bottom = 165),
            Line("japan", "日本語ひらがなカタカナ", top = 196, bottom = 225),
            Line("korea", "한국어한글", top = 256, bottom = 285),
        )

    private val embeddedFontKeys = listOf("FontFile", "FontFile2", "FontFile3")

    /** Gray level (0 black, 255 white) under which a pixel counts as ink. */
    private const val INK_LEVEL = 0x80

    /** A 24 pt line of 5 to 11 CJK letters leaves well over this many ink pixels at 72 dpi. */
    private const val MIN_INK_PIXELS_PER_LINE = 300

    /**
     * Opens the file at [path] with MuPDF on the calling thread, runs the three checks and
     * releases everything.
     *
     * @throws AssertionError if a check fails.
     */
    fun verify(path: String) {
        val document = Document.openDocument(path)
        try {
            val page = document.loadPage(0)
            try {
                assertFontsAreNotEmbedded(page as PDFPage)
                assertEveryCharacterHasAGlyph(page)
                assertEveryLineHasInk(page)
            } finally {
                page.destroy()
            }
        } finally {
            document.destroy()
        }
    }

    private fun assertFontsAreNotEmbedded(page: PDFPage) {
        val fonts = page.getObject().get("Resources").get("Font")
        lines.forEach { line ->
            val descriptor = fonts.get(line.font).get("DescendantFonts").get(0).get("FontDescriptor")
            assertWithMessage("font descriptor of ${line.font}").that(descriptor.isNull).isFalse()
            embeddedFontKeys.forEach { key ->
                assertWithMessage("${line.font} has /$key").that(descriptor.get(key).isNull).isTrue()
            }
        }
    }

    private fun assertEveryCharacterHasAGlyph(page: Page) {
        val characters = drawnCharacters(page)
        assertThat(buildString { characters.forEach { appendCodePoint(it.codePoint) } })
            .isEqualTo(lines.joinToString("") { it.text })
        val withoutGlyph = characters.filterNot { it.fontHasGlyph }.map { "U+%04X".format(it.codePoint) }
        assertWithMessage("characters drawn without a glyph (boxes)").that(withoutGlyph).isEmpty()
    }

    private fun assertEveryLineHasInk(page: Page) {
        val pixmap = page.toPixmap(Matrix.Identity(), ColorSpace.DeviceGray, false)
        try {
            val samples = pixmap.samples
            lines.forEach { line ->
                val ink = (line.top until line.bottom).sumOf { row ->
                    (0 until pixmap.width).count { column ->
                        (samples[row * pixmap.stride + column].toInt() and BYTE_MASK) < INK_LEVEL
                    }
                }
                assertWithMessage("ink pixels of ${line.font}").that(ink).isAtLeast(MIN_INK_PIXELS_PER_LINE)
            }
        } finally {
            pixmap.destroy()
        }
    }

    private fun drawnCharacters(page: Page): List<DrawnCharacter> {
        val text = page.toStructuredText()
        try {
            val recorder = CharacterRecorder()
            text.walk(recorder)
            return recorder.characters
        } finally {
            text.destroy()
        }
    }

    private const val BYTE_MASK = 0xFF
}

/** A character of the page and whether the font that draws it has a glyph for it. */
private data class DrawnCharacter(val codePoint: Int, val fontHasGlyph: Boolean)

/** Records the non-blank characters of a structured text, with their font's coverage. */
private class CharacterRecorder : StructuredTextWalker {
    val characters = mutableListOf<DrawnCharacter>()

    override fun onChar(c: Int, origin: Point?, font: Font, size: Float, q: Quad?, argb: Int, flags: Int, bidi: Int) {
        // Glyph 0 is .notdef: the font has no glyph for this character.
        if (!Character.isWhitespace(c)) characters += DrawnCharacter(c, font.encodeCharacter(c) != 0)
    }

    override fun onImageBlock(bbox: Rect?, transform: Matrix?, image: Image?) = Unit

    override fun beginTextBlock(bbox: Rect?, flags: Int) = Unit

    override fun endTextBlock() = Unit

    override fun beginLine(bbox: Rect?, wmode: Int, dir: Point?) = Unit

    override fun endLine() = Unit

    override fun beginStruct(standard: String?, raw: String?, index: Int) = Unit

    override fun endStruct() = Unit

    override fun onVector(bbox: Rect?, info: StructuredTextWalker.VectorInfo?, argb: Int) = Unit
}
