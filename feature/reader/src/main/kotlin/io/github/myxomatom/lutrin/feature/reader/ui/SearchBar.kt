// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.search.SearchState
import io.github.myxomatom.lutrin.feature.reader.search.SearchStatus

/** Height of the row of the field (mockup V1). */
private val FieldRowHeight = 64.dp

/** Padding of the row of the field on both sides (mockup V1). */
private val FieldRowPadding = 4.dp

/** Gap between the option chips (mockup V1). */
private val ChipSpacing = 8.dp

/** Space under the option chips (mockup V1). */
private val ChipRowBottom = 12.dp

/** Padding around the counter (mockup V1). */
private val CounterPadding = 4.dp

/** Size of the check mark of a selected chip (mockup V1). */
private val ChipIconSize = 18.dp

/** Test tag of the counter of the search bar. */
internal const val SEARCH_COUNTER_TAG = "searchCounter"

/**
 * Search bar of the reader (V1), in place of the top bar while the search is open: « Fermer la
 * recherche », the field, the counter « 2 / 5 » with « précédent » and « suivant », then the two
 * options and, while pages are searched, the progress. The field takes the focus when the bar
 * opens; validating it with the keyboard searches at once and hides the keyboard.
 *
 * TalkBack announces, once, the start and the outcome of a search (« 5 résultats », « Aucun
 * résultat »), then the result reached with « suivant » or « précédent »: the counter is a polite
 * live region whose description only changes at those moments, never page by page.
 *
 * @param state the search.
 * @param actions what its controls do.
 */
@Composable
internal fun SearchBar(state: SearchState, actions: ReaderSearch, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)) {
            FieldRow(state, actions)
            OptionChips(state, actions)
            Box(Modifier.fillMaxWidth().height(SearchProgressHeight)) {
                if (state.status == SearchStatus.Searching) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        // The counter announces the search; the progress would repeat it page by page.
                        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

/** Height kept for the progress indicator, so that the bar does not jump when it appears. */
private val SearchProgressHeight = 4.dp

/** Close, field, counter, previous and next. */
@Composable
private fun FieldRow(state: SearchState, actions: ReaderSearch) {
    Row(
        Modifier.fillMaxWidth().height(FieldRowHeight).padding(horizontal = FieldRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_search_close))
        }
        SearchField(state.query, actions, Modifier.weight(1f))
        SearchCounter(state)
        val hasResults = !state.results.isEmpty()
        IconButton(onClick = actions.onPrevious, enabled = hasResults) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.reader_search_previous))
        }
        IconButton(onClick = actions.onNext, enabled = hasResults) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.reader_search_next))
        }
    }
}

/** The field, focused when the bar opens; « Rechercher » on the keyboard searches at once. */
@Composable
private fun SearchField(query: String, actions: ReaderSearch, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focus) { focus.requestFocus() }
    val transparent = Color.Transparent
    TextField(
        value = query,
        onValueChange = actions.onQueryChange,
        modifier = modifier.focusRequester(focus),
        textStyle = MaterialTheme.typography.titleMedium,
        placeholder = { Text(stringResource(R.string.reader_search_field), maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            actions.onSubmit()
        }),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = transparent,
                unfocusedContainerColor = transparent,
                focusedIndicatorColor = transparent,
                unfocusedIndicatorColor = transparent,
            ),
    )
}

/**
 * « 2 / 5 », « 2 / 5… » while pages are searched, « 2 / 1 000+ » at the limit; nothing before a
 * search. Its description is what TalkBack announces ([searchAnnouncement]).
 */
@Composable
private fun SearchCounter(state: SearchState) {
    val announcement = searchAnnouncement(state)
    val text = searchCounterText(state)
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier =
            Modifier
                .padding(horizontal = CounterPadding)
                .testTag(SEARCH_COUNTER_TAG)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = announcement
                },
    )
}

/** What the counter shows. */
@Composable
private fun searchCounterText(state: SearchState): String {
    if (state.status == SearchStatus.Idle) return ""
    val position = state.currentIndex + 1
    val total = state.results.size
    val format = when {
        state.results.isLimitReached -> R.string.reader_search_counter_limit
        state.status == SearchStatus.Searching -> R.string.reader_search_counter_running
        else -> R.string.reader_search_counter
    }
    return stringResource(format, position, total)
}

/**
 * What TalkBack announces about the search, and reads for the counter: « Recherche en cours… »
 * while pages are searched (never the count page by page), then the number of results, then,
 * once the user moved through them, the result shown and its page.
 */
@Composable
internal fun searchAnnouncement(state: SearchState): String {
    val total = state.results.size
    val position = state.currentIndex + 1
    return when {
        state.status == SearchStatus.Idle -> ""

        state.status == SearchStatus.Searching -> stringResource(R.string.reader_search_searching)

        state.status == SearchStatus.Failed -> stringResource(R.string.reader_search_failed)

        total == 0 -> stringResource(R.string.reader_search_no_result)

        state.hasMoved -> state.currentHit?.let {
            stringResource(R.string.reader_search_position_page, position, total, it.pageIndex + 1)
        }.orEmpty()

        state.results.isLimitReached -> pluralStringResource(R.plurals.reader_search_limit, total, total)

        else -> pluralStringResource(R.plurals.reader_search_result_count, total, total)
    }
}

/** « Respecter la casse » and « Respecter les accents », off by default. */
@Composable
private fun OptionChips(state: SearchState, actions: ReaderSearch) {
    // Scrolls rather than wraps when the labels are long (large font, French).
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = LutrinDimens.ScreenGutter, end = LutrinDimens.ScreenGutter, bottom = ChipRowBottom),
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
    ) {
        OptionChip(stringResource(R.string.reader_search_match_case), state.matchCase, actions.onMatchCaseChange)
        OptionChip(
            stringResource(R.string.reader_search_match_accents),
            state.matchAccents,
            actions.onMatchAccentsChange,
        )
    }
}

/** A chip with a check mark while it is selected; TalkBack reads it as a toggle. */
@Composable
private fun OptionChip(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(label, maxLines = 1) },
        leadingIcon =
            if (selected) {
                { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(ChipIconSize)) }
            } else {
                null
            },
    )
}
