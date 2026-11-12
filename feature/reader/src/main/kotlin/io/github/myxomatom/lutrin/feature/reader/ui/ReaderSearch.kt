// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import io.github.myxomatom.lutrin.feature.reader.search.SearchController
import io.github.myxomatom.lutrin.feature.reader.search.SearchEffect
import io.github.myxomatom.lutrin.feature.reader.search.SearchState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * What the reader screen needs for the search in the document (F07, V1, ADR 0023), wired by
 * [ReaderRoute] to the search controller of the ViewModel. The defaults make a reader whose
 * search never finds anything.
 *
 * @property state the search: bar, results, current occurrence.
 * @property effects occurrences to bring into view, collected once by the screen.
 * @property onOpen « Rechercher » in the top bar.
 * @property onClose the arrow of the search bar, or back.
 * @property onQueryChange called with the whole text of the field at each change.
 * @property onSubmit « Rechercher » on the keyboard.
 * @property onMatchCaseChange « Respecter la casse » toggled.
 * @property onMatchAccentsChange « Respecter les accents » toggled.
 * @property onNext « Résultat suivant ».
 * @property onPrevious « Résultat précédent ».
 */
@Stable
class ReaderSearch(
    val state: SearchState = SearchState(),
    val effects: Flow<SearchEffect> = emptyFlow(),
    val onOpen: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onMatchCaseChange: (Boolean) -> Unit = {},
    val onMatchAccentsChange: (Boolean) -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
)

/**
 * The search of [controller] for the screen, in its [state] collected by the caller.
 *
 * @param controller the search controller of the ViewModel.
 * @param state its current state.
 */
internal fun readerSearch(controller: SearchController, state: SearchState): ReaderSearch = ReaderSearch(
    state = state,
    effects = controller.effects,
    onOpen = controller::onOpen,
    onClose = controller::onClose,
    onQueryChange = controller::onQueryChange,
    onSubmit = controller::onSubmit,
    onMatchCaseChange = controller::onMatchCaseChange,
    onMatchAccentsChange = controller::onMatchAccentsChange,
    onNext = controller::onNext,
    onPrevious = controller::onPrevious,
)

/**
 * Carries out the effects of the search ([SearchEffectsHandler]) and, while it is open, lets back
 * close it. Composed after the navigation of the app, so that its back handler comes first.
 */
@Composable
internal fun SearchHandling(search: ReaderSearch, navigator: PageNavigator) {
    SearchEffectsHandler(search.effects, navigator)
    BackHandler(enabled = search.state.isOpen, onBack = search.onClose)
}

/**
 * Brings each occurrence the search asks for ([SearchEffect.Reveal]) into the middle of the
 * reading area, in the scroll mode of [navigator].
 */
@Composable
internal fun SearchEffectsHandler(effects: Flow<SearchEffect>, navigator: PageNavigator) {
    LaunchedEffect(effects, navigator) {
        effects.collect { effect ->
            when (effect) {
                is SearchEffect.Reveal -> navigator.reveal(effect.hit.pageIndex, effect.hit.bounds)
            }
        }
    }
}

/** The highlights of [search] on the pages, rebuilt only when its results or its current occurrence change. */
@Composable
internal fun rememberSearchHighlights(search: SearchState): SearchHighlights =
    remember(search.results, search.current) {
        if (search.results.isEmpty()) SearchHighlights.None else SearchHighlights(search.results, search.current)
    }
