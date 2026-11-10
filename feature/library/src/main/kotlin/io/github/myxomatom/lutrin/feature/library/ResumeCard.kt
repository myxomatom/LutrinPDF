// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens

/** Margins of the card (E1 mock-up: 4 dp above, 12 dp below, the screen gutter on the sides). */
private val CardTopMargin = 4.dp
private val CardBottomMargin = 12.dp

/** Inner padding of the card and gap between its picture and its texts. */
private val CardPadding = 16.dp
private val CardGap = 16.dp

/** The page of the card leans a little and casts a shadow, like a sheet on a desk. */
private const val PAGE_TILT_DEGREES = -3f
private val PageElevation = 6.dp
private val PageCorner = 4.dp

/** Letter spacing of the « REPRENDRE LA LECTURE » overline. */
private val OverlineLetterSpacing = 0.8.sp

/** Vertical gaps between the overline, the title, the details and the bar. */
private val TitleTop = 6.dp
private val DetailsTop = 2.dp
private val ProgressTop = 10.dp

/** The bar of the card: thicker than in the rows, on a faint track of the primary colour. */
private val ProgressHeight = 6.dp
private const val PROGRESS_TRACK_ALPHA = 0.22f

/** Least space between the bar (or the details) and the « Continuer » pill. */
private val ContinueTop = 12.dp

/** The « Continuer » pill: a look of button, but not a second target (the whole card is one). */
private val ContinueHeight = 40.dp
private val ContinueStartPadding = 20.dp
private val ContinueEndPadding = 16.dp
private val ContinueIcon = 18.dp
private val ContinueGap = 8.dp

/** The title of the document takes two lines at most. */
private const val TITLE_MAX_LINES = 2

/** From this font scale on, the texts of the card go under its page ([StackedResumeContent]). */
private const val LARGE_FONT_SCALE = 1.5f

/**
 * The « Reprendre la lecture » card of the most recent document (E1 mock-up adopted by
 * ADR 0018): its first page, large and slightly tilted, its name, the page reached or its number
 * of pages, a progress bar for a reading in progress, and « Continuer ».
 *
 * The whole card is one target that opens the document, described as « Reprendre la lecture :
 * Contrat de location, page 3 sur 8 » for screen readers (the description wins over the texts
 * merged into the card); « Continuer » only looks like a button. The « ⋮ » button next to it is a
 * separate target with the options of the document (« Retirer des récents »), as in the rows.
 *
 * @param document the most recent document.
 * @param onOpen called with the URI of the document when the card is tapped.
 * @param onRemove called when « Retirer des récents » is chosen.
 * @param loadThumbnail reads a thumbnail file for a frame of the given size in pixels.
 * @param onRefreshThumbnail called when the thumbnail is missing or outdated.
 * @param modifier modifier applied to the card.
 */
@Composable
internal fun ResumeCard(
    document: RecentDocumentItem,
    onOpen: (String) -> Unit,
    onRemove: () -> Unit,
    loadThumbnail: suspend (String, IntSize) -> ImageBitmap?,
    onRefreshThumbnail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = resumeDescription(document)
    Surface(
        onClick = { onOpen(document.uri) },
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = LutrinDimens.ScreenGutter,
                end = LutrinDimens.ScreenGutter,
                top = CardTopMargin,
                bottom = CardBottomMargin,
            )
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        val thumbnail = @Composable {
            RecentThumbnail(
                document = document,
                placement = ThumbnailPlacement.InCard,
                loadThumbnail = loadThumbnail,
                onRefreshThumbnail = onRefreshThumbnail,
                // Said by the description of the card.
                modifier = Modifier
                    .rotate(PAGE_TILT_DEGREES)
                    // Not clipped: the lock of a protected document overlaps the corner of the page.
                    .shadow(PageElevation, RoundedCornerShape(PageCorner), clip = false)
                    .clearAndSetSemantics {},
            )
        }
        val actions = @Composable { ResumeActions(document.name, onRemove) }
        if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
            StackedResumeContent(document, thumbnail, actions)
        } else {
            SideBySideResumeContent(document, thumbnail, actions)
        }
    }
}

/** The layout of the mock-up: the page on the left, every text on its right. */
@Composable
private fun SideBySideResumeContent(
    document: RecentDocumentItem,
    thumbnail: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.padding(CardPadding).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        thumbnail()
        Column(Modifier.weight(1f).fillMaxHeight()) {
            ResumeHeading(document)
            ResumeProgress(document)
            Spacer(Modifier.weight(1f).heightIn(min = ContinueTop))
            actions()
        }
    }
}

/**
 * With a large font (200 %), the texts next to the page would leave « Continuer » no room: only
 * the overline and the title stay next to the page, the rest takes the whole width below.
 */
@Composable
private fun StackedResumeContent(
    document: RecentDocumentItem,
    thumbnail: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Column(Modifier.padding(CardPadding)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
            thumbnail()
            ResumeHeading(document, Modifier.weight(1f))
        }
        ResumeProgress(document, Modifier.padding(top = CardGap))
        Spacer(Modifier.height(ContinueTop))
        actions()
    }
}

/**
 * « REPRENDRE LA LECTURE » and the title of the document. The texts of the card merge into it,
 * whose description is what screen readers say; they remain for voice control (« Appuyer sur
 * Continuer ») and for tests.
 */
@Composable
private fun ResumeHeading(document: RecentDocumentItem, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    Column(modifier) {
        Text(
            text = stringResource(R.string.library_resume).uppercase(locale),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = OverlineLetterSpacing),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = document.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = TitleTop),
        )
    }
}

/** « Page 3 sur 8 · aujourd'hui, 20:41 » and, for a reading in progress, its bar. */
@Composable
private fun ResumeProgress(document: RecentDocumentItem, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = documentDetails(document),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = DetailsTop),
        )
        if (document.isReadingInProgress) {
            val primary = MaterialTheme.colorScheme.primary
            ReadingProgressBar(
                progress = document.progress,
                color = primary,
                trackColor = primary.copy(alpha = PROGRESS_TRACK_ALPHA),
                thickness = ProgressHeight,
                modifier = Modifier.padding(top = ProgressTop).fillMaxWidth(),
            )
        }
    }
}

/** « Continuer » and the « ⋮ » button of the document, at the bottom of the card. */
@Composable
private fun ResumeActions(name: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shrinks if it must, so that « ⋮ » always keeps its place.
        ContinuePill(Modifier.weight(1f, fill = false))
        DocumentMenu(name, onRemove, MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** « Continuer → »: drawn like a filled button; the card around it is the target. */
@Composable
private fun ContinuePill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = ContinueHeight)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(start = ContinueStartPadding, end = ContinueEndPadding),
        horizontalArrangement = Arrangement.spacedBy(ContinueGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_continue),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(ContinueIcon),
        )
    }
}

/**
 * What screen readers say for the card: « Reprendre la lecture : Contrat de location, page 3
 * sur 8 », or « …, 4 pages » when no reading is in progress, then « document protégé » for a
 * protected document.
 */
@Composable
private fun resumeDescription(document: RecentDocumentItem): String {
    val resume =
        if (document.isReadingInProgress) {
            stringResource(R.string.library_resume_position, document.name, document.lastPage + 1, document.pageCount)
        } else {
            pluralStringResource(R.plurals.library_resume_pages, document.pageCount, document.name, document.pageCount)
        }
    return if (document.isProtected) stringResource(R.string.library_resume_protected, resume) else resume
}
