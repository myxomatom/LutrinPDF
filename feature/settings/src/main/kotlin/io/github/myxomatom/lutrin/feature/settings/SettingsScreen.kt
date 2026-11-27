// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.myxomatom.lutrin.core.domain.settings.AppLanguage
import io.github.myxomatom.lutrin.core.domain.settings.AppTheme
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme

/** Horizontal margin of the settings content (E3 mockup: 24 dp). */
private val SettingsGutter = 24.dp

/** Space above a section title. */
private val SectionTitleTop = 16.dp

/** Space below a section title. */
private val SectionTitleBottom = 8.dp

/** Whether the device supports dynamic colours (Material You, Android 12+). */
private val isDynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Stateful entry point of the settings screen: connects [SettingsScreen] to its ViewModel.
 *
 * @param onBack called when the user leaves the settings.
 * @param onOpenAbout called when the user opens « À propos et licences » (E4).
 * @param modifier modifier applied to the screen.
 * @param viewModel state holder, provided by Hilt for the navigation entry.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        dynamicColorSupported = isDynamicColorSupported,
        onDynamicColorChange = viewModel::onDynamicColorChange,
        onBack = onBack,
        modifier = modifier,
        onClearRecents = viewModel::onClearRecents,
        onPagePreviewChange = viewModel::onPagePreviewChange,
        onIgnoreDocumentRestrictionsChange = viewModel::onIgnoreDocumentRestrictionsChange,
        onOpenAbout = onOpenAbout,
        onAppThemeChange = viewModel::onAppThemeChange,
        onFollowDarkThemeChange = viewModel::onFollowDarkThemeChange,
        onKeepScreenOnChange = viewModel::onKeepScreenOnChange,
        onAppLanguageChange = viewModel::onAppLanguageChange,
    )
}

/**
 * Settings screen (E3, `docs/design/png/E3-Parametres.png`). For now the « Appearance » section
 * with the theme of the app, dynamic colours (ADR 0010) and whether documents follow the dark
 * theme (ADR 0028), the « Reading » section with the page preview of the reader's
 * slider (ADR 0018) and the switch that ignores the copy and print restrictions of documents
 * (ADR 0024, ADR 0026), and « Vider les récents » in « Data and privacy », confirmed by a dialog and disabled while
 * the list is empty (ADR 0016); the other settings come with milestone M5.
 *
 * @param state what to show.
 * @param dynamicColorSupported whether the device can use dynamic colours (Android 12+);
 *   otherwise the switch is shown disabled, with the reason.
 * @param onDynamicColorChange called with the new value of the switch.
 * @param onBack called when the user leaves the settings.
 * @param modifier modifier applied to the screen.
 * @param onClearRecents called once the user confirmed « Vider les récents ».
 * @param onPagePreviewChange called with the new value of the page preview switch.
 * @param onIgnoreDocumentRestrictionsChange called with the new value of « Ignorer les restrictions
 *   du document ».
 * @param onOpenAbout called when the user opens « À propos et licences » (E4), the last row.
 * @param onAppThemeChange called with the theme chosen for the app (ADR 0028).
 * @param onFollowDarkThemeChange called with the new value of « Suivre le thème sombre ».
 * @param onKeepScreenOnChange called with the new value of « Garder l'écran allumé » (ADR 0029).
 * @param onAppLanguageChange called with the language chosen for the app (F19, ADR 0029); the row
 *   exists only when [SettingsUiState.appLanguage] is known.
 */
@Suppress("LongMethod") // Plain wiring of the state and of one callback per setting.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    dynamicColorSupported: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onClearRecents: () -> Unit = {},
    onPagePreviewChange: (Boolean) -> Unit = {},
    onIgnoreDocumentRestrictionsChange: (Boolean) -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onAppThemeChange: (AppTheme) -> Unit = {},
    onFollowDarkThemeChange: (Boolean) -> Unit = {},
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onAppLanguageChange: (AppLanguage) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            SettingsContent(
                state = state,
                dynamicColorSupported = dynamicColorSupported,
                appearance =
                    AppearanceActions(
                        onAppThemeChange,
                        onAppLanguageChange,
                        onDynamicColorChange,
                        onFollowDarkThemeChange,
                    ),
                onKeepScreenOnChange = onKeepScreenOnChange,
                onPagePreviewChange = onPagePreviewChange,
                onIgnoreDocumentRestrictionsChange = onIgnoreDocumentRestrictionsChange,
                onClearRecents = onClearRecents,
                onOpenAbout = onOpenAbout,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}

@Suppress("LongParameterList") // One callback per setting of the screen.
@Composable
private fun SettingsContent(
    state: SettingsUiState,
    dynamicColorSupported: Boolean,
    appearance: AppearanceActions,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onPagePreviewChange: (Boolean) -> Unit,
    onIgnoreDocumentRestrictionsChange: (Boolean) -> Unit,
    onClearRecents: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        AppearanceSection(state, dynamicColorSupported, appearance)
        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_section_reading))
        SwitchItem(
            title = stringResource(R.string.settings_keep_screen_on),
            summary = stringResource(R.string.settings_keep_screen_on_summary),
            checked = state.keepScreenOn,
            onCheckedChange = onKeepScreenOnChange,
        )
        SwitchItem(
            title = stringResource(R.string.settings_page_preview),
            summary = stringResource(R.string.settings_page_preview_summary),
            checked = state.pagePreview,
            onCheckedChange = onPagePreviewChange,
        )
        SwitchItem(
            title = stringResource(R.string.settings_ignore_document_restrictions),
            summary = stringResource(R.string.settings_ignore_document_restrictions_summary),
            checked = state.ignoreDocumentRestrictions,
            onCheckedChange = onIgnoreDocumentRestrictionsChange,
        )
        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_section_data))
        ClearRecentsItem(state.recentCount, onClearRecents)
        HorizontalDivider()
        AboutItem(onOpenAbout)
    }
}

/** What the « Appearance » section changes: the theme, the language, dynamic colours, the dark theme follow. */
private class AppearanceActions(
    val onAppThemeChange: (AppTheme) -> Unit,
    val onAppLanguageChange: (AppLanguage) -> Unit,
    val onDynamicColorChange: (Boolean) -> Unit,
    val onFollowDarkThemeChange: (Boolean) -> Unit,
)

/**
 * « Apparence »: the theme of the app (mockup E3), its language (ADR 0029), dynamic colours (ADR 0010), and whether the
 * documents follow the dark theme (ADR 0028). The colours of the documents themselves are chosen
 * in the reader (V6), not here, unlike the mockup.
 */
@Composable
private fun AppearanceSection(state: SettingsUiState, dynamicColorSupported: Boolean, actions: AppearanceActions) {
    Column {
        SectionTitle(stringResource(R.string.settings_section_appearance))
        AppThemeChoice(state.appTheme, actions.onAppThemeChange)
        // Only where the system lets the app choose its own language (Android 13 and later, ADR 0029).
        state.appLanguage?.let { AppLanguageChoice(it, actions.onAppLanguageChange) }
        SwitchItem(
            title = stringResource(R.string.settings_dynamic_color),
            summary =
                stringResource(
                    if (dynamicColorSupported) {
                        R.string.settings_dynamic_color_summary
                    } else {
                        R.string.settings_dynamic_color_unavailable
                    },
                ),
            checked = state.dynamicColor && dynamicColorSupported,
            enabled = dynamicColorSupported,
            onCheckedChange = actions.onDynamicColorChange,
        )
        SwitchItem(
            title = stringResource(R.string.settings_follow_dark_theme),
            summary = stringResource(R.string.settings_follow_dark_theme_summary),
            checked = state.followDarkTheme,
            onCheckedChange = actions.onFollowDarkThemeChange,
        )
    }
}

/** « À propos et licences »: opens E4, which has its own module. */
@Composable
private fun AboutItem(onOpenAbout: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_about)) },
        leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
        modifier =
            modifier
                .clickable(role = Role.Button, onClick = onOpenAbout)
                .padding(horizontal = SettingsGutter - LIST_ITEM_INSET),
    )
}

/**
 * « Vider les récents »: asks for confirmation, since the removal cannot be undone (permissions
 * released). Disabled, with the reason, while the list is empty.
 */
@Composable
private fun ClearRecentsItem(recentCount: Int, onClearRecents: () -> Unit, modifier: Modifier = Modifier) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val enabled = recentCount > 0
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_clear_recents)) },
        supportingContent =
            if (enabled) null else ({ Text(stringResource(R.string.settings_clear_recents_empty)) }),
        leadingContent = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        modifier =
            modifier
                .clickable(enabled = enabled, role = Role.Button) { confirming = true }
                .padding(horizontal = SettingsGutter - LIST_ITEM_INSET)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
    )
    if (confirming && enabled) {
        ClearRecentsDialog(
            recentCount = recentCount,
            onConfirm = {
                confirming = false
                onClearRecents()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun ClearRecentsDialog(recentCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_clear_recents_title)) },
        text = { Text(pluralStringResource(R.plurals.settings_clear_recents_message, recentCount, recentCount)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_clear_recents_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_clear_recents_cancel)) }
        },
    )
}

/** Opacity of a disabled row (Material 3: 38 %). */
private const val DISABLED_ALPHA = 0.38f

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .padding(
                    start = SettingsGutter,
                    end = SettingsGutter,
                    top = SectionTitleTop,
                    bottom = SectionTitleBottom,
                )
                .semantics { heading() },
    )
}

/** A switch row with its description; the whole row toggles it, as one accessible element. */
@Composable
private fun SwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier =
            modifier
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = SettingsGutter - LIST_ITEM_INSET),
    )
}

/** Horizontal padding a Material 3 ListItem already applies on each side. */
private val LIST_ITEM_INSET = 16.dp

@Preview
@Composable
private fun SettingsScreenPreview() {
    LutrinTheme(dynamicColor = false) {
        SettingsScreen(SettingsUiState(loading = false), dynamicColorSupported = true, onDynamicColorChange = {
        }, onBack = {})
    }
}
