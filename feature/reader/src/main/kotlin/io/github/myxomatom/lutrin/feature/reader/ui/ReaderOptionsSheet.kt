// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.domain.settings.DocumentColorMode
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.feature.reader.R

/** Horizontal margin of the sheet content (mockup V6: 24 dp). */
private val SheetGutter = 24.dp

/** Space below the last option, above the navigation bar. */
private val SheetBottom = 24.dp

/** Gap between the title and the sections (mockup V6). */
private val SectionSpacing = 16.dp

/** Gap between a section title and its choices (mockup V6). */
private val SectionTitleSpacing = 8.dp

/** Gap between two samples of document colours (mockup V6). */
private val SwatchSpacing = 12.dp

/** Height of a sample of document colours (mockup V6: 72 dp), more with a large font. */
private val SwatchMinHeight = 72.dp

/** Corners of a sample (mockup V6). */
private val SwatchCorner = 12.dp

/** Edge of a sample that is not chosen (mockup V6). */
private val SwatchBorder = 1.dp

/** Edge of the chosen sample, thick enough to tell it apart without relying on colour (mockup V6). */
private val SwatchSelectedBorder = 3.dp

/** Inner margin of a sample, around its line and its name. */
private val SwatchPadding = 8.dp

/** Gap between the line of « text » and the name of a sample (mockup V6). */
private val SwatchLineGap = 6.dp

/** Width of the line of « text » of a sample (mockup V6: 28 dp). */
private val SwatchLineWidth = 28.dp

/** Thickness of the line of « text » of a sample (mockup V6: 4 dp). */
private val SwatchLineHeight = 4.dp

/** The line is drawn in the ink of the sample, a little lighter than its name. */
private const val SWATCH_LINE_ALPHA = 0.6f

/**
 * Display options of the reader (V6), opened by « More options »: the scroll mode (F03, ADR 0011)
 * and the colours of the document (F13, ADR 0028); the other options of the mockup arrive with
 * the next lots of milestone M5. The page preview of the slider (ADR 0018) and the theme of the
 * app are in the settings screen (E3), with the other app-wide choices.
 *
 * @param scrollMode current scroll mode.
 * @param onScrollModeChange called with the chosen scroll mode; the sheet stays open.
 * @param colorMode colours the pages are drawn with now.
 * @param onColorModeChange called with the chosen colours; the sheet stays open, so that the
 *   pages can be seen changing above it.
 * @param onDismiss called when the user closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderOptionsSheet(
    scrollMode: ScrollMode,
    onScrollModeChange: (ScrollMode) -> Unit,
    colorMode: DocumentColorMode,
    onColorModeChange: (DocumentColorMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ReaderOptions(scrollMode, onScrollModeChange, colorMode, onColorModeChange)
    }
}

/**
 * Content of the [ReaderOptionsSheet], without the sheet: the title, the scroll section and the
 * colours of the document. It scrolls when the font is large.
 *
 * @param scrollMode current scroll mode.
 * @param onScrollModeChange called with the chosen scroll mode.
 * @param colorMode colours the pages are drawn with now.
 * @param onColorModeChange called with the chosen colours.
 * @param modifier modifier applied to the content.
 */
@Composable
internal fun ReaderOptions(
    scrollMode: ScrollMode,
    onScrollModeChange: (ScrollMode) -> Unit,
    colorMode: DocumentColorMode,
    onColorModeChange: (DocumentColorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = SheetGutter, end = SheetGutter, bottom = SheetBottom),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        Text(
            stringResource(R.string.reader_options_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        OptionsSection(stringResource(R.string.reader_options_scrolling)) {
            ScrollModeChoice(scrollMode, onScrollModeChange)
        }
        OptionsSection(stringResource(R.string.reader_options_colors)) {
            ColorModeChoice(colorMode, onColorModeChange)
        }
    }
}

/** A titled section of the options: its title, read as a heading, above its choices. */
@Composable
private fun OptionsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionTitleSpacing)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/**
 * « Normal » / « Nuit » / « Sépia » (mockup V6): three samples of paper, each drawn in the
 * colours it gives the pages, whatever the theme of the app. The chosen one has a thick border in
 * the primary colour; TalkBack reads them as a group of radio buttons. With a large font they are
 * stacked, one per line.
 */
@Composable
private fun ColorModeChoice(colorMode: DocumentColorMode, onColorModeChange: (DocumentColorMode) -> Unit) {
    if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        // A large font no longer fits three names side by side: one sample per line.
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(SwatchSpacing)) {
            DocumentColorMode.entries.forEach { mode ->
                ColorModeSwatch(mode, mode == colorMode, { onColorModeChange(mode) }, Modifier.fillMaxWidth())
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SwatchSpacing),
    ) {
        DocumentColorMode.entries.forEach { mode ->
            ColorModeSwatch(
                mode = mode,
                selected = mode == colorMode,
                onClick = { onColorModeChange(mode) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/** From this font scale on, the samples of colours are stacked instead of side by side. */
private const val LARGE_FONT_SCALE = 1.5f

/** One sample of paper: a line of « text » in its ink above its name, on its paper. */
@Composable
private fun ColorModeSwatch(
    mode: DocumentColorMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SwatchCorner)
    val ink = Color(mode.ink)
    val border =
        if (selected) {
            BorderStroke(SwatchSelectedBorder, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(SwatchBorder, MaterialTheme.colorScheme.outlineVariant)
        }
    Column(
        modifier
            .heightIn(min = SwatchMinHeight)
            .clip(shape)
            .background(Color(mode.paper))
            .border(border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(SwatchPadding),
        verticalArrangement = Arrangement.spacedBy(SwatchLineGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(SwatchLineWidth, SwatchLineHeight)
                .background(ink.copy(alpha = SWATCH_LINE_ALPHA), RoundedCornerShape(SwatchLineHeight)),
        )
        Text(
            stringResource(mode.label),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = ink,
            textAlign = TextAlign.Center,
        )
    }
}

/** « Continuous » / « Page by page » segmented buttons. */
@Composable
private fun ScrollModeChoice(scrollMode: ScrollMode, onScrollModeChange: (ScrollMode) -> Unit) {
    val modes = ScrollMode.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == scrollMode,
                onClick = { onScrollModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                label = { Text(stringResource(mode.label)) },
            )
        }
    }
}

/** Label of document colours in the options. */
private val DocumentColorMode.label: Int
    get() = when (this) {
        DocumentColorMode.NORMAL -> R.string.reader_colors_normal
        DocumentColorMode.NIGHT -> R.string.reader_colors_night
        DocumentColorMode.SEPIA -> R.string.reader_colors_sepia
    }

/** Label of a scroll mode in the options. */
private val ScrollMode.label: Int
    get() = when (this) {
        ScrollMode.CONTINUOUS -> R.string.reader_scroll_continuous
        ScrollMode.PAGED -> R.string.reader_scroll_paged
    }
