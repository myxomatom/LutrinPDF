// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens

/**
 * Centred message explaining an empty or error state (views E1 « vide » and V8), with an
 * illustration on top and actions below. Every screen uses it for its non-content states
 * (`AGENTS.md` §11).
 *
 * @param title short headline, announced as a heading by screen readers.
 * @param message one or two sentences telling the user what happened and what to do.
 * @param modifier modifier applied to the column.
 * @param illustration decorative picture shown above the title.
 * @param actions buttons, stacked full width below the message.
 */
@Composable
fun StatusMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier.padding(horizontal = LutrinDimens.StatusMessageGutter),
        verticalArrangement = Arrangement.spacedBy(LutrinDimens.StatusMessageSpacing, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        illustration()
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LutrinDimens.ButtonSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(LutrinDimens.StatusMessageActionsTop - LutrinDimens.ButtonSpacing))
            actions()
        }
    }
}

/**
 * Warning sign in an `errorContainer` circle, the illustration of error messages (V8).
 *
 * @param modifier modifier applied to the circle.
 */
@Composable
fun ErrorIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(LutrinDimens.ErrorIconCircle)
            .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(LutrinDimens.ErrorIcon),
        )
    }
}
