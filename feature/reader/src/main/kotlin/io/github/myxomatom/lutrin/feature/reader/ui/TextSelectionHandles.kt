// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import io.github.myxomatom.lutrin.core.ui.theme.LutrinReaderColors
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.selection.SelectionHandle
import kotlin.math.roundToInt

/** Radius of the drop drawn for a handle (Material text handles: 22 dp wide). */
private val HandleRadius = 11.dp

/** Touch target of a handle (`AGENTS.md` §11), centred on the drop. */
private val HandleTouchSize = 48.dp

/** Space between the selection and the action bar above it. */
private val ToolbarGap = 8.dp

/** Space between the selection and the action bar below it: room for the handles. */
private val ToolbarGapBelow = HandleRadius * 2 + 8.dp

/** Rounded corners of the action bar. */
private val ToolbarCorner = 8.dp

/** Elevation of the action bar over the page. */
private val ToolbarElevation = 3.dp

/** Opacity of « Copier » when the author forbids the copy (Material 3 disabled content: 38 %). */
private const val FORBIDDEN_ALPHA = 0.38f

// Drawing and gestures.

/**
 * What a page shows of the selection (F08): the selection itself, whether it may be copied, and
 * what its handles and action bar do.
 *
 * @property selection the selection, on this page, never empty.
 * @property canCopy whether « Copier » copies; otherwise it explains that the author forbids it.
 * @property actions the selection of the reader.
 */
@Immutable
internal class PageSelection(val selection: TextSelection, val canCopy: Boolean, val actions: ReaderSelection)

/** The selection on the page at [pageIndex], or `null` when it is on another page or there is none. */
@Composable
internal fun rememberPageSelection(selection: ReaderSelection, pageIndex: Int): PageSelection? {
    val current = selection.selection?.takeIf { it.pageIndex == pageIndex }
    return remember(current, selection) { current?.let { PageSelection(it, selection.canCopy, selection) } }
}

/**
 * Draws the areas of the selection over the rendering of the page, scaled to its displayed size,
 * like the search highlights ([SearchHighlightLayer]): paths built once per size and selection,
 * in the draw phase.
 */
@Composable
internal fun SelectionHighlightLayer(selection: TextSelection?, page: PageSize, modifier: Modifier = Modifier) {
    if (selection == null) return
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val path = selection.quads.toPath(size.width / page.width)
            onDrawBehind { drawPath(path, LutrinReaderColors.Selection) }
        },
    )
}

/**
 * The handles and the action bar of a selection, over a page displayed [width] × [height]
 * pixels wide. Not clipped by the page: a handle may hang below its last line.
 */
@Composable
internal fun TextSelectionOverlay(pageSelection: PageSelection, page: PageSize, width: Int, height: Int) {
    val density = LocalDensity.current
    val scale = width / page.width
    val overlay = remember { CoordinatesHolder() }
    val direction = LocalLayoutDirection.current
    // Page coordinates run left to right whatever the language of the interface.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        SelectionOverlayContent(
            pageSelection,
            page,
            scale,
            overlay,
            direction,
            Modifier.size(
                with(density) {
                    width.toDp()
                },
                with(density) { height.toDp() },
            ),
        )
    }
}

/** The content of [TextSelectionOverlay], laid out left to right; the action bar in [direction]. */
@Suppress("LongParameterList") // Plain wiring of TextSelectionOverlay.
@Composable
private fun SelectionOverlayContent(
    pageSelection: PageSelection,
    page: PageSize,
    scale: Float,
    overlay: CoordinatesHolder,
    direction: LayoutDirection,
    modifier: Modifier = Modifier,
) {
    Box(modifier.onPlaced { overlay.coordinates = it }) {
        selectionBounds(pageSelection.selection)?.let {
            SelectionToolbarAnchor(it, scale, pageSelection, direction)
        }
        selectionHandles(pageSelection.selection).forEach { placement ->
            TextSelectionHandle(
                placement = placement,
                anchor = Offset(placement.point.x * scale, placement.point.y * scale),
                overlay = overlay,
                onDragStart = { pageSelection.actions.onHandleDragStart(placement.handle) },
                onDrag = { position ->
                    val point = PdfPoint(
                        (position.x / scale).coerceIn(0f, page.width),
                        (position.y / scale).coerceIn(0f, page.height),
                    )
                    pageSelection.actions.onHandleDrag(point)
                },
                onDragEnd = pageSelection.actions.onHandleDragEnd,
            )
        }
    }
}

/** Live layout coordinates of the overlay, read by the handles while they move. */
private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

/**
 * One handle (`TextSelectionHandles`): a drop whose tip marks [anchor], in a 48 dp touch target.
 * Dragging it reports where its tip would be, keeping the distance between the finger and the
 * tip, so that the finger never hides the text it selects.
 */
@Suppress("LongParameterList") // A handle, where it is, and its three drag callbacks.
@Composable
private fun TextSelectionHandle(
    placement: HandlePlacement,
    anchor: Offset,
    overlay: CoordinatesHolder,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val density = LocalDensity.current
    val radius = with(density) { HandleRadius.toPx() }
    val touch = with(density) { HandleTouchSize.toPx() }
    val currentAnchor by rememberUpdatedState(anchor)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val handle = remember { CoordinatesHolder() }
    val dropCentre = anchor + Offset(if (placement.side == HandleSide.Left) -radius else radius, radius)
    val label =
        stringResource(
            if (placement.handle == SelectionHandle.Start) {
                R.string.reader_selection_start_handle
            } else {
                R.string.reader_selection_end_handle
            },
        )
    // Absolute: page coordinates do not mirror in a right-to-left interface.
    Spacer(
        Modifier
            .absoluteOffset {
                IntOffset((dropCentre.x - touch / 2).roundToInt(), (dropCentre.y - touch / 2).roundToInt())
            }
            .size(HandleTouchSize)
            .onPlaced { handle.coordinates = it }
            .semantics { contentDescription = label }
            .handleDrag(overlay, handle, { currentAnchor }, { currentOnDragStart() }, { currentOnDrag(it) }) {
                currentOnDragEnd()
            }
            .drawWithCache {
                val drop = handleDrop(Offset(size.width / 2, size.height / 2), radius, placement.side)
                onDrawBehind { drawPath(drop, LutrinReaderColors.SelectionHandle) }
            },
    )
}

/**
 * Drags a handle: reports where its tip would be, in the coordinates of [overlay], keeping the
 * distance between the finger and the tip measured when the finger went down.
 */
@Suppress("LongParameterList") // Where the handle is, and its three drag callbacks.
private fun Modifier.handleDrag(
    overlay: CoordinatesHolder,
    handle: CoordinatesHolder,
    anchor: () -> Offset,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        // Consumed: a press on a handle neither scrolls nor taps the page.
        down.consume()
        val grab = overlay.positionOf(handle, down.position) - anchor()
        onDragStart()
        drag(down.id) { change ->
            change.consume()
            onDrag(overlay.positionOf(handle, change.position) - grab)
        }
        onDragEnd()
    }
}

/**
 * [position], in the coordinates of [handle], in those of the overlay; computed from the live
 * layout, so that a handle moved by the selection it changes does not move the finger.
 */
private fun CoordinatesHolder.positionOf(handle: CoordinatesHolder, position: Offset): Offset {
    val overlay = coordinates?.takeIf { it.isAttached }
    val own = handle.coordinates?.takeIf { it.isAttached }
    return if (overlay != null && own != null) overlay.localPositionOf(own, position) else position
}

/** A drop centred on [centre]: a disc with a square corner, its tip towards the text. */
private fun handleDrop(centre: Offset, radius: Float, side: HandleSide): Path = Path().apply {
    addOval(Rect(centre, radius))
    val corner = if (side == HandleSide.Left) Offset(centre.x, centre.y - radius) else centre - Offset(radius, radius)
    addRect(Rect(corner, Size(radius, radius)))
}

/**
 * An invisible box over the selection, from which the action bar pops up above it, in its own
 * window: never clipped by the page or by the reading area.
 */
@Composable
private fun SelectionToolbarAnchor(
    bounds: PdfRect,
    scale: Float,
    pageSelection: PageSelection,
    direction: LayoutDirection,
) {
    val density = LocalDensity.current
    val topLimit = with(density) { ToolbarTopLimit.roundToPx() }
    val provider =
        remember(density, topLimit) {
            SelectionToolbarPosition(
                gapAbove = with(density) { ToolbarGap.roundToPx() },
                gapBelow = with(density) { ToolbarGapBelow.roundToPx() },
                topLimit = topLimit,
            )
        }
    Box(
        Modifier
            .absoluteOffset { IntOffset((bounds.left * scale).roundToInt(), (bounds.top * scale).roundToInt()) }
            .size(with(density) { (bounds.width * scale).toDp() }, with(density) { (bounds.height * scale).toDp() }),
    ) {
        Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false)) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) { SelectionActionBar(pageSelection) }
        }
    }
}

/** Keeps the action bar below the status bar and the top bar when it goes above the selection. */
private val ToolbarTopLimit: Dp = 88.dp

/** Places the action bar with [toolbarPosition]. */
private class SelectionToolbarPosition(val gapAbove: Int, val gapBelow: Int, val topLimit: Int) :
    PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = toolbarPosition(anchorBounds, windowSize, popupContentSize, gapAbove, gapBelow, topLimit)
}

/**
 * The action bar of a selection: « Copier » and « Tout sélectionner » (no « Partager », ADR
 * 0024). When the author forbids the copy, « Copier » stays reachable but dimmed, read as
 * forbidden by TalkBack, and explains why when pressed.
 */
@Composable
internal fun SelectionActionBar(pageSelection: PageSelection, modifier: Modifier = Modifier) {
    val forbidden = stringResource(R.string.reader_selection_copy_forbidden_state)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ToolbarCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = ToolbarElevation,
        tonalElevation = ToolbarElevation,
    ) {
        Row {
            TextButton(
                onClick = pageSelection.actions.onCopy,
                modifier = if (pageSelection.canCopy) Modifier else Modifier.semantics { stateDescription = forbidden },
            ) {
                val color = MaterialTheme.colorScheme.primary
                Text(
                    stringResource(R.string.reader_selection_copy),
                    color = if (pageSelection.canCopy) color else color.copy(alpha = FORBIDDEN_ALPHA),
                )
            }
            TextButton(onClick = pageSelection.actions.onSelectAll) {
                Text(stringResource(R.string.reader_selection_select_all))
            }
        }
    }
}
