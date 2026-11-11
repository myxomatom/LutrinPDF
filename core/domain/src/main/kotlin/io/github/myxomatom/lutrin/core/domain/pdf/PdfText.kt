// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

/** Most results a search returns before it stops (F07, ADR 0020). */
const val MAX_SEARCH_HITS: Int = 1_000

/**
 * Options of a text search (F07).
 *
 * @property matchCase when `false`, "Lutrin" also matches "lutrin".
 * @property matchAccents when `false`, "ete" also matches "été".
 * @property maxHits the search stops once it has found this many results.
 * @throws IllegalArgumentException if [maxHits] is not positive.
 */
data class SearchOptions(
    val matchCase: Boolean = false,
    val matchAccents: Boolean = false,
    val maxHits: Int = MAX_SEARCH_HITS,
) {
    init {
        require(maxHits > 0) { "maxHits must be positive, was $maxHits" }
    }
}

/**
 * One occurrence of the searched text.
 *
 * @property pageIndex zero-based page of the occurrence.
 * @property areas highlighted areas, in page coordinates; several when the match spans lines,
 *   for example a word cut by a hyphen at the end of a line.
 * @property excerpt the occurrence in its context, for a list of results; `null` when the engine
 *   could not tell which characters matched.
 */
data class SearchHit(val pageIndex: Int, val areas: List<PdfRect>, val excerpt: SearchExcerpt? = null)

/**
 * An occurrence of the searched text with the words around it, on one line. Shown as
 * `before + match + after`: [before] ends and [after] starts with the spaces that separate them
 * from [match], if any.
 *
 * @property before text preceding the occurrence, at most [MAX_CONTEXT_LENGTH] characters.
 * @property match the occurrence as written in the document (with its case and accents).
 * @property after text following the occurrence, at most [MAX_CONTEXT_LENGTH] characters.
 */
data class SearchExcerpt(val before: String, val match: String, val after: String) {
    /** Factories and limits of [SearchExcerpt]. */
    companion object {
        /** Most characters kept on each side of the occurrence. */
        const val MAX_CONTEXT_LENGTH: Int = 40

        /**
         * Cuts the excerpt of the occurrence `text[matchStart until matchEnd]`. Runs of
         * whitespace become one space; each side keeps at most [maxContext] characters and, when
         * cut, starts or ends on a whole word if it can.
         *
         * @param text text around the occurrence, typically the page text on one line.
         * @param matchStart index of the first character of the occurrence in [text].
         * @param matchEnd index after the last character of the occurrence in [text].
         * @param maxContext most characters kept on each side.
         * @throws IllegalArgumentException if the range is not inside [text] or [maxContext] is negative.
         */
        fun around(text: String, matchStart: Int, matchEnd: Int, maxContext: Int = MAX_CONTEXT_LENGTH): SearchExcerpt {
            require(matchStart in 0..matchEnd && matchEnd <= text.length) {
                "Invalid match [$matchStart, $matchEnd) in a text of ${text.length} characters"
            }
            require(maxContext >= 0) { "Negative context length $maxContext" }
            val before = text.substring(0, matchStart).collapseWhitespace().trimStart()
            val after = text.substring(matchEnd).collapseWhitespace().trimEnd()
            return SearchExcerpt(
                before = before.keepEnd(maxContext),
                match = text.substring(matchStart, matchEnd).collapseWhitespace(),
                after = after.keepStart(maxContext),
            )
        }

        private val whitespace = Regex("\\s+")

        private fun String.collapseWhitespace() = replace(whitespace, " ")

        /** The last [max] characters, starting on a whole word when a space allows it. */
        private fun String.keepEnd(max: Int): String {
            val kept = takeLast(max)
            val wordStart = kept.indexOf(' ')
            return when {
                length <= max -> this

                // Cut right after a space: the kept part already starts with a whole word.
                this[length - max - 1] == ' ' -> kept

                wordStart in 0 until kept.lastIndex -> kept.substring(wordStart + 1)

                else -> kept
            }
        }

        /** The first [max] characters, ending on a whole word when a space allows it. */
        private fun String.keepStart(max: Int): String {
            val kept = take(max)
            val wordEnd = kept.lastIndexOf(' ')
            return when {
                length <= max -> this

                // Cut right before a space: the kept part already ends with a whole word.
                this[max] == ' ' -> kept

                wordEnd > 0 -> kept.substring(0, wordEnd)

                else -> kept
            }
        }
    }
}

/**
 * What a search found on one page (F07). A search emits one result per page, in page order,
 * including the pages without any occurrence, so that the caller can show its progress.
 *
 * @property pageIndex zero-based page just searched; `pageIndex + 1` pages are done.
 * @property hits occurrences on this page, in reading order; possibly empty.
 * @property isLimitReached `true` on the last result of a search stopped at
 *   [SearchOptions.maxHits]: the following pages were not searched.
 */
data class SearchPageResult(val pageIndex: Int, val hits: List<SearchHit>, val isLimitReached: Boolean = false)

/** How a text selection grows from the touched points (F08). */
enum class SelectionMode {
    /** From character to character, for dragging a selection handle. */
    Characters,

    /** Whole words, for a long press on a word. */
    Words,

    /** Whole lines. */
    Lines,
}

/**
 * Text selected on one page (F08). A selection never spans pages (ADR 0020).
 *
 * @property pageIndex zero-based page of the selection.
 * @property quads areas to highlight, in page coordinates: one per line of the selection.
 * @property text the selected text in reading (logical) order, lines separated by `\n`.
 */
data class TextSelection(val pageIndex: Int, val quads: List<PdfQuad>, val text: String) {
    /** Whether nothing is selected (no text under the points, or a page without text layer). */
    val isEmpty: Boolean get() = text.isEmpty()

    /** Factories of [TextSelection]. */
    companion object {
        /** An empty selection on the page at [pageIndex]. */
        fun empty(pageIndex: Int): TextSelection = TextSelection(pageIndex, emptyList(), "")
    }
}
