// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier

/**
 * The page of the reader that is the shared element of the thumbnail transition (ADR 0019): on
 * the way back to the library, this page shrinks into the document's thumbnail.
 *
 * @property index zero-based page.
 * @property modifier the shared element modifier, applied to that page only.
 */
@Immutable
internal data class SharedPage(val index: Int, val modifier: Modifier)

/**
 * Adds [SharedPage.modifier] to page [index] when it is the shared page; leaves the other pages
 * unchanged.
 *
 * @param sharedPage the shared page, or `null` for none.
 * @param index zero-based page this modifier is for.
 */
internal fun Modifier.sharedPage(sharedPage: SharedPage?, index: Int): Modifier =
    if (sharedPage != null && sharedPage.index == index) then(sharedPage.modifier) else this

/**
 * Which page is the shared element, or `null` for none: the page shown, as long as it is not
 * zoomed in. A zoomed page is larger than the screen; it would shrink from far outside it, so
 * the reader then only fades.
 *
 * @param currentPage zero-based page shown.
 * @param zoom current zoom, 1 = fit.
 * @param hasTransition whether the reader's page has a transition at all.
 */
internal fun sharedPageIndex(currentPage: Int, zoom: Float, hasTransition: Boolean): Int? =
    currentPage.takeIf { hasTransition && zoom <= 1f }
