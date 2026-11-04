// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.R as UiR
import io.github.myxomatom.lutrin.core.ui.component.StatusMessage
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme

/** Bottom padding of the offline notice (E1 mockup). */
private val OfflineNoticeBottom = 32.dp

/** Gap between the shield and the text of the offline notice. */
private val OfflineNoticeGap = 8.dp

/**
 * Library screen (E1). Until recent documents exist (milestone M3) it always shows the empty
 * state of `docs/design/png/E1-Bibliotheque-Vide.png`; the tools and settings actions of the
 * mockup appear with their screens (milestones M5, M6).
 *
 * @param onOpenFile called when the user asks to pick a PDF with the system file picker.
 * @param modifier modifier applied to the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenFile: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.library_title)) }) },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            StatusMessage(
                title = stringResource(R.string.library_empty_title),
                message = stringResource(R.string.library_empty_message),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                illustration = {
                    Image(painter = painterResource(UiR.drawable.illustration_lectern), contentDescription = null)
                },
                actions = { OpenFileButton(onOpenFile) },
            )
            OfflineNotice()
        }
    }
}

@Composable
private fun OpenFileButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = Modifier.heightIn(min = LutrinDimens.ButtonHeight),
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_folder),
            contentDescription = null,
            modifier = Modifier.size(LutrinDimens.ButtonIcon),
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.library_open_file))
    }
}

@Composable
private fun OfflineNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = LutrinDimens.ScreenGutter, end = LutrinDimens.ScreenGutter, bottom = OfflineNoticeBottom),
        horizontalArrangement = Arrangement.spacedBy(OfflineNoticeGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_shield),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(LutrinDimens.SmallIcon),
        )
        Text(
            text = stringResource(R.string.library_offline_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun LibraryScreenPreview() {
    LutrinTheme(dynamicColor = false) { LibraryScreen(onOpenFile = {}) }
}
