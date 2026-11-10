// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.ui.theme.titleMediumSerif
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import io.github.myxomatom.lutrin.feature.reader.render.fitPageWidth
import io.github.myxomatom.lutrin.feature.reader.render.pageHeightPx

/** Test tag of the preview card shown above the slider while it is dragged. */
internal const val PAGE_PREVIEW_TAG = "pagePreview"

/** Width of the preview card (mockup E2-Lecteur-Curseur: 148 dp). */
private val CardWidth = 148.dp

/** Padding around the page in the card. */
private val CardPadding = 10.dp

/** Corners of the card. */
private val CardCorner = 20.dp

/** Shadow of the card, above the floating bar's. */
private val CardElevation = 8.dp

/** Widest page in the card: the card minus its padding. */
private val PageMaxWidth = CardWidth - CardPadding * 2

/** Gap between the page and « Page N ». */
private val CaptionGap = 8.dp

/** Corners of the page in the card. */
private val PageCorner = 4.dp

/** Edge of the page in the card. */
private val PageEdgeWidth = 1.dp

/** Pages are white paper, whatever the theme of the app (F13 sets the document colours). */
private val Paper = Color.White

/** Edge of the paper (mockup: `#C3C6CF`), the same in both themes, like the paper itself. */
private val PaperEdge = Color(0xFFC3C6CF)

/**
 * Card shown above the thumb of the slider while it is dragged with the page preview on
 * (ADR 0018): the page aimed at, « Page N » and, once the outline exists (milestone M4), the
 * title of its section.
 *
 * The page is rendered by the engine through [renderer], off the main thread, at the size it is
 * shown; a blank sheet of paper stands in until it arrives. Aiming at another page cancels the
 * rendering under way. TalkBack does not announce the card on its own: the slider already reads
 * the page aimed at as its value.
 *
 * @param pageIndex zero-based page aimed at.
 * @param page its size, in points.
 * @param renderer draws the preview.
 * @param pageMaxHeight tallest page in the card, so that the card fits in the window.
 * @param modifier modifier applied to the card.
 * @param sectionTitle title of the section of the page; `null` shows none.
 */
@Composable
internal fun PagePreviewCard(
    pageIndex: Int,
    page: PageSize,
    renderer: PageRenderer,
    pageMaxHeight: Dp,
    modifier: Modifier = Modifier,
    sectionTitle: String? = null,
) {
    Surface(
        modifier = modifier.width(CardWidth).testTag(PAGE_PREVIEW_TAG),
        shape = RoundedCornerShape(CardCorner),
        color = MaterialTheme.colorScheme.surfaceBright,
        shadowElevation = CardElevation,
    ) {
        Column(
            Modifier.padding(CardPadding).semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PreviewPage(pageIndex, page, renderer, pageMaxHeight)
            Spacer(Modifier.height(CaptionGap))
            Text(
                stringResource(R.string.reader_page_preview_caption, pageIndex + 1),
                style = MaterialTheme.typography.titleMediumSerif,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            sectionTitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The page fitted in [PageMaxWidth] × [maxHeight], as rendered, or blank while it renders. */
@Composable
private fun PreviewPage(pageIndex: Int, page: PageSize, renderer: PageRenderer, maxHeight: Dp) {
    val density = LocalDensity.current
    val widthPx =
        with(density) { fitPageWidth(page, PageMaxWidth.roundToPx(), maxHeight.roundToPx(), margin = 0) }
    val heightPx = pageHeightPx(page, widthPx)
    val shape = RoundedCornerShape(PageCorner)
    Box(
        Modifier
            .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
            .clip(shape)
            .background(Paper)
            .border(PageEdgeWidth, PaperEdge, shape),
    ) {
        // One state per page: another page starts blank, never with the bitmap of the previous one.
        val bitmap = key(pageIndex, widthPx) { rememberPreview(renderer, pageIndex, widthPx) }
        bitmap?.let {
            val image = remember(it) { it.asImageBitmap() }
            Image(
                image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The preview of [pageIndex], at once when it is in the cache, otherwise once rendered. Leaving
 * the composition, or another page, cancels the rendering.
 */
@Composable
private fun rememberPreview(renderer: PageRenderer, pageIndex: Int, widthPx: Int): Bitmap? {
    val preview by produceState(renderer.cachedPreview(pageIndex, widthPx), renderer, pageIndex, widthPx) {
        if (value == null) value = renderer.renderPreview(pageIndex, widthPx)
    }
    return preview
}
