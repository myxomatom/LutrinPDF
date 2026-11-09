// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Size of a thumbnail (E1 mock-up: 44 × 58 px at 1×). */
private val ThumbnailWidth = 44.dp
private val ThumbnailHeight = 58.dp

/** Paddings of a row of the mock-up. */
private val RowVerticalPadding = 10.dp
private val RowEndPadding = 4.dp
private val RowGap = 16.dp

/** Width and thickness of the reading progress bar. */
private val ProgressWidth = 120.dp
private val ProgressHeight = 4.dp

/** Gap between the name, the details and the progress bar, and the extra space above the bar. */
private val TextGap = 3.dp
private val ProgressTop = 4.dp

/** Vertical padding of the « Récents » header. */
private val HeaderVerticalPadding = 8.dp

/** Corner radius of a thumbnail (E1 mock-up). */
private val ThumbnailCorner = 4.dp
private val ThumbnailBorder = 1.dp

/** Space kept under the list so that the floating button never hides the last row. */
private val ListBottomPadding = 96.dp

/** Lines drawn in a thumbnail that is not available yet, as fractions of its width. */
private val PlaceholderLines = listOf(0.6f, 1f, 1f, 0.8f, 1f, 0.5f)
private val PlaceholderLineHeight = 3.dp
private val PlaceholderPadding = 7.dp
private val PlaceholderLineGap = 5.dp
private val PlaceholderLineCorner = 2.dp

/**
 * The recent documents, most recent first, under a « Récents · n documents » header.
 *
 * @param documents rows to show; never empty.
 * @param onOpen called with the URI of the tapped document.
 * @param loadThumbnail reads a thumbnail file.
 * @param onThumbnailOutdated called for a row shown with a missing or outdated thumbnail.
 */
@Composable
internal fun RecentDocumentList(
    documents: List<RecentDocumentItem>,
    onOpen: (String) -> Unit,
    loadThumbnail: suspend (String) -> ImageBitmap?,
    onThumbnailOutdated: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = ListBottomPadding)) {
        item(key = HEADER_KEY) { RecentHeader(documents.size) }
        items(documents, key = { it.documentId }) { document ->
            RecentDocumentRow(document, onOpen, loadThumbnail, onThumbnailOutdated)
        }
    }
}

private const val HEADER_KEY = "header"

@Composable
private fun RecentHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = LutrinDimens.ScreenGutter, vertical = HeaderVerticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_recents),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = pluralStringResource(R.plurals.library_document_count, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentDocumentRow(
    document: RecentDocumentItem,
    onOpen: (String) -> Unit,
    loadThumbnail: suspend (String) -> ImageBitmap?,
    onThumbnailOutdated: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LutrinDimens.TouchTargetMin)
            .clickable(onClickLabel = stringResource(R.string.library_open_document)) { onOpen(document.uri) }
            .padding(
                start = LutrinDimens.ScreenGutter,
                end = RowEndPadding,
                top = RowVerticalPadding,
                bottom = RowVerticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(document, loadThumbnail, onThumbnailOutdated)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TextGap)) {
            Text(
                text = document.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.library_recent_details,
                    document.lastPage + 1,
                    document.pageCount,
                    openedOnText(document.openedOn),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The page is already said by the text above: the bar is decoration only.
            LinearProgressIndicator(
                progress = { document.progress },
                modifier = Modifier
                    .padding(top = ProgressTop)
                    .width(ProgressWidth)
                    .height(ProgressHeight)
                    .clearAndSetSemantics {},
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun Thumbnail(
    document: RecentDocumentItem,
    loadThumbnail: suspend (String) -> ImageBitmap?,
    onThumbnailOutdated: (String) -> Unit,
) {
    val path = document.thumbnailPath
    val currentLoad by rememberUpdatedState(loadThumbnail)
    val currentOnOutdated by rememberUpdatedState(onThumbnailOutdated)
    val picture by produceState<ImageBitmap?>(null, path) { value = path?.let { currentLoad(it) } }
    LaunchedEffect(document.documentId, document.thumbnailOutdated, document.openedOn) {
        if (document.thumbnailOutdated) currentOnOutdated(document.documentId)
    }
    val shape = RoundedCornerShape(ThumbnailCorner)
    val frame = Modifier
        .size(ThumbnailWidth, ThumbnailHeight)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .border(ThumbnailBorder, MaterialTheme.colorScheme.outlineVariant, shape)
    val image = picture
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

/** « Aujourd'hui, 20:41 », « Hier », « 28 oct. » or « 28 oct. 2025 », in the app's language. */
@Composable
private fun openedOnText(openedOn: OpenedOn): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return when (openedOn) {
        is OpenedOn.Today -> {
            val time = remember(openedOn, locale) {
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(openedOn.time)
            }
            stringResource(R.string.library_opened_today, time)
        }

        OpenedOn.Yesterday -> stringResource(R.string.library_opened_yesterday)

        is OpenedOn.ThisYear -> remember(openedOn, locale) { formatDate(openedOn.date, locale, DAY_MONTH) }

        is OpenedOn.OtherYear -> remember(openedOn, locale) { formatDate(openedOn.date, locale, DAY_MONTH_YEAR) }
    }
}

/** Skeletons of [DateFormat.getBestDateTimePattern]: abbreviated month, with or without year. */
private const val DAY_MONTH = "dMMM"
private const val DAY_MONTH_YEAR = "dMMMy"

private fun formatDate(date: LocalDate, locale: Locale, skeleton: String): String =
    DateTimeFormatter
        .ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        .format(date)
