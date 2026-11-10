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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.R as CoreUiR

/** Size of a thumbnail (E1 mock-up: 44 × 58 px at 1×). */
private val ThumbnailWidth = 44.dp
private val ThumbnailHeight = 58.dp

/** Corner radius of a thumbnail (E1 mock-up). */
private val ThumbnailCorner = 4.dp
private val ThumbnailBorder = 1.dp

/** Lock badge of a protected document, overlapping the corner of its thumbnail (E1 mock-up). */
private val LockBadgeSize = 22.dp
private val LockBadgeIcon = 14.dp
private val LockBadgeOverlap = 6.dp

/** Lines drawn in a thumbnail that is not available yet, as fractions of its width. */
private val PlaceholderLines = listOf(0.6f, 1f, 1f, 0.8f, 1f, 0.5f)
private val PlaceholderLineHeight = 3.dp
private val PlaceholderPadding = 7.dp
private val PlaceholderLineGap = 5.dp
private val PlaceholderLineCorner = 2.dp

/**
 * The picture of the first page of a recent document, or lines in its place while it is
 * missing, with the lock of a protected document (E1 mock-up, ADR 0015, 0017). Asks for the
 * picture when it is missing or outdated.
 *
 * @param document the row.
 * @param loadThumbnail reads a thumbnail file.
 * @param onRefreshThumbnail called for a row shown with a missing or outdated thumbnail.
 */
@Composable
internal fun Thumbnail(
    document: RecentDocumentItem,
    loadThumbnail: suspend (String) -> ImageBitmap?,
    onRefreshThumbnail: (String) -> Unit,
) {
    val path = document.thumbnailPath
    val currentLoad by rememberUpdatedState(loadThumbnail)
    val currentOnOutdated by rememberUpdatedState(onRefreshThumbnail)
    val picture by produceState<ImageBitmap?>(null, path) { value = path?.let { currentLoad(it) } }
    LaunchedEffect(document.documentId, document.thumbnailOutdated, document.openedOn) {
        // Never for a protected document, which cannot be opened without its password (ADR 0017).
        if (document.thumbnailOutdated && !document.isProtected) currentOnOutdated(document.documentId)
    }
    val shape = RoundedCornerShape(ThumbnailCorner)
    val frame = Modifier
        .size(ThumbnailWidth, ThumbnailHeight)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .border(ThumbnailBorder, MaterialTheme.colorScheme.outlineVariant, shape)
    // A protected document is never drawn (ADR 0017): an old picture is not shown either.
    val image = picture?.takeUnless { document.isProtected }
    Box {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.library_thumbnail, document.name),
                contentScale = ContentScale.Fit,
                modifier = frame,
            )
        } else {
            ThumbnailPlaceholder(frame)
        }
        if (document.isProtected) {
            LockBadge(Modifier.align(Alignment.BottomEnd).offset(LockBadgeOverlap, LockBadgeOverlap))
        }
    }
}

/** The lock of a protected document; its description is read with the row. */
@Composable
private fun LockBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(LockBadgeSize).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(CoreUiR.drawable.ic_lock),
            contentDescription = stringResource(R.string.library_protected),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(LockBadgeIcon),
        )
    }
}

/** Lines in place of the first page while its picture is missing, like the mock-up. */
@Composable
private fun ThumbnailPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(PlaceholderPadding),
        verticalArrangement = Arrangement.spacedBy(PlaceholderLineGap),
    ) {
        PlaceholderLines.forEachIndexed { index, fraction ->
            val color =
                if (index == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.surfaceVariant
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(PlaceholderLineHeight)
                    .clip(RoundedCornerShape(PlaceholderLineCorner))
                    .background(color),
            )
        }
    }
}
