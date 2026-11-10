// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.R as CoreUiR

/** Corner radius and edge of a thumbnail (E1 mock-up). */
private val ThumbnailCorner = 4.dp
private val ThumbnailBorder = 1.dp

/**
 * Colours of a page: white with a grey edge in both themes, like the mock-ups, because the theme
 * of the application never changes how a document is drawn (F13, `docs/design/proposals/`).
 */
private val PaperColor = Color.White
private val PaperEdgeColor = Color(0xFFC3C6CF)

/** Edge of the page in the resume card: bluish, to sit on its `primaryContainer` background. */
private val CardPaperEdgeColor = Color(0xFFB4C8E6)

/** Grey lines standing for the text of a page while its picture is missing (E1 mock-ups). */
private val PlaceholderHeadingColor = Color(0xFF8A8E96)
private val PlaceholderLineColor = Color(0xFFD9DCE2)

/** Lock badge of a protected document, overlapping the corner of its thumbnail (E1 mock-up). */
private val LockBadgeSize = 24.dp
private val LockBadgeIcon = 14.dp
private val LockBadgeOverlap = 6.dp
private val LockBadgeRing = 2.dp

/** Lines drawn in a thumbnail that is not available yet, as fractions of its width. */
private val PlaceholderLines = listOf(0.6f, 1f, 1f, 0.8f, 1f, 0.5f)

/** Placeholder lines of a thumbnail as fractions of the frame width, so that both sizes look alike. */
private const val PLACEHOLDER_PADDING_RATIO = 0.14f
private const val PLACEHOLDER_LINE_RATIO = 0.05f
private const val PLACEHOLDER_GAP_RATIO = 0.085f

/**
 * The picture of the first page of a recent document, or lines in its place while it is
 * missing, with the lock of a protected document (E1 mock-up, ADR 0015, 0017, 0018). Asks for the
 * picture when it is missing or outdated. The page is white in both themes, like paper.
 *
 * The picture is decoded for the frame at the screen density, no larger ([ThumbnailLoader]).
 *
 * @param document the recent document.
 * @param placement row or card: size and colours of the frame.
 * @param loadThumbnail reads a thumbnail file for a frame of the given size in pixels.
 * @param onRefreshThumbnail called for a document shown with a missing or outdated thumbnail.
 * @param modifier modifier applied to the frame, lock included (rotation, shadow, semantics).
 */
@Composable
internal fun RecentThumbnail(
    document: RecentDocumentItem,
    placement: ThumbnailPlacement,
    loadThumbnail: suspend (String, IntSize) -> ImageBitmap?,
    onRefreshThumbnail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = placement.size
    val picture = rememberThumbnail(document, placement, loadThumbnail, onRefreshThumbnail)
    val shape = RoundedCornerShape(ThumbnailCorner)
    val edge = if (placement == ThumbnailPlacement.InCard) CardPaperEdgeColor else PaperEdgeColor
    val frame = Modifier
        .size(size)
        .clip(shape)
        .background(PaperColor)
        .border(ThumbnailBorder, edge, shape)
    // A protected document is never drawn (ADR 0017): an old picture is not shown either.
    val image = picture?.takeUnless { document.isProtected }
    Box(modifier) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.library_thumbnail, document.name),
                contentScale = ContentScale.Fit,
                modifier = frame,
            )
        } else {
            ThumbnailPlaceholder(size.width, frame)
        }
        if (document.isProtected) {
            LockBadge(placement, Modifier.align(Alignment.BottomEnd).offset(LockBadgeOverlap, LockBadgeOverlap))
        }
    }
}

/**
 * The picture of [document] decoded for the frame of [placement], or `null` while it is loading
 * or missing; asks for it to be drawn (again) when it is missing or outdated.
 */
@Composable
private fun rememberThumbnail(
    document: RecentDocumentItem,
    placement: ThumbnailPlacement,
    loadThumbnail: suspend (String, IntSize) -> ImageBitmap?,
    onRefreshThumbnail: (String) -> Unit,
): ImageBitmap? {
    val path = document.thumbnailPath
    val size = placement.size
    val pixels = with(LocalDensity.current) { IntSize(size.width.roundToPx(), size.height.roundToPx()) }
    val currentLoad by rememberUpdatedState(loadThumbnail)
    val currentOnOutdated by rememberUpdatedState(onRefreshThumbnail)
    val picture by produceState<ImageBitmap?>(null, path, pixels) { value = path?.let { currentLoad(it, pixels) } }
    LaunchedEffect(document.documentId, document.thumbnailOutdated, document.openedOn) {
        // Never for a protected document, which cannot be opened without its password (ADR 0017).
        if (document.thumbnailOutdated && !document.isProtected) currentOnOutdated(document.documentId)
    }
    return picture
}

/**
 * The lock of a protected document; its description is read with the row. A ring of the colour
 * behind the thumbnail detaches it from the page; on the card, whose background is
 * `primaryContainer`, the badge takes `primary` to stay visible.
 */
@Composable
private fun LockBadge(placement: ThumbnailPlacement, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val onCard = placement == ThumbnailPlacement.InCard
    Box(
        modifier = modifier
            .size(LockBadgeSize)
            .background(if (onCard) colors.primaryContainer else colors.surface, CircleShape)
            .padding(LockBadgeRing)
            .background(if (onCard) colors.primary else colors.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(CoreUiR.drawable.ic_lock),
            contentDescription = stringResource(R.string.library_protected),
            tint = if (onCard) colors.onPrimary else colors.onPrimaryContainer,
            modifier = Modifier.size(LockBadgeIcon),
        )
    }
}

/** Lines in place of the first page while its picture is missing, like the mock-up. */
@Composable
private fun ThumbnailPlaceholder(width: Dp, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(width * PLACEHOLDER_PADDING_RATIO),
        verticalArrangement = Arrangement.spacedBy(width * PLACEHOLDER_GAP_RATIO),
    ) {
        PlaceholderLines.forEachIndexed { index, fraction ->
            val color = if (index == 0) PlaceholderHeadingColor else PlaceholderLineColor
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(width * PLACEHOLDER_LINE_RATIO)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
