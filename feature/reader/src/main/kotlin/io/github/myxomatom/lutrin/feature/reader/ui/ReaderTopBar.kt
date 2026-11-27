// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.myxomatom.lutrin.core.ui.theme.titleLargeCompact
import io.github.myxomatom.lutrin.feature.reader.R

/**
 * Top bar of the reader: back, title and, once a document is open, « Rechercher » (V1), the
 * table of contents (V2) and « More options »: the menu of the display options (V6), « Partager »
 * and « Propriétés » (V5).
 *
 * @param menu what the items of the « More options » menu do; `null` hides the button (error
 *   screen).
 * @param onOutline opens the table of contents; `null` hides the button (error screen, and a
 *   document without an outline: a button to an empty panel would be a dead end, ADR 0021).
 * @param onSearch opens the search bar; `null` hides the button (error screen).
 *
 * « Tourner » (F27, ADR 0032, [ReaderMenu.rotation]) sits between « Rechercher » and the table of
 * contents while the title keeps room enough ([rotationFitsInBar]); otherwise it moves to the top of
 * the menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    menu: ReaderMenu? = null,
    onOutline: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
) {
    BoxWithConstraints {
        val actionCount = listOfNotNull(onSearch, onOutline, menu, menu?.rotation).size
        val rotationInBar = rotationFitsInBar(maxWidth, LocalDensity.current.fontScale, actionCount)
        ReaderTopAppBar(title, onBack, menu, onOutline, onSearch, rotationInBar)
    }
}

/** [ReaderTopBar], with « Tourner » in the bar when [rotationInBar], in the menu otherwise. */
@Suppress("LongParameterList") // The parameters of ReaderTopBar, and where « Tourner » goes.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopAppBar(
    title: String,
    onBack: () -> Unit,
    menu: ReaderMenu?,
    onOutline: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    rotationInBar: Boolean,
) {
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLargeCompact,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
            }
        },
        actions = {
            if (onSearch != null) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.reader_search_open))
                }
            }
            menu?.rotation?.takeIf { rotationInBar }?.let { RotateButton(it) }
            if (onOutline != null) {
                IconButton(onClick = onOutline) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.reader_outline_title),
                    )
                }
            }
            if (menu != null) ReaderMenuButton(menu, rotationInMenu = !rotationInBar)
        },
    )
}
