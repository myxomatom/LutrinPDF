// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlin.math.roundToInt

/** Height of the floating bar (mockup E2 of `docs/design/proposals/`). */
private val BarHeight = 64.dp

/** Space between the floating bar and the edges of the safe area, and above the bar. */
private val BarMargin = 16.dp

/** Padding before the page indicator, inside the bar. */
private val BarStartPadding = 20.dp

/** Padding after the fit button, inside the bar. */
private val BarEndPadding = 8.dp

/** Gap between the indicator, the slider and the fit button. */
private val BarSpacing = 12.dp

/** Shadow lifting the bar off the document that scrolls beneath it. */
private val BarElevation = 6.dp

/** Narrowest page indicator, so that the slider does not jump between « 9 / 12 » and « 10 / 12 ». */
private val IndicatorMinWidth = 52.dp

/** Thickness of the slider track. */
private val TrackHeight = 4.dp

/** Diameter of the thumb at rest; also its size in the slider layout. */
private val ThumbSize = 20.dp

/** Diameter of the thumb while it is dragged. */
private val ActiveThumbSize = 24.dp

/** Diameter of the halo around the thumb while it is dragged. */
private val HaloSize = 40.dp

/** Opacity of the primary colour in the halo of the thumb. */
private const val HALO_ALPHA = 0.2f

/** Gap between the preview card and the bar. */
private val PreviewGap = 16.dp

/** Height of the preview card around its page: padding, gap and caption (mockup: 10 + 8 + 24 + 10). */
private val PreviewChrome = 52.dp

/** Smallest preview page, in a landscape window too low for the full size. */
private val MinPreviewHeight = 48.dp

/**
 * Room the floating bar takes at the bottom of the reading area: the bottom system bar and
 * cutout, the bar and a margin on both sides of it. The pages get as much free space at the end
 * of the document, so that the last one can scroll above the bar and be read whole.
 */
@Composable
internal fun floatingPageBarClearance(): Dp =
    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding() +
        BarMargin + BarHeight + BarMargin

/**
 * Floating bar of the reader (E2, ADR 0018): a pill above the document with « page / count »,
 * a thin slider to jump through the document (F05) and the fit button (F04). The document
 * scrolls beneath it. With the page preview on, dragging the slider shows a card above the
 * thumb with the page aimed at ([PagePreviewCard]); the document moves on release
 * ([PageScrubber]).
 *
 * The bar keeps clear of the system bars, the gesture area and the cutout; it is meant to be
 * aligned at the bottom of the reading area.
 *
 * @param page zero-based page shown by the document.
 * @param pageSizes size of every page; no slider for a document of one page.
 * @param renderer draws the preview of the page aimed at.
 * @param scrubber state of the slider gesture, shared with the screen that dims the document.
 * @param onGoTo shows a zero-based page in the document, without animation.
 * @param fitLabel description of the fit button: « fit to width » or « fit page ».
 * @param onFit brings the zoom back to 1.
 * @param previewMaxHeight tallest page in the preview card, so that it fits above the bar.
 * @param modifier modifier applied to the bar and its preview.
 * @param sectionTitle title of the section of a page, shown under « Page N » in the preview when
 *   the document has an outline (milestone M4); `null` shows none.
 */
@Suppress("LongParameterList") // The bar gathers three controls and the preview of the slider.
@Composable
internal fun FloatingPageBar(
    page: Int,
    pageSizes: List<PageSize>,
    renderer: PageRenderer,
    scrubber: PageScrubber,
    onGoTo: (Int) -> Unit,
    fitLabel: String,
    onFit: () -> Unit,
    previewMaxHeight: Dp,
    modifier: Modifier = Modifier,
    sectionTitle: (Int) -> String? = { null },
) {
    // A bar removed in the middle of a gesture (full screen, new document) does not move the document.
    DisposableEffect(scrubber) { onDispose { scrubber.cancel() } }
    val thumbAnchor = remember { ThumbAnchor() }
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(BarMargin)
            .onPlaced { thumbAnchor.bar = it },
    ) {
        scrubber.previewPage?.let { aimed ->
            PagePreviewCard(
                pageIndex = aimed,
                page = pageSizes[aimed],
                renderer = renderer,
                pageMaxHeight = (previewMaxHeight - PreviewChrome - PreviewGap).coerceAtLeast(MinPreviewHeight),
                sectionTitle = sectionTitle(aimed),
                modifier = Modifier.centeredAt { thumbAnchor.centerX },
            )
            Spacer(Modifier.height(PreviewGap))
        }
        Bar(page, pageSizes.size, scrubber, onGoTo, fitLabel, onFit, thumbAnchor)
    }
}

/** The pill itself: indicator, slider and fit button. */
@Suppress("LongParameterList") // Plain wiring of the state of FloatingPageBar.
@Composable
private fun Bar(
    page: Int,
    pageCount: Int,
    scrubber: PageScrubber,
    onGoTo: (Int) -> Unit,
    fitLabel: String,
    onFit: () -> Unit,
    thumbAnchor: ThumbAnchor,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceBright,
        shadowElevation = BarElevation,
    ) {
        Row(
            Modifier.fillMaxWidth().height(BarHeight).padding(start = BarStartPadding, end = BarEndPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BarSpacing),
        ) {
            val shown = scrubber.aimedPage ?: page
            val colors = MaterialTheme.colorScheme
            Text(
                stringResource(R.string.reader_page_indicator, shown + 1, pageCount),
                style = MaterialTheme.typography.titleSmall,
                color = if (scrubber.isPreviewing) colors.primary else colors.onSurface,
                modifier = Modifier.widthIn(min = IndicatorMinWidth),
            )
            if (pageCount > 1) {
                PageSlider(shown, pageCount, scrubber, onGoTo, thumbAnchor, Modifier.weight(1f))
            } else {
                Box(Modifier.weight(1f))
            }
            IconButton(
                onClick = onFit,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                Icon(painterResource(R.drawable.ic_fit_width), contentDescription = fitLabel)
            }
        }
    }
}

/**
 * The Material 3 slider with a thin track and a round thumb (ADR 0018): no stop indicator, no
 * tick, no gap around the thumb. TalkBack reads « Go to page » and « Page x of n », and moves it
 * one page at a time. The track with a slider state is still experimental in Material 3 1.4; it is
 * the only way to drop the stop indicator and the gap around the thumb without redrawing a slider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageSlider(
    shown: Int,
    pageCount: Int,
    scrubber: PageScrubber,
    onGoTo: (Int) -> Unit,
    thumbAnchor: ThumbAnchor,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.reader_page_slider)
    val value = stringResource(R.string.reader_page_slider_value, shown + 1, pageCount)
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()
    val colors =
        SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
        )
    Slider(
        value = shown.toFloat(),
        onValueChange = { scrubber.onSlide(it.roundToInt())?.let(onGoTo) },
        onValueChangeFinished = { scrubber.onRelease()?.let(onGoTo) },
        valueRange = 0f..(pageCount - 1).toFloat(),
        interactionSource = interactionSource,
        colors = colors,
        thumb = { SliderThumb(active = dragged || scrubber.isPreviewing, thumbAnchor) },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                modifier = Modifier.height(TrackHeight),
                colors = colors,
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = TrackHeight / 2,
            )
        },
        modifier =
            modifier
                .cancelPreviewOnGestureCancel(scrubber)
                .semantics {
                    contentDescription = label
                    stateDescription = value
                },
    )
}

/**
 * Round thumb, 20 dp at rest; while dragged, 24 dp in a 40 dp halo drawn around it. Its layout
 * is 20 dp wide (the track runs from the centre of the thumb at one end to the other) but 48 dp
 * high: the Material slider is as high as its thumb, and this keeps its touch target at 48 dp.
 */
@Composable
private fun SliderThumb(active: Boolean, thumbAnchor: ThumbAnchor) {
    val color = MaterialTheme.colorScheme.primary
    Spacer(
        Modifier
            .size(width = ThumbSize, height = LutrinDimens.TouchTargetMin)
            .onGloballyPositioned { thumbAnchor.update(it) }
            .drawBehind {
                if (active) {
                    drawCircle(color.copy(alpha = HALO_ALPHA), radius = HaloSize.toPx() / 2)
                    drawCircle(color, radius = ActiveThumbSize.toPx() / 2)
                } else {
                    drawCircle(color, radius = ThumbSize.toPx() / 2)
                }
            },
    )
}

/**
 * Where the thumb is, horizontally, in the bar: the preview card is centred on it.
 *
 * [bar] is a plain property, set at each placement of the bar; [centerX] is snapshot state, read
 * only when the card is placed, so that a move of the thumb places the card again without
 * composing anything.
 */
private class ThumbAnchor {
    /** Coordinates of the bar and its preview. */
    var bar: LayoutCoordinates? = null

    private val center = mutableFloatStateOf(Float.NaN)

    /** Centre of the thumb in [bar], in pixels; `null` before the first layout. */
    val centerX: Float? get() = center.floatValue.takeUnless { it.isNaN() }

    /** Records the position of the [thumb]. */
    fun update(thumb: LayoutCoordinates) {
        val bar = bar?.takeIf { it.isAttached } ?: return
        center.floatValue = bar.localPositionOf(thumb, Offset(thumb.size.width / 2f, 0f)).x
    }
}

/**
 * Places the element centred on [centerX] (the middle of the available width when `null`),
 * within the available width: at the ends of the slider, the card stays inside the bar.
 */
private fun Modifier.centeredAt(centerX: () -> Float?): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0))
    val width = constraints.maxWidth
    layout(width, placeable.height) {
        val center = centerX() ?: (width / 2f)
        val x = (center - placeable.width / 2f).roundToInt()
        placeable.place(x.coerceIn(0, (width - placeable.width).coerceAtLeast(0)), 0)
    }
}

/**
 * Cancels the preview when the system cancels the gesture on the slider (another window takes
 * the touch, for example). Compose ends such a gesture with its pointers lifted and already
 * consumed; seen first, in the initial pass, before the slider reports the end of its drag, so
 * that the release that follows goes nowhere and the document stays on its page.
 */
private fun Modifier.cancelPreviewOnGestureCancel(scrubber: PageScrubber): Modifier = pointerInput(scrubber) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val lifted = !event.changes.fastAny { it.pressed }
            if (lifted && event.changes.fastAll { it.isConsumed && it.previousPressed }) scrubber.cancel()
        }
    }
}
