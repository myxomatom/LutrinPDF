// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens
import io.github.myxomatom.lutrin.feature.reader.R

/** Horizontal margin of the sheet content (mockup V6: 24 dp). */
private val SheetGutter = 24.dp

/** Space below the last option, above the navigation bar. */
private val SheetBottom = 24.dp

/** Gap between the title and the sections (mockup V6). */
private val SectionSpacing = 16.dp

/** Gap between a section title and its choices (mockup V6). */
private val SectionTitleSpacing = 8.dp

/** Gap between the text of a switch row and its switch (mockup V6). */
private val SwitchRowSpacing = 16.dp

/** Space above and below the text of a switch row with a description. */
private val SwitchRowVerticalPadding = 8.dp

/**
 * Display options of the reader (V6), opened by « More options ». For now only the scroll
 * section: the scroll mode (F03) and the page preview of the slider (ADR 0018); the other options
 * of the mockup arrive with milestone M5 (ADR 0011).
 *
 * @param scrollMode current scroll mode.
 * @param onScrollModeChange called with the chosen scroll mode; the sheet stays open.
 * @param pagePreview whether dragging the slider previews the page.
 * @param onPagePreviewChange called with the new page preview choice; the sheet stays open.
 * @param onDismiss called when the user closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderOptionsSheet(
    scrollMode: ScrollMode,
    onScrollModeChange: (ScrollMode) -> Unit,
    pagePreview: Boolean,
    onPagePreviewChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ReaderOptions(scrollMode, onScrollModeChange, pagePreview, onPagePreviewChange)
    }
}

/**
 * Content of the [ReaderOptionsSheet], without the sheet: the title and the scroll section.
 *
 * @param scrollMode current scroll mode.
 * @param onScrollModeChange called with the chosen scroll mode.
 * @param pagePreview whether dragging the slider previews the page.
 * @param onPagePreviewChange called with the new page preview choice.
 * @param modifier modifier applied to the content.
 */
@Composable
internal fun ReaderOptions(
    scrollMode: ScrollMode,
    onScrollModeChange: (ScrollMode) -> Unit,
    pagePreview: Boolean,
    onPagePreviewChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(start = SheetGutter, end = SheetGutter, bottom = SheetBottom),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        Text(
            stringResource(R.string.reader_options_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(SectionTitleSpacing)) {
            Text(
                stringResource(R.string.reader_options_scrolling),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            ScrollModeChoice(scrollMode, onScrollModeChange)
            PagePreviewSwitch(pagePreview, onPagePreviewChange)
        }
    }
}

/**
 * « Page preview » switch with its description; the whole row toggles it, as one accessible
 * element (mockup V6: label on the left, switch on the right, at least 48 dp high).
 */
@Composable
private fun PagePreviewSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LutrinDimens.TouchTargetMin)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = SwitchRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SwitchRowSpacing),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.reader_page_preview), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.reader_page_preview_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
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

/** Label of a scroll mode in the options. */
private val ScrollMode.label: Int
    get() = when (this) {
        ScrollMode.CONTINUOUS -> R.string.reader_scroll_continuous
        ScrollMode.PAGED -> R.string.reader_scroll_paged
    }
