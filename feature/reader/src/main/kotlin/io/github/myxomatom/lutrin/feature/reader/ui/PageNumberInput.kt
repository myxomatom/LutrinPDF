// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

/** What the « Go to page » dialog (V3, F05) makes of the text typed by the user. */
internal sealed interface PageNumberInput {
    /**
     * A page of the document.
     *
     * @property page zero-based page, ready for `PageNavigator.goTo`.
     */
    data class Valid(val page: Int) : PageNumberInput

    /** Nothing typed. */
    data object Empty : PageNumberInput

    /** A number, but 0 or beyond the last page, or too long to be a number. */
    data object OutOfRange : PageNumberInput
}

/**
 * Reads [text], a page number as the user counts them (the first page is 1).
 *
 * The dialog only lets digits through, but this is also the validation: blank text is
 * [PageNumberInput.Empty]; anything that is not a number from 1 to [pageCount] is
 * [PageNumberInput.OutOfRange].
 *
 * @param text what is in the field.
 * @param pageCount number of pages of the document, at least 1.
 */
internal fun parsePageNumber(text: CharSequence, pageCount: Int): PageNumberInput {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return PageNumberInput.Empty
    val number = trimmed.toString().toIntOrNull()
    return if (number != null && number in 1..pageCount) {
        PageNumberInput.Valid(number - 1)
    } else {
        PageNumberInput.OutOfRange
    }
}
