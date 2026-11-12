// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import io.github.myxomatom.lutrin.feature.reader.selection.SelectionHandle
import kotlin.math.hypot

// Geometry of the handles (F08, ADR 0024). Pure functions, tested without a screen.

/** On which side of the point it marks a handle hangs: away from the selected text. */
internal enum class HandleSide {
    /** The drop is to the left of its point, its tip at its top-right corner. */
    Left,

    /** The drop is to the right of its point, its tip at its top-left corner. */
    Right,
}

/**
 * One handle of a selection.
 *
 * @property handle which end of the selection it moves.
 * @property point the point of the page it marks: the bottom of the first or last character.
 * @property side where the drop hangs, away from the selected text.
 */
internal data class HandlePlacement(val handle: SelectionHandle, val point: PdfPoint, val side: HandleSide)

/**
 * The two handles of [selection], placed at its start and end in reading order: in right-to-left
 * text the start is on the right, so its drop hangs to the right, outside the selection, and the
 * end one to the left. Empty for an empty selection.
 */
internal fun selectionHandles(selection: TextSelection): List<HandlePlacement> {
    val start = selection.start
    val end = selection.end
    if (start == null || end == null || selection.quads.isEmpty()) return emptyList()
    val first = selection.quads.first()
    val last = selection.quads.last()
    val startSide = if (start.isNearerStartOf(first)) HandleSide.Left else HandleSide.Right
    val endSide = if (end.isNearerStartOf(last)) HandleSide.Left else HandleSide.Right
    return listOf(
        HandlePlacement(SelectionHandle.Start, start, startSide),
        HandlePlacement(SelectionHandle.End, end, endSide),
    )
}

/**
 * Whether the point is nearer the lower-left corner of [quad] than its lower-right one: the left
 * end of its line on screen, whatever the direction of the text.
 */
private fun PdfPoint.isNearerStartOf(quad: PdfQuad): Boolean = distanceTo(quad.lowerLeft) <= distanceTo(quad.lowerRight)

private fun PdfPoint.distanceTo(other: PdfPoint): Float = hypot(x - other.x, y - other.y)

/** Smallest rectangle around every area of [selection], in page coordinates; `null` when empty. */
internal fun selectionBounds(selection: TextSelection): PdfRect? = selection.quads
    .takeIf { it.isNotEmpty() }
    ?.let { quads ->
        PdfRect.enclosing(quads.flatMap { it.bounds.let { b -> listOf(b.left to b.top, b.right to b.bottom) } })
    }

/**
 * Where the action bar goes, in window pixels: centred above the selection when there is room
 * under [topLimit], otherwise below it, past its handles; always inside the window.
 *
 * @param anchor bounds of the selection in the window.
 * @param window size of the window.
 * @param content size of the action bar.
 * @param gapAbove space between the selection and the bar above it.
 * @param gapBelow space between the selection and the bar below it.
 * @param topLimit lowest y the bar may start at when above (status bar).
 */
@Suppress("LongParameterList") // Plain geometry inputs.
internal fun toolbarPosition(
    anchor: IntRect,
    window: IntSize,
    content: IntSize,
    gapAbove: Int,
    gapBelow: Int,
    topLimit: Int,
): IntOffset {
    val x = (anchor.center.x - content.width / 2).coerceIn(0, maxOf(0, window.width - content.width))
    val above = anchor.top - gapAbove - content.height
    val y = if (above >= topLimit) above else anchor.bottom + gapBelow
    return IntOffset(x, y.coerceIn(0, maxOf(0, window.height - content.height)))
}
