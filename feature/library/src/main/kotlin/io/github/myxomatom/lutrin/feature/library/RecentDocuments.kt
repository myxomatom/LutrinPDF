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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.R as CoreUiR
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Paddings of a row of the mock-up; the « ⋮ » button brings its own 48 dp touch target. */
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

/** Space kept under the list so that the floating button never hides the last row. */
private val ListBottomPadding = 96.dp

/**
 * The recent documents, most recent first, under a « Récents · n documents » header.
 *
 * @param documents rows to show; never empty.
 * @param onOpen called with the URI of the tapped document.
 * @param onRemove called with the identifier of the row whose « Retirer des récents » is chosen.
 * @param loadThumbnail reads a thumbnail file.
 * @param onRefreshThumbnail called for a row shown with a missing or outdated thumbnail.
 * @param modifier modifier applied to the list.
 */
@Composable
internal fun RecentDocumentList(
    documents: List<RecentDocumentItem>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    loadThumbnail: suspend (String) -> ImageBitmap?,
    onRefreshThumbnail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = ListBottomPadding)) {
        item(key = HEADER_KEY) { RecentHeader(documents.size) }
        items(documents, key = { it.documentId }) { document ->
            RecentDocumentRow(document, onOpen, { onRemove(document.documentId) }, loadThumbnail, onRefreshThumbnail)
        }
    }
}

private const val HEADER_KEY = "header"

@Composable
private fun RecentHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = LutrinDimens.ScreenGutter,
            vertical = HeaderVerticalPadding,
        ),
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
    onRemove: () -> Unit,
    loadThumbnail: suspend (String) -> ImageBitmap?,
    onRefreshThumbnail: (String) -> Unit,
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
        Thumbnail(document, loadThumbnail, onRefreshThumbnail)
        RecentDocumentTexts(document, Modifier.weight(1f))
        RowMenu(document.name, onRemove)
    }
}

/**
 * The « ⋮ » button of a row and its menu. The button is a separate 48 dp target, named after the
 * document for screen readers (« Plus d'actions pour Contrat de location »).
 */
@Composable
private fun RowMenu(name: String, onRemove: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.library_row_menu, name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_remove)) },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun RecentDocumentTexts(document: RecentDocumentItem, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TextGap)) {
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
        ReadingProgress(document.progress)
    }
}

/** The page is already said by the text of the row: the bar is decoration only. */
@Composable
private fun ReadingProgress(progress: Float) {
    LinearProgressIndicator(
        progress = { progress },
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

/** « Aujourd'hui, 20:41 », « Hier », « 28 oct. » or « 28 oct. 2025 », in the app's language. */
@Composable
private fun openedOnText(openedOn: OpenedOn): String {
    val locale = LocalLocale.current.platformLocale
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

private fun formatDate(date: LocalDate, locale: Locale, skeleton: String): String = DateTimeFormatter
    .ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    .format(date)
