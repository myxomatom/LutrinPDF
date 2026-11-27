// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.share.ShareOutcome

/**
 * What the items of the « More options » menu do (ADR 0027). The menu closes before each one is
 * called.
 *
 * @property onDisplay « Affichage »: opens the display options (V6).
 * @property onShare « Partager »: opens the chooser of the system (F15).
 * @property onProperties « Propriétés »: opens the properties sheet (V5, F16).
 */
@Stable
class ReaderMenu(val onDisplay: () -> Unit, val onShare: () -> Unit, val onProperties: () -> Unit)

/**
 * The « More options » button of the top bar and the menu it opens: « Affichage », « Partager » and
 * « Propriétés ». The menu stays open across a rotation of the screen.
 *
 * @param menu what its items do.
 */
@Composable
internal fun ReaderMenuButton(menu: ReaderMenu) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chosen: (() -> Unit) -> Unit = { action ->
        expanded = false
        action()
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.reader_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuItem(R.string.reader_options_title) { chosen(menu.onDisplay) }
            MenuItem(R.string.reader_menu_share) { chosen(menu.onShare) }
            MenuItem(R.string.reader_menu_properties) { chosen(menu.onProperties) }
        }
    }
}

@Composable
private fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

/**
 * What a tap on « Partager » does (F15, ADR 0027): opens the chooser of the system, or says that
 * no app can receive the file, or that the file cannot be shared.
 *
 * @param onShare opens the chooser and tells how it went.
 * @param snackbar where the message appears.
 */
@Composable
internal fun rememberShareClick(onShare: () -> ShareOutcome, snackbar: SnackbarHostState): () -> Unit {
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val currentOnShare by rememberUpdatedState(onShare)
    return remember(snackbar) {
        {
            when (currentOnShare()) {
                ShareOutcome.STARTED -> Unit

                ShareOutcome.NO_APP -> scope.inform(snackbar, resources.getString(R.string.reader_share_no_app))

                ShareOutcome.NOT_SHAREABLE ->
                    scope.inform(snackbar, resources.getString(R.string.reader_share_unavailable))
            }
        }
    }
}
