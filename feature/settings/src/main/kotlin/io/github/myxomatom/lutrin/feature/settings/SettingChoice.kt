// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Horizontal margin of a choice, that of the settings content (E3 mockup: 24 dp). */
private val ChoiceGutter = 24.dp

/** Space above and below a choice. */
private val ChoiceVerticalPadding = 8.dp

/** Gap between a title and its buttons (mockup E3), and between a radio button and its label. */
private val ChoiceSpacing = 8.dp

/** Smallest height of a radio row (`AGENTS.md` §11). */
private val MinTouchTarget = 48.dp

/** From this font scale on, a choice is a list of radio buttons rather than segmented buttons. */
private const val LARGE_FONT_SCALE = 1.5f

/**
 * A setting that is one choice among a few: its [title] over segmented buttons (mockup E3). With a
 * large font the labels no longer fit side by side: the same choice is then a list of radio
 * buttons, one per line, each at least 48 dp high.
 *
 * @param title string resource of the title.
 * @param options the choices, in order.
 * @param selected the choice made now.
 * @param label string resource of the label of a choice.
 * @param onSelect called with the choice the user picks.
 * @param modifier modifier applied to the choice and its title.
 */
@Composable
internal fun <T> SettingChoice(
    title: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = ChoiceGutter, vertical = ChoiceVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(ChoiceSpacing),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
        if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
            ChoiceRadioList(options, selected, label, onSelect)
        } else {
            ChoiceSegments(options, selected, label, onSelect)
        }
    }
}

@Composable
private fun <T> ChoiceSegments(options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(stringResource(label(choice))) },
            )
        }
    }
}

@Composable
private fun <T> ChoiceRadioList(options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { choice ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouchTarget)
                    .selectable(selected = choice == selected, role = Role.RadioButton) { onSelect(choice) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChoiceSpacing),
            ) {
                RadioButton(selected = choice == selected, onClick = null)
                Text(stringResource(label(choice)), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
