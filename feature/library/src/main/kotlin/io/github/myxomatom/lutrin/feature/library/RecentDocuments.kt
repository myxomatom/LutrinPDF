// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens
import io.github.myxomatom.lutrin.core.ui.transition.rememberDocumentTransition
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Paddings of a row of the mock-up; the « ⋮ » button brings its own 48 dp touch target. */
private val RowVerticalPadding = 8.dp
private val RowEndPadding = 4.dp
private val RowGap = 16.dp

/** Width and thickness of the reading progress bar of a row. */
private val ProgressWidth = 120.dp
private val ProgressHeight = 4.dp

/** Gap between the name, the details and the progress bar, and the extra space above the bar. */
private val TextGap = 3.dp
private val ProgressTop = 2.dp

/** Paddings of the « Récents » header (E1 mock-up: 8 dp above, 4 dp below). */
private val HeaderTopPadding = 8.dp
private val HeaderBottomPadding = 4.dp

/** Space kept under the list so that the floating button never hides the last row. */
private val ListBottomPadding = 96.dp

/**
 * The recent documents (ADR 0018): the most recent one in the « Reprendre la lecture » card,
 * then, when there are others, the « Récents · n autres documents » header and one row each.
 *
 * @param content the documents to show.
 * @param onOpen called with the URI of the document whose card or row is tapped.
 * @param onRemove called with the identifier of the document whose « Retirer des récents » is
 *   chosen, in the card or in a row.
 * @param loadThumbnail reads a thumbnail file for a frame of the given size in pixels.
 * @param onRefreshThumbnail called for a document shown with a missing or outdated thumbnail.
 * @param modifier modifier applied to the list.
 */
@Composable
internal fun RecentDocumentList(
    content: LibraryUiState.Content,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    loadThumbnail: suspend (String, IntSize) -> ImageBitmap?,
    onRefreshThumbnail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = ListBottomPadding)) {
        item(key = RESUME_KEY, contentType = RESUME_KEY) {
            ResumeCard(
                document = content.resume,
                onOpen = onOpen,
                onRemove = { onRemove(content.resume.documentId) },
                loadThumbnail = loadThumbnail,
                onRefreshThumbnail = onRefreshThumbnail,
            )
        }
        if (content.others.isNotEmpty()) {
            item(key = HEADER_KEY, contentType = HEADER_KEY) { RecentHeader(content.others.size) }
        }
        items(content.others, key = { it.documentId }, contentType = { ROW_TYPE }) { document ->
            RecentDocumentRow(document, onOpen, { onRemove(document.documentId) }, loadThumbnail, onRefreshThumbnail)
        }
    }
}

private const val RESUME_KEY = "resume"
private const val HEADER_KEY = "header"
private const val ROW_TYPE = "row"

/** « Récents » and the number of documents under the card (« 5 autres documents »). */
@Composable
private fun RecentHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = LutrinDimens.ScreenGutter,
            end = LutrinDimens.ScreenGutter,
            top = HeaderTopPadding,
            bottom = HeaderBottomPadding,
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
            text = pluralStringResource(R.plurals.library_other_documents, count, count),
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
    loadThumbnail: suspend (String, IntSize) -> ImageBitmap?,
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
        RecentThumbnail(
            document = document,
            placement = ThumbnailPlacement.InList,
            loadThumbnail = loadThumbnail,
            onRefreshThumbnail = onRefreshThumbnail,
            transition = rememberDocumentTransition(document.transitionDocumentId),
        )
        RecentDocumentTexts(document, Modifier.weight(1f))
        DocumentMenu(document.name, onRemove, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The « ⋮ » button of a recent document and its menu, in a row or in the resume card. The button
 * is a separate 48 dp target, named after the document for screen readers (« Plus d'actions
 * pour Contrat de location »).
 *
 * @param name name of the document.
 * @param onRemove called when « Retirer des récents » is chosen.
 * @param tint colour of the icon.
 * @param modifier modifier applied to the button and its menu.
 */
@Composable
internal fun DocumentMenu(name: String, onRemove: () -> Unit, tint: Color, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.library_row_menu, name),
                tint = tint,
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
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = documentDetails(document),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (document.isReadingInProgress) {
            ReadingProgressBar(
                progress = document.progress,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                thickness = ProgressHeight,
                modifier = Modifier.padding(top = ProgressTop).width(ProgressWidth),
            )
        }
    }
}

/** Test tag of the progress bar, shown only for a reading in progress. */
internal const val READING_PROGRESS_TAG = "readingProgress"

/**
 * The reading progress of a document in progress. The page is already said by the text next to
 * it: the bar is decoration only for screen readers ([READING_PROGRESS_TAG] for tests).
 *
 * @param progress from 0 to 1.
 * @param color colour of the part read.
 * @param trackColor colour of the rest.
 * @param thickness height of the bar.
 * @param modifier modifier applied to the bar (width, padding).
 */
@Composable
internal fun ReadingProgressBar(
    progress: Float,
    color: Color,
    trackColor: Color,
    thickness: Dp,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.testTag(READING_PROGRESS_TAG).height(thickness).clearAndSetSemantics {},
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/**
 * « Page 3 sur 8 · aujourd'hui, 20:41 » for a reading in progress, otherwise the number of pages:
 * « 4 pages · hier », « 1 page · 19 oct. » (ADR 0018).
 *
 * @param document the recent document.
 */
@Composable
internal fun documentDetails(document: RecentDocumentItem): String {
    val date = openedOnText(document.openedOn)
    return if (document.isReadingInProgress) {
        stringResource(R.string.library_reading_position, document.lastPage + 1, document.pageCount, date)
    } else {
        pluralStringResource(R.plurals.library_page_count, document.pageCount, document.pageCount, date)
    }
}

/** « aujourd'hui, 20:41 », « hier », « 28 oct. » or « 28 oct. 2025 », in the app's language. */
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
