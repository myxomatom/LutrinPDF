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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import io.github.myxomatom.lutrin.core.domain.settings.FitMode
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
 * What the display options show (V6).
 *
 * @property scrollMode current scroll mode.
 * @property fitMode what zoom 1 fits now.
 * @property colorMode colours the pages are drawn with now.
 * @property fullscreen whether the bars are hidden (F14).
 * @property lockOrientation whether the orientation is locked (F17).
 */
internal data class ReaderDisplay(
    val scrollMode: ScrollMode,
    val fitMode: FitMode,
    val colorMode: DocumentColorMode,
    val fullscreen: Boolean,
    val lockOrientation: Boolean,
)

/**
 * What the choices of the display options do (V6). Each one is applied at once and the sheet stays
 * open, so that the pages can be seen changing above it.
 *
 * @property onScrollModeChange called with the chosen scroll mode.
 * @property onFitModeChange called with the chosen fit.
 * @property onColorModeChange called with the chosen colours.
 * @property onFullscreenChange called with the new state of the full screen switch.
 * @property onLockOrientationChange called with the new state of the orientation switch.
 */
internal class ReaderDisplayActions(
    val onScrollModeChange: (ScrollMode) -> Unit = {},
    val onFitModeChange: (FitMode) -> Unit = {},
    val onColorModeChange: (DocumentColorMode) -> Unit = {},
    val onFullscreenChange: (Boolean) -> Unit = {},
    val onLockOrientationChange: (Boolean) -> Unit = {},
)

/**
 * Display options of the reader (V6), opened by « More options »: the scroll mode (F03, ADR 0011),
 * the fit of the pages (F04, ADR 0029), the colours of the document (F13, ADR 0028), full screen
 * (F14) and the orientation lock (F17, ADR 0029). The page preview of the slider (ADR 0018), the
 * theme of the app and keeping the screen on are in the settings screen (E3), with the other
 * app-wide choices.
 *
 * @param display what the options show.
 * @param actions what the choices do; the sheet stays open after each one.
 * @param onDismiss called when the user closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderOptionsSheet(display: ReaderDisplay, actions: ReaderDisplayActions, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ReaderOptions(display, actions)
    }
}

/**
 * Content of the [ReaderOptionsSheet], without the sheet: the title, the scroll, fit and colour
 * sections and the two switches. It scrolls when the font is large.
 *
 * @param display what the options show.
 * @param actions what the choices do.
 * @param modifier modifier applied to the content.
 */
@Composable
internal fun ReaderOptions(display: ReaderDisplay, actions: ReaderDisplayActions, modifier: Modifier = Modifier) {
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
            ExclusiveChoice(ScrollMode.entries, display.scrollMode, ScrollMode::label, actions.onScrollModeChange)
        }
        OptionsSection(stringResource(R.string.reader_options_fit)) {
            ExclusiveChoice(FitMode.entries, display.fitMode, FitMode::label, actions.onFitModeChange)
        }
        OptionsSection(stringResource(R.string.reader_options_colors)) {
            ColorModeChoice(display.colorMode, actions.onColorModeChange)
        }
        SwitchRow(stringResource(R.string.reader_options_fullscreen), display.fullscreen, actions.onFullscreenChange)
        SwitchRow(
            stringResource(R.string.reader_options_lock_orientation),
            display.lockOrientation,
            actions.onLockOrientationChange,
        )
    }
}

/** Smallest height of a row of the options (`AGENTS.md` §11: 48 dp targets). */
private val RowMinHeight = 48.dp

/** Gap between a radio button or a title and what follows it on a row. */
private val RowGap = 16.dp

/** A title and a switch on one line; the whole row toggles it, as one element for TalkBack. */
@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
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

/**
 * A choice of one among a few labelled options: segmented buttons (mockup V6), or, with a large
 * font that no longer fits the labels side by side, a list of radio buttons, one per line, as the
 * theme choice of the settings does.
 *
 * @param options the choices, in order.
 * @param selected the current choice.
 * @param label string resource of the label of a choice.
 * @param onSelect called with the choice the user picks.
 */
@Composable
private fun <T> ExclusiveChoice(options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = RowMinHeight)
                        .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RowGap),
                ) {
                    RadioButton(selected = option == selected, onClick = null)
                    Text(stringResource(label(option)), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        return
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(stringResource(label(option))) },
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

/** Label of a fit in the options. */
private val FitMode.label: Int
    get() = when (this) {
        FitMode.WIDTH -> R.string.reader_fit_width
        FitMode.PAGE -> R.string.reader_fit_page
    }

/** Label of a scroll mode in the options. */
private val ScrollMode.label: Int
    get() = when (this) {
        ScrollMode.CONTINUOUS -> R.string.reader_scroll_continuous
        ScrollMode.PAGED -> R.string.reader_scroll_paged
    }
