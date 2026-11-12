// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.domain.pdf.SearchExcerpt
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.search.SearchState
import io.github.myxomatom.lutrin.feature.reader.search.SearchStatus

/** Margin of the card on its sides (mockup V1). */
private val CardSideMargin = 16.dp

/** Margin of the card above the bottom of the screen (mockup V1). */
private val CardBottomMargin = 24.dp

/** Padding inside the card (mockup V1: 12 × 16). */
private val CardHorizontalPadding = 16.dp
private val CardVerticalPadding = 12.dp

/** Gap between the header and the excerpt (mockup V1). */
private val CardLineSpacing = 2.dp

/** Shadow of the card (mockup V1). */
private val CardElevation = 3.dp

/** Most lines of an excerpt; enough for its 2 × 40 characters of context in a large font. */
private const val EXCERPT_MAX_LINES = 3

/** Luminance under which the card is dark (the light theme) and its match a light amber. */
private const val DARK_CARD_LUMINANCE = 0.5f

/** Match on the dark card of the light theme (mockup V1: #FFE08A, contrast 11:1 on #2E3135). */
private val MatchOnDarkCard = Color(0xFFFFE08A)

/** Match on the light card of the dark theme: dark amber, contrast 5:1 on #E1E2E8. */
private val MatchOnLightCard = Color(0xFF7C5800)

/**
 * Card at the bottom of the reader while the search is open (V1), in place of the floating
 * bar: the page of the current result, its section when the document has an outline, and its
 * excerpt with the match in bold. Without results, it says so: « Aucun résultat » with a hint
 * about scanned documents, whose pages have no text to search (ADR 0023), or that the search
 * failed. Nothing before the first result of a search that runs.
 *
 * @param state the search.
 * @param sectionTitle title of the section of a page, or `null` without an outline.
 */
@Composable
internal fun SearchResultCard(state: SearchState, sectionTitle: (Int) -> String?, modifier: Modifier = Modifier) {
    val hit = state.currentHit
    val empty = state.results.isEmpty()
    when {
        hit != null -> SearchCard(modifier) {
            val section = sectionTitle(hit.pageIndex)
            CardHeader(
                if (section != null) {
                    stringResource(R.string.reader_search_card_page_section, hit.pageIndex + 1, section)
                } else {
                    stringResource(R.string.reader_search_card_page, hit.pageIndex + 1)
                },
            )
            hit.excerpt?.let { ExcerptText(it) }
        }

        empty && state.status == SearchStatus.Done -> SearchCard(modifier) {
            Text(stringResource(R.string.reader_search_no_result), style = MaterialTheme.typography.bodyMedium)
            CardHeader(stringResource(R.string.reader_search_no_text_hint))
        }

        empty && state.status == SearchStatus.Failed -> SearchCard(modifier) {
            Text(stringResource(R.string.reader_search_failed), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The dark card of the mockup (inverse surface: light in the dark theme), above the navigation bar. */
@Composable
private fun SearchCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(start = CardSideMargin, end = CardSideMargin, bottom = CardBottomMargin),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = CardElevation,
    ) {
        Column(
            Modifier.padding(horizontal = CardHorizontalPadding, vertical = CardVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(CardLineSpacing),
        ) {
            content()
        }
    }
}

/** « Page 5 · Article 4 », small and dimmed. */
@Composable
private fun CardHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = HEADER_ALPHA),
    )
}

/** Opacity of the header of the card (mockup V1: #C3C6CF on #2E3135, contrast 8:1). */
private const val HEADER_ALPHA = 0.8f

/** « … le preneur peut demander la **résiliation** avec un préavis de … ». */
@Composable
private fun ExcerptText(excerpt: SearchExcerpt) {
    val ellipsis = stringResource(R.string.reader_search_ellipsis)
    val matchColor =
        if (MaterialTheme.colorScheme.inverseSurface.luminance() <
            DARK_CARD_LUMINANCE
        ) {
            MatchOnDarkCard
        } else {
            MatchOnLightCard
        }
    Text(
        text = excerptText(excerpt, ellipsis, SpanStyle(fontWeight = FontWeight.Bold, color = matchColor)),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = EXCERPT_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The excerpt with [ellipsis] where its line goes on, and the match in [matchStyle].
 *
 * @param excerpt the occurrence and the words around it.
 * @param ellipsis put before a context that does not start the excerpt, and after one that does
 *   not end it: a context cut out of its line.
 */
internal fun excerptText(excerpt: SearchExcerpt, ellipsis: String, matchStyle: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        if (excerpt.before.isNotBlank()) append("$ellipsis ${excerpt.before}")
        withStyle(matchStyle) { append(excerpt.match) }
        if (excerpt.after.isNotBlank()) append("${excerpt.after} $ellipsis")
    }
