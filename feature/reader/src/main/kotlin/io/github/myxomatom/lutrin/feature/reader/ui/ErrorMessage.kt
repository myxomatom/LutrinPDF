// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.ReaderError

/** Size of the round icon of the error screen (mockup V8). */
private val ICON_CIRCLE = 80.dp

/**
 * Explains why the document could not be opened (V8, `SPEC.md` §6), with « Retirer des récents »
 * above the way back when the document is in the recent list (mock-up V8, ADR 0016).
 *
 * @param reason what went wrong.
 * @param onBack leaves the reader.
 * @param modifier modifier applied to the message.
 * @param onRemoveFromRecents removes the document from the recent list; `null` hides the button.
 */
@Composable
internal fun ErrorMessage(
    reason: ReaderError,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveFromRecents: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(ICON_CIRCLE).background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        }
        Text(
            stringResource(R.string.reader_error_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(reason.message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val button = Modifier.fillMaxWidth().heightIn(min = LutrinDimens.ButtonHeight)
        if (onRemoveFromRecents != null) {
            FilledTonalButton(onClick = onRemoveFromRecents, modifier = button) {
                Text(stringResource(R.string.reader_remove_from_recents), textAlign = TextAlign.Center)
            }
        }
        OutlinedButton(onClick = onBack, modifier = button) {
            Text(stringResource(R.string.reader_back), textAlign = TextAlign.Center)
        }
    }
}

private val ReaderError.message: Int
    get() =
        when (this) {
            ReaderError.UNREADABLE -> R.string.reader_error_unreadable
            ReaderError.UNSUPPORTED_FORMAT -> R.string.reader_error_unsupported
            ReaderError.NOT_ACCESSIBLE -> R.string.reader_error_not_accessible
        }
