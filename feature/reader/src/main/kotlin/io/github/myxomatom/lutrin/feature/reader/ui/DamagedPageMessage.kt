// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDarkColorScheme
import io.github.myxomatom.lutrin.core.ui.theme.LutrinLightColorScheme
import io.github.myxomatom.lutrin.core.ui.theme.titleMediumSerif
import io.github.myxomatom.lutrin.feature.reader.R

/** Width at which the message is laid out, before it is scaled to the page shown. */
private val MessageLayoutWidth = 280.dp

/** Largest share of the width, and of the height, of the page that the message covers. */
private const val PAGE_SHARE = 0.8f

/** Gap between the title and the explanation. */
private val TitleGap = 8.dp

/**
 * Message drawn on a page the engine cannot read, in a document that opens (ADR 0031): it says
 * that the PDF file is at fault, not the app, and that the other pages can be read.
 *
 * The message is laid out at [MessageLayoutWidth], with the font size chosen by the user, then
 * scaled as a whole to cover at most [PAGE_SHARE] of the page: it follows the size of the page on
 * screen, small page, zoom out or zoom in, and never spills over it. Its colours are those of the
 * text of the interface on white paper, or on the dark paper of night mode (F13), whatever the
 * theme, like the page itself. TalkBack reads the title and the explanation together.
 *
 * @param nightMode whether the page is drawn with the colours of night mode.
 * @param modifier modifier of the area of the page, which the message is centred in.
 */
@Composable
internal fun DamagedPageMessage(nightMode: Boolean, modifier: Modifier = Modifier) {
    val colors = if (nightMode) LutrinDarkColorScheme else LutrinLightColorScheme
    ScaledToFit(modifier) {
        Column(
            Modifier.semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.reader_page_damaged_title),
                style = MaterialTheme.typography.titleMediumSerif,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(TitleGap))
            Text(
                stringResource(R.string.reader_page_damaged_text),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Fills the space it is given and shows [content], laid out [MessageLayoutWidth] wide, centred
 * and scaled so that it covers at most [PAGE_SHARE] of that space in each direction.
 */
@Composable
private fun ScaledToFit(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val layoutWidth = MessageLayoutWidth.roundToPx()
        val message = measurables.single().measure(Constraints(minWidth = layoutWidth, maxWidth = layoutWidth))
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else message.width
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else message.height
        val scale = minOf(width * PAGE_SHARE / message.width, height * PAGE_SHARE / message.height)
        layout(width, height) {
            // Scaled around its centre, which is the centre of the page.
            message.placeWithLayer((width - message.width) / 2, (height - message.height) / 2) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}
