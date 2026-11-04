// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * @param modifier modifier applied to the screen.
 * @param viewModel state holder, provided by Hilt for the navigation entry.
 */
@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        dynamicColorSupported = isDynamicColorSupported,
        onDynamicColorChange = viewModel::onDynamicColorChange,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Settings screen (E3, `docs/design/png/E3-Parametres.png`). For now only the « Appearance »
 * section with dynamic colours (ADR 0010); the other settings come with milestone M5.
 *
 * @param state what to show.
 * @param dynamicColorSupported whether the device can use dynamic colours (Android 12+);
 *   otherwise the switch is shown disabled, with the reason.
 * @param onDynamicColorChange called with the new value of the switch.
 * @param onBack called when the user leaves the settings.
 * @param modifier modifier applied to the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    dynamicColorSupported: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
            Column(Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
                SectionTitle(stringResource(R.string.settings_section_appearance))
                DynamicColorItem(
                    checked = state.dynamicColor && dynamicColorSupported,
                    enabled = dynamicColorSupported,
                    onCheckedChange = onDynamicColorChange,
                )
            }
        }
    }
}

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

/** A switch row; the whole row toggles it, as one accessible element. */
@Composable
private fun DynamicColorItem(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = if (enabled) R.string.settings_dynamic_color_summary else R.string.settings_dynamic_color_unavailable
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
        supportingContent = { Text(stringResource(summary)) },
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
