// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.R as UiR
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Minimum width of the « 3 / 8 » indicator, so that the slider does not move between pages. */
private val PageIndicatorMinWidth = 56.dp

/** Gap between the elements of the bar. */
private val PageBarSpacing = 8.dp

/**
 * Bottom bar of the reader (E2): current page « n / total », a slider to jump through the
 * document (F05) and the fit-to-width action (F04).
 *
 * @param listState scroll state of the pages, read for the current page and moved by the slider.
 * @param pageCount number of pages.
 * @param onFitWidth called when the user asks to fit the page to the width again.
 * @param modifier modifier applied to the bar.
 */
@Composable
internal fun PageBar(listState: LazyListState, pageCount: Int, onFitWidth: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val currentPage by remember(listState) { derivedStateOf { listState.centerPageIndex() } }
    // While dragging, the thumb follows the finger rather than the (lagging) scroll position.
    var draggedPage by remember { mutableStateOf<Float?>(null) }
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.navigationBarsPadding().padding(
                horizontal = LutrinDimens.ScreenGutter,
                vertical = PageBarSpacing,
            ),
            horizontalArrangement = Arrangement.spacedBy(PageBarSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageIndicator(page = draggedPage?.roundToInt() ?: currentPage, pageCount = pageCount)
            if (pageCount > 1) {
                PageSlider(
                    value = draggedPage ?: currentPage.toFloat(),
                    pageCount = pageCount,
                    onValueChange = { value ->
                        draggedPage = value
                        scope.launch { listState.scrollToItem(value.roundToInt()) }
                    },
                    onValueChangeFinished = { draggedPage = null },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onFitWidth) {
                Icon(
                    painterResource(UiR.drawable.ic_fit_width),
                    contentDescription = stringResource(R.string.reader_fit_width),
                )
            }
        }
    }
}

/** « n / total », with [page] counted from 0. */
@Composable
private fun PageIndicator(page: Int, pageCount: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.reader_page_indicator, page + 1, pageCount),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = modifier.widthIn(min = PageIndicatorMinWidth),
    )
}

/** Height of the slider track (E2 mockup). */
private val SliderTrackHeight = 4.dp

/** Diameter of the round slider thumb (E2 mockup). */
private val SliderThumbSize = 20.dp

/**
 * Continuous slider over the pages, with the thin track and round thumb of the mockup instead of
 * the Material 3 defaults (thick track, bar thumb, one tick per page: unreadable on 500 pages).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageSlider(
    value: Float,
    pageCount: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val description = stringResource(R.string.reader_page_slider)
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = 0f..(pageCount - 1).toFloat(),
        interactionSource = interactionSource,
        thumb = {
            Box(Modifier.size(SliderThumbSize).background(MaterialTheme.colorScheme.primary, CircleShape))
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(SliderTrackHeight),
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
            )
        },
        modifier = modifier.semantics { contentDescription = description },
    )
}
