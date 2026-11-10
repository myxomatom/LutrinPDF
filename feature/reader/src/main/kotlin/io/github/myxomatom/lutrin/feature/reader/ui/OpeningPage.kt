// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.theme.LutrinMotion
import io.github.myxomatom.lutrin.core.ui.transition.DocumentTransition
import io.github.myxomatom.lutrin.core.ui.transition.sharedDocumentPage
import kotlinx.coroutines.flow.first

/** Test tag of the opening page. */
internal const val OPENING_PAGE_TAG = "openingPage"

/** Shape of the opening page, width over height: that of the library's thumbnails (104 × 138 dp). */
private const val OPENING_PAGE_ASPECT_RATIO = 104f / 138f

/** Height of the reader's top bar (Material 3 small top app bar), above the first page. */
private val ReaderTopBarHeight = 64.dp

/**
 * The blank page into which the linked thumbnail grows, placed where the first page of the
 * continuous view lies at "fit width" (below the top bar, between the margins). It is drawn only
 * once the thumbnail has been found on the other side, so that a reader entered without one
 * (a protected document has none) shows nothing more. It lets every touch through.
 *
 * @param transition the opening page's transition; nothing is shown without one.
 * @param phase from [rememberOpeningPagePhase].
 * @param modifier modifier applied to the area of the page.
 */
@Composable
internal fun OpeningPage(transition: DocumentTransition?, phase: OpeningPagePhase, modifier: Modifier = Modifier) {
    if (transition == null) return
    val matched = rememberMatchFound(transition)
    AnimatedVisibility(
        visible = phase != OpeningPagePhase.Gone,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = fadeOut(tween(LutrinMotion.SHORT_FADE_MILLIS)),
        label = "openingPage",
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(ScaffoldDefaults.contentWindowInsets.only(TopAndSides))
                .padding(top = ReaderTopBarHeight + PAGE_SPACING, start = PAGE_GUTTER, end = PAGE_GUTTER),
        ) {
            Box(
                Modifier
                    .testTag(OPENING_PAGE_TAG)
                    .size(maxWidth, maxWidth / OPENING_PAGE_ASPECT_RATIO)
                    // Shared only while the navigation moves; the reader's page takes over after.
                    .sharedDocumentPage(transition.takeIf { phase == OpeningPagePhase.Shared })
                    .graphicsLayer { alpha = if (matched.value || transition.isMatchFound) 1f else 0f }
                    .shadow(PAGE_ELEVATION)
                    .background(PAPER),
            )
        }
    }
}

/** The insets kept clear by the page: those of the reader's content, without the bottom. */
private val TopAndSides = WindowInsetsSides.Top + WindowInsetsSides.Horizontal

/**
 * Whether the thumbnail of the library has been found on the other side of [transition], once:
 * the page keeps showing after the library has left the screen.
 */
@Composable
private fun rememberMatchFound(transition: DocumentTransition): State<Boolean> {
    val matched = remember(transition) { mutableStateOf(false) }
    LaunchedEffect(transition) {
        snapshotFlow { transition.isMatchFound }.first { it }
        matched.value = true
    }
    return matched
}
