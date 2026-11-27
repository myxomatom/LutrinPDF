// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width of one icon button of the top bar: its 48 dp touch target (`AGENTS.md` §11). */
private val BarActionWidth = 48.dp

/** What the top bar keeps besides its actions: the back button and the margins of the title. */
private val BarFixedWidth = 72.dp

/** Least room for the title at the default font size, grown with the font scale. */
private val MinTitleWidth = 64.dp

/**
 * Whether « Tourner » (F27, ADR 0032) fits in the top bar as an icon: the bar, [barWidth] wide,
 * holds [actionCount] icon buttons (« Tourner » included) and still leaves the title its least
 * room, which grows with [fontScale]. Otherwise « Tourner » moves into the « ⋮ » menu: on a narrow
 * screen, or with a very large font (200 % on a phone 360 dp wide).
 */
internal fun rotationFitsInBar(barWidth: Dp, fontScale: Float, actionCount: Int): Boolean =
    barWidth - BarFixedWidth - BarActionWidth * actionCount >= MinTitleWidth * fontScale

/**
 * « Rotate right » (Material Icons, Apache 2.0): turns the pages a quarter clockwise (F27). Drawn
 * here, like [OpenInNewIcon], because `material-icons-extended` is far too large for one icon.
 */
internal val RotateRightIcon: ImageVector by lazy {
    materialIcon(name = "RotateRight") {
        materialPath {
            moveTo(15.55f, 5.55f)
            lineTo(11f, 1f)
            verticalLineToRelative(3.07f)
            curveTo(7.06f, 4.56f, 4f, 7.92f, 4f, 12f)
            reflectiveCurveToRelative(3.05f, 7.44f, 7f, 7.93f)
            verticalLineToRelative(-2.02f)
            curveToRelative(-2.84f, -0.48f, -5f, -2.94f, -5f, -5.91f)
            reflectiveCurveToRelative(2.16f, -5.43f, 5f, -5.91f)
            lineTo(11f, 10f)
            lineToRelative(4.55f, -4.45f)
            close()
            moveTo(19.93f, 11f)
            curveToRelative(-0.17f, -1.39f, -0.72f, -2.73f, -1.62f, -3.89f)
            lineToRelative(-1.42f, 1.42f)
            curveToRelative(0.54f, 0.75f, 0.88f, 1.6f, 1.02f, 2.47f)
            horizontalLineToRelative(2.02f)
            close()
            moveTo(13f, 17.9f)
            verticalLineToRelative(2.02f)
            curveToRelative(1.39f, -0.17f, 2.74f, -0.71f, 3.9f, -1.61f)
            lineToRelative(-1.44f, -1.44f)
            curveToRelative(-0.75f, 0.54f, -1.59f, 0.89f, -2.46f, 1.03f)
            close()
            moveTo(16.89f, 15.48f)
            lineToRelative(1.42f, 1.41f)
            curveToRelative(0.9f, -1.16f, 1.45f, -2.5f, 1.62f, -3.89f)
            horizontalLineToRelative(-2.02f)
            curveToRelative(-0.14f, 0.87f, -0.48f, 1.72f, -1.02f, 2.48f)
            close()
        }
    }
}
