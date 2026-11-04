// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful entry point of the reader: connects [ReaderScreen] to its [ReaderViewModel].
 *
 * @param onBack called when the user leaves the reader.
 * @param modifier modifier applied to the screen.
 * @param viewModel state holder, provided by Hilt for the navigation entry.
 */
@Composable
fun ReaderRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReaderScreen(state = state, onBack = onBack, loadPage = viewModel::loadPage, modifier = modifier)
}
