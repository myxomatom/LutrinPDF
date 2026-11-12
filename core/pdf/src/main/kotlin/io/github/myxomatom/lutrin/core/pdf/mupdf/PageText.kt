// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchExcerpt
import io.github.myxomatom.lutrin.core.domain.pdf.SelectionMode
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import kotlin.math.abs

/**
 * One character of a page, as the engine extracted it.
 *
 * @property codePoint Unicode code point.
 * @property quad area of the character, in page coordinates.
 * @property isRightToLeft whether it belongs to right-to-left text (Arabic, Hebrew…).
 */
internal data class Glyph(val codePoint: Int, val quad: PdfQuad, val isRightToLeft: Boolean = false) {
    /** Whether the character separates words. */
    val isWhitespace: Boolean get() = Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)

    /** Whether the character is a hyphen that may cut a word at the end of a line. */
    val isHyphen: Boolean get() = codePoint in HYPHENS
}

/**
 * One line of text, its characters in logical (reading) order.
 *
 * @property glyphs characters of the line, never empty.
 * @property direction unit vector of the writing direction, `(1, 0)` for horizontal text.
 */
internal class GlyphLine(val glyphs: List<Glyph>, val direction: PdfPoint = HORIZONTAL_TEXT) {
    init {
        require(glyphs.isNotEmpty()) { "A line has at least one character" }
    }
}

/** Writing direction of horizontal text. */
internal val HORIZONTAL_TEXT = PdfPoint(1f, 0f)

/** Code points of the hyphens that MuPDF also treats as such (`fz_is_unicode_hyphen`). */
private val HYPHENS = setOf('-'.code, 0x00AD, 0x2010, 0x2011)

/** Separator of lines in a selection. */
private const val LINE_BREAK = "\n"

/** Separator of lines in a search excerpt, which is read on one line. */
private const val JOINED_LINE = " "

/**
 * Text of one page, platform free: everything selection and search excerpts need, computed in
 * Kotlin from the characters MuPDF extracted (ADR 0020).
 *
 * A *caret* is a position between two characters, numbered over the whole page in logical
 * order: caret `i` is just before the `i`-th character. Working with carets rather than points
 * keeps a selection exact in right-to-left text, where MuPDF's own selection, which goes back
 * and forth between carets and points, loses the first letter of a word.
 */
internal class PageText(private val lines: List<GlyphLine>) {
    private val glyphs: List<Glyph> = lines.flatMap { it.glyphs }

    /** Index of the first character of each line, then the number of characters. */
    private val lineStarts: IntArray =
        lines.runningFold(0) { start, line -> start + line.glyphs.size }.toIntArray()

    /** Line of each character. */
    private val lineOfGlyph: IntArray =
        lines.flatMapIndexed { line, glyphLine -> List(glyphLine.glyphs.size) { line } }.toIntArray()

    private val extents: List<LineExtent> = lines.map(::LineExtent)

    // Centres of the characters, computed once: every search result tests them.
    private val centerX = FloatArray(glyphs.size) { glyphs[it].quad.centerX() }
    private val centerY = FloatArray(glyphs.size) { glyphs[it].quad.centerY() }

    /** Whether the page has no text at all (scanned page). */
    val isEmpty: Boolean get() = glyphs.isEmpty()

    /**
     * Whether [point] lies on a line of text, between its first and last characters: `false` in
     * a margin, between paragraphs or on an image, so that a long press there selects nothing.
     */
    fun isOnText(point: PdfPoint): Boolean = extents.any { it.contains(point) }

    /**
     * Selects the text between [start] and [end] (in any order), grown according to [mode].
     *
     * @param pageIndex page reported in the result.
     */
    fun select(pageIndex: Int, start: PdfPoint, end: PdfPoint, mode: SelectionMode): TextSelection {
        val range = if (isEmpty) IntRange.EMPTY else selectedRange(start, end, mode)
        return if (range.isEmpty()) {
            TextSelection.empty(pageIndex)
        } else {
            TextSelection(
                pageIndex = pageIndex,
                quads = quadsOf(range),
                text = flatten(range, LINE_BREAK).text,
                start = glyphs[range.first].leadingCorner(),
                end = glyphs[range.last].trailingCorner(),
            )
        }
    }

    /** Characters selected between [start] and [end]; the page must have text. */
    private fun selectedRange(start: PdfPoint, end: PdfPoint, mode: SelectionMode): IntRange {
        val (first, last) = listOf(caretAt(start), caretAt(end)).sortedBy { it.index }
        return when (mode) {
            SelectionMode.Characters -> first.index until last.index
            SelectionMode.Words -> wordStart(first) until wordEnd(last)
            SelectionMode.Lines -> lineStarts[first.line] until lineStarts[last.line + 1]
        }
    }

    /**
     * The search excerpt of the occurrence covering [areas], or `null` if no character lies in
     * them. The context is read on one line: lines are joined by a space, or by nothing after a
     * hyphen, which is then dropped, as MuPDF's search does (unless the occurrence ends on it).
     */
    fun excerpt(areas: List<PdfQuad>, maxContext: Int = SearchExcerpt.MAX_CONTEXT_LENGTH): SearchExcerpt? {
        val matched = matchedGlyphs(areas.map { it.bounds })
        if (matched.isEmpty()) return null
        // Enough characters on each side to fill the context once whitespace is collapsed.
        val margin = maxContext * 2
        val from = (matched.first - margin).coerceAtLeast(0)
        val to = (matched.last + 1 + margin).coerceAtMost(glyphs.size)
        val flat = flatten(from until to, JOINED_LINE, keepHyphenAt = matched.last)
        val matchStart = flat.offsets[matched.first - from]
        val matchEnd = flat.offsets[matched.last - from] + Character.charCount(glyphs[matched.last].codePoint)
        return SearchExcerpt.around(flat.text, matchStart, matchEnd, maxContext)
    }

    /**
     * Characters whose centre lies in one of [areas], from the first to the last, without the
     * spaces at either end: a hit area may also cover the space next to the occurrence. Only the
     * lines that the areas touch are looked at.
     */
    private fun matchedGlyphs(areas: List<PdfRect>): IntRange {
        val matched = lines.indices
            .filter { line -> areas.any { extents[line].bounds.intersects(it) } }
            .flatMap { line -> lineStarts[line] until lineStarts[line + 1] }
            .filter { index ->
                !glyphs[index].isWhitespace && areas.any { it.contains(centerX[index], centerY[index]) }
            }
        return if (matched.isEmpty()) IntRange.EMPTY else matched.first()..matched.last()
    }

    /**
     * Text of [range], with [lineSeparator] between lines; with [JOINED_LINE], a hyphen ending a
     * line is dropped and the lines joined directly, except the hyphen at [keepHyphenAt].
     */
    private fun flatten(range: IntRange, lineSeparator: String, keepHyphenAt: Int = -1): FlatText {
        val offsets = IntArray(range.count())
        val text = buildString {
            for (index in range) {
                if (index > range.first && lineOfGlyph[index] != lineOfGlyph[index - 1]) {
                    val previous = glyphs[index - 1]
                    if (lineSeparator == JOINED_LINE && previous.isHyphen && index - 1 != keepHyphenAt) {
                        setLength(length - Character.charCount(previous.codePoint))
                    } else {
                        append(lineSeparator)
                    }
                }
                offsets[index - range.first] = length
                appendCodePoint(glyphs[index].codePoint)
            }
        }
        return FlatText(text, offsets)
    }

    private fun wordStart(caret: Caret): Int {
        var index = caret.index
        while (index > lineStarts[caret.line] && !glyphs[index - 1].isWhitespace) index--
        return index
    }

    private fun wordEnd(caret: Caret): Int {
        var index = caret.index
        while (index < lineStarts[caret.line + 1] && !glyphs[index].isWhitespace) index++
        return index
    }

    /** One quad per line of [range], covering its characters along the line. */
    private fun quadsOf(range: IntRange): List<PdfQuad> = range
        .groupBy { lineOfGlyph[it] }
        .map { (line, indices) -> lineQuad(lines[line].direction, indices.map { glyphs[it] }) }

    /**
     * The caret nearest to [point]: on the line whose band holds the point (the one whose middle
     * is nearest when bands overlap), or else the closest line across, at the character edge
     * nearest along that line.
     */
    private fun caretAt(point: PdfPoint): Caret {
        val line = extents.indices.minWith(
            compareBy<Int> { extents[it].distanceAcross(point) }
                .thenBy { extents[it].distanceAlong(point) }
                .thenBy { extents[it].distanceFromMiddle(point) },
        )
        val direction = lines[line].direction
        val position = point.along(direction)
        var best = lineStarts[line]
        var bestDistance = Float.MAX_VALUE
        lines[line].glyphs.forEachIndexed { offset, glyph ->
            val (leading, trailing) = glyph.edgesAlong(direction)
            if (abs(position - leading) < bestDistance) {
                bestDistance = abs(position - leading)
                best = lineStarts[line] + offset
            }
            if (abs(position - trailing) < bestDistance) {
                bestDistance = abs(position - trailing)
                best = lineStarts[line] + offset + 1
            }
        }
        return Caret(best, line)
    }

    /**
     * A caret and the line it belongs to: the caret at the end of a line is also the one at the
     * start of the next, so the line must be kept to grow the selection on the right one.
     */
    private data class Caret(val index: Int, val line: Int)

    /** Text of a range of characters and where each character starts in it. */
    private class FlatText(val text: String, val offsets: IntArray)

    /** Extent of a line along and across its writing direction. */
    private class LineExtent(line: GlyphLine) {
        private val direction = line.direction
        private val corners = line.glyphs.flatMap { it.quad.corners() }
        private val alongMin = corners.minOf { it.along(direction) }
        private val alongMax = corners.maxOf { it.along(direction) }
        private val acrossMin = corners.minOf { it.across(direction) }
        private val acrossMax = corners.maxOf { it.across(direction) }

        /** Axis-aligned rectangle around the line. */
        val bounds: PdfRect = PdfRect.enclosing(corners.map { it.x to it.y })

        fun distanceAcross(point: PdfPoint): Float = distanceOutside(point.across(direction), acrossMin, acrossMax)

        fun distanceAlong(point: PdfPoint): Float = distanceOutside(point.along(direction), alongMin, alongMax)

        fun distanceFromMiddle(point: PdfPoint): Float = abs(point.across(direction) - (acrossMin + acrossMax) / 2)

        fun contains(point: PdfPoint): Boolean = distanceAcross(point) == 0f && distanceAlong(point) == 0f
    }
}

/** Leading and trailing edges of the glyph along [direction], in reading order. */
private fun Glyph.edgesAlong(direction: PdfPoint): Pair<Float, Float> {
    val positions = quad.corners().map { it.along(direction) }
    return if (isRightToLeft) positions.max() to positions.min() else positions.min() to positions.max()
}

/** Bottom corner on the side where reading enters the glyph: left, or right in right-to-left text. */
private fun Glyph.leadingCorner(): PdfPoint = if (isRightToLeft) quad.lowerRight else quad.lowerLeft

/** Bottom corner on the side where reading leaves the glyph. */
private fun Glyph.trailingCorner(): PdfPoint = if (isRightToLeft) quad.lowerLeft else quad.lowerRight
