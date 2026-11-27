// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.myxomatom.lutrin.core.domain.settings.AppLanguage
import io.github.myxomatom.lutrin.core.domain.settings.AppTheme

/** The themes in the order of the mockup. */
private val AppThemes = listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.SYSTEM)

/**
 * « Thème de l'application »: « Clair » / « Sombre » / « Système » segmented buttons (mockup E3),
 * a list of radio buttons with a large font ([SettingChoice]).
 *
 * @param theme the theme chosen now.
 * @param onThemeChange called with the theme the user picks.
 * @param modifier modifier applied to the choice and its title.
 */
@Composable
internal fun AppThemeChoice(theme: AppTheme, onThemeChange: (AppTheme) -> Unit, modifier: Modifier = Modifier) {
    SettingChoice(R.string.settings_app_theme, AppThemes, theme, AppTheme::label, onThemeChange, modifier)
}

/**
 * « Langue de l'application »: « Système » / « Français » / « English » (F19, ADR 0029), as the
 * theme choice. The names of the languages are in their own language, whatever the one in use.
 *
 * @param language the language chosen now.
 * @param onLanguageChange called with the language the user picks; the system then restarts the
 *   screens of the app in it.
 * @param modifier modifier applied to the choice and its title.
 */
@Composable
internal fun AppLanguageChoice(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingChoice(
        R.string.settings_app_language,
        AppLanguage.entries,
        language,
        AppLanguage::label,
        onLanguageChange,
        modifier,
    )
}

/** Label of an app theme in the settings. */
private val AppTheme.label: Int
    get() = when (this) {
        AppTheme.LIGHT -> R.string.settings_theme_light
        AppTheme.DARK -> R.string.settings_theme_dark
        AppTheme.SYSTEM -> R.string.settings_theme_system
    }

/** Label of an app language in the settings. */
private val AppLanguage.label: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.FRENCH -> R.string.settings_language_french
        AppLanguage.ENGLISH -> R.string.settings_language_english
    }
