// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import io.github.myxomatom.lutrin.feature.reader.OutlineEntry
import io.github.myxomatom.lutrin.feature.reader.OutlineTarget
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import kotlinx.coroutines.launch

/**
 * The table of contents drawer over [content], which receives the function opening it, or `null`
 * for a document without outline (no button, ADR 0021). [onOpen] runs first, so that the current
 * section is expanded when the drawer shows.
 */
@Suppress("LongParameterList") // Plain wiring of the state hoisted in ReaderContent.
@Composable
internal fun OutlineHost(
    state: ReaderUiState.Ready,
    navigator: PageNavigator,
    onOpen: () -> Unit,
    onToggle: (Int) -> Unit,
    onExternalEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (openOutline: (() -> Unit)?) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    OutlineDrawer(
        drawerState = drawerState,
        outline = state.outline,
        documentTitle = state.title,
        pageCount = state.pageSizes.size,
        onToggle = onToggle,
        onEntryClick = rememberOutlineEntryHandler(drawerState, navigator, onExternalEntry),
        onClose = { scope.launch { drawerState.close() } },
        modifier = modifier,
    ) {
        content(
            if (state.outline.isEmpty) {
                null
            } else {
                {
                    onOpen()
                    scope.launch { drawerState.open() }
                }
            },
        )
    }
}

/**
 * What choosing an entry of the table of contents does: an entry of the document closes the
 * drawer and shows its page, at its position when it has one (F06); an address outside the
 * document goes to [onExternal] and the drawer stays open, for the confirmation of external
 * links to take over (V7); an entry that leads nowhere does nothing. The jump runs in a scope
 * that outlives the drawer.
 */
@Composable
internal fun rememberOutlineEntryHandler(
    drawerState: DrawerState,
    navigator: PageNavigator,
    onExternal: (String) -> Unit,
): (OutlineEntry) -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnExternal by rememberUpdatedState(onExternal)
    return remember(drawerState, navigator, scope) {
        { entry ->
            when (val target = entry.target) {
                is OutlineTarget.Page -> {
                    scope.launch { navigator.goTo(target.pageIndex, target.yOffset) }
                    scope.launch { drawerState.close() }
                }

                is OutlineTarget.External -> currentOnExternal(target.uri)

                null -> Unit
            }
        }
    }
}
