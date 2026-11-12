// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.ui.theme.LutrinReaderColors
import io.github.myxomatom.lutrin.feature.reader.search.HitRef
import io.github.myxomatom.lutrin.feature.reader.search.SearchResults

/** Width of the outline of the current occurrence (mockup V1: 2 px at 390 px, so 2 dp). */
private val CurrentOutlineWidth = 2.dp

/**
 * The occurrences of a search to highlight on the pages (F07, V1), in both scroll modes. Built
 * from the results and the current occurrence; looking up a page is a binary search.
 *
 * @property results the occurrences found so far.
 * @property current the occurrence shown, highlighted differently; `null` before the first one.
 */
@Immutable
internal class SearchHighlights(private val results: SearchResults, private val current: HitRef?) {
    /** What to draw on the page at [pageIndex], or `null` when it has no occurrence. */
    fun forPage(pageIndex: Int): PageHighlights? {
        val hits = results.onPage(pageIndex)
        if (hits.isEmpty()) return null
        val currentOnPage = current?.takeIf { it.pageIndex == pageIndex }?.indexOnPage
        return PageHighlights(
            others = hits.filterIndexed { index, _ -> index != currentOnPage }.flatMap { it.areas },
            current = currentOnPage?.let { hits.getOrNull(it) }?.areas.orEmpty(),
        )
    }

    /** No highlight. */
    companion object {
        /** Highlights of a reader without search. */
        val None: SearchHighlights = SearchHighlights(SearchResults.Empty, current = null)
    }
}

/**
 * The areas to highlight on one page, in page coordinates.
 *
 * @property others the occurrences other than the current one.
 * @property current the areas of the current occurrence, empty when it is on another page.
 */
@Immutable
internal data class PageHighlights(val others: List<PdfQuad>, val current: List<PdfQuad>)

/**
 * Draws the [highlights] of a page over its rendering, scaled to the size it is displayed at.
 * The paths are built once per size and set of areas, in the draw phase: composing a page does
 * no work for its highlights (`AGENTS.md` §8). The page is white paper whatever the theme, so the
 * colours are those of the document tokens ([LutrinReaderColors]); the current occurrence also
 * has an outline, so that it is not told apart by its hue only.
 *
 * @param highlights what to draw; nothing when `null`.
 * @param page size of the page, in points.
 */
@Composable
internal fun SearchHighlightLayer(highlights: PageHighlights?, page: PageSize, modifier: Modifier = Modifier) {
    if (highlights == null) return
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val scale = size.width / page.width
            val others = highlights.others.toPath(scale)
            val current = highlights.current.toPath(scale)
            val outline = Stroke(width = CurrentOutlineWidth.toPx())
            onDrawBehind {
                drawPath(others, LutrinReaderColors.SearchHit)
                drawPath(current, LutrinReaderColors.SearchHitCurrent)
                drawPath(current, LutrinReaderColors.SearchHitCurrentOutline, style = outline)
            }
        },
    )
}

/** One closed outline per quad, from page points to pixels. */
private fun List<PdfQuad>.toPath(scale: Float): Path = Path().apply {
    forEach { quad ->
        moveTo(quad.upperLeft.x * scale, quad.upperLeft.y * scale)
        lineTo(quad.upperRight.x * scale, quad.upperRight.y * scale)
        lineTo(quad.lowerRight.x * scale, quad.lowerRight.y * scale)
        lineTo(quad.lowerLeft.x * scale, quad.lowerLeft.y * scale)
        close()
    }
}

/** The highlights of the page at [pageIndex], looked up again only when the search changes. */
@Composable
internal fun rememberPageHighlights(highlights: SearchHighlights, pageIndex: Int): PageHighlights? =
    remember(highlights, pageIndex) { highlights.forPage(pageIndex) }
