// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.feature.reader.R

/** Inner margin of the dialog (Material 3 dialogs). */
private val DialogPadding = 24.dp

/** Space between the icon, the title and the content, and above the buttons. */
private val SectionSpacing = 16.dp

/** Inner margin of the box showing the link. */
private val LinkPadding = 16.dp

/** Tallest box for the link: a very long link scrolls inside it instead of pushing the buttons away. */
private val LinkMaxHeight = 160.dp

/** Thickness of the outline of the box showing the link. */
private val LinkBorder = 1.dp

/**
 * Confirmation of an external link (V7, F09, ADR 0022): shows the whole link, says that the app
 * does not reach the internet and that another app will open it, and offers « Copier le lien »,
 * « Annuler » and « Ouvrir ». Nothing is opened without « Ouvrir ».
 *
 * @param uri the link, already checked (`http`, `https` or `mailto`), shown in full.
 * @param onOpen « Ouvrir »: the caller hands the link to another app and closes the dialog.
 * @param onCopy « Copier le lien »: the caller copies the link and closes the dialog.
 * @param onDismiss « Annuler », the back gesture and a tap outside.
 * @param modifier modifier applied to the dialog surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExternalLinkDialog(
    uri: String,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier,
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(Modifier.padding(DialogPadding)) {
                DialogHeader()
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .padding(top = SectionSpacing)
                        .verticalScroll(rememberScrollState()),
                ) {
                    LinkBox(uri)
                    Text(
                        text = stringResource(R.string.reader_link_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlertDialogDefaults.textContentColor,
                        modifier = Modifier.padding(top = SectionSpacing),
                    )
                }
                LinkButtons(onOpen, onCopy, onDismiss)
            }
        }
    }
}

/** The icon of the mock-up, then the question, a heading for TalkBack. */
@Composable
private fun ColumnScope.DialogHeader() {
    Icon(
        painterResource(R.drawable.ic_open_in_new),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Text(
        text = stringResource(R.string.reader_link_title),
        style = MaterialTheme.typography.headlineSmall,
        color = AlertDialogDefaults.titleContentColor,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = SectionSpacing).semantics { heading() },
    )
}

/** The whole link, in an outlined box; a very long one scrolls inside it. */
@Composable
private fun LinkBox(uri: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(LinkBorder, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = uri,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .heightIn(max = LinkMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(LinkPadding),
        )
    }
}

/**
 * « Copier le lien » on the start side, « Annuler » and « Ouvrir » on the end side (mockup V7);
 * with large fonts, the buttons that no longer fit go to the next line.
 */
@Composable
private fun LinkButtons(onOpen: () -> Unit, onCopy: () -> Unit, onDismiss: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = SectionSpacing),
        horizontalArrangement = Arrangement.End,
        verticalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onCopy) { Text(stringResource(R.string.reader_link_copy)) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_link_cancel)) }
        TextButton(onClick = onOpen) { Text(stringResource(R.string.reader_link_open)) }
    }
}
