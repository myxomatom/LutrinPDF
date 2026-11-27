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
import io.github.myxomatom.lutrin.core.domain.settings.AppTheme

/** Horizontal margin of the theme choice, that of the settings content (E3 mockup: 24 dp). */
private val ThemeChoiceGutter = 24.dp

/**
 * « Thème de l'application »: « Clair » / « Sombre » / « Système » segmented buttons (mockup E3).
 * With a large font the three labels no longer fit side by side: the same choice is then a list
 * of radio buttons, one per line.
 *
 * @param theme the theme chosen now.
 * @param onThemeChange called with the theme the user picks.
 * @param modifier modifier applied to the choice and its title.
 */
@Composable
internal fun AppThemeChoice(theme: AppTheme, onThemeChange: (AppTheme) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = ThemeChoiceGutter, vertical = ThemeChoiceVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(ThemeChoiceSpacing),
    ) {
        Text(stringResource(R.string.settings_app_theme), style = MaterialTheme.typography.bodyLarge)
        if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
            AppThemeRadioList(theme, onThemeChange)
        } else {
            AppThemeSegments(theme, onThemeChange)
        }
    }
}

/** The themes in the order of the mockup. */
private val AppThemes = listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.SYSTEM)

@Composable
private fun AppThemeSegments(theme: AppTheme, onThemeChange: (AppTheme) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        AppThemes.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice == theme,
                onClick = { onThemeChange(choice) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = AppThemes.size),
                label = { Text(stringResource(choice.label)) },
            )
        }
    }
}

@Composable
private fun AppThemeRadioList(theme: AppTheme, onThemeChange: (AppTheme) -> Unit) {
    Column(Modifier.selectableGroup()) {
        AppThemes.forEach { choice ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouchTarget)
                    .selectable(selected = choice == theme, role = Role.RadioButton) { onThemeChange(choice) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ThemeChoiceSpacing),
            ) {
                RadioButton(selected = choice == theme, onClick = null)
                Text(stringResource(choice.label), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** From this font scale on, the theme is chosen in a list rather than with segmented buttons. */
private const val LARGE_FONT_SCALE = 1.5f

/** Smallest height of a radio row (`AGENTS.md` §11). */
private val MinTouchTarget = 48.dp

/** Label of an app theme in the settings. */
private val AppTheme.label: Int
    get() = when (this) {
        AppTheme.LIGHT -> R.string.settings_theme_light
        AppTheme.DARK -> R.string.settings_theme_dark
        AppTheme.SYSTEM -> R.string.settings_theme_system
    }

/** Space above and below the theme choice. */
private val ThemeChoiceVerticalPadding = 8.dp

/** Gap between « Thème de l'application » and its buttons (mockup E3). */
private val ThemeChoiceSpacing = 8.dp
