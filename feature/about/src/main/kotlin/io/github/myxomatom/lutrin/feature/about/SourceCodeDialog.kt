// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Inner margin of the box showing the address, and space around it. */
private val AddressPadding = 16.dp

/** Thickness of the outline of the box showing the address. */
private val AddressBorder = 1.dp

/**
 * Confirmation before the source code address is handed to another app, on the model of V7
 * (ADR 0022, ADR 0030): shows the whole address, says that Lutrin does not access the internet and
 * that another app will open it. Nothing is opened without « Ouvrir ».
 *
 * @param url the address, shown in full.
 * @param onOpen « Ouvrir »: the caller hands the address to another app and closes the dialog.
 * @param onDismiss « Annuler », the back gesture and a tap outside.
 * @param modifier modifier applied to the dialog.
 */
@Composable
internal fun SourceCodeDialog(url: String, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.about_source_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(AddressBorder, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(AddressPadding),
                    )
                }
                Text(
                    text = stringResource(R.string.about_source_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = AddressPadding),
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(R.string.about_source_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_source_cancel)) } },
    )
}
