// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertWithMessage

// The accessibility rules of `AGENTS.md` §11 that a JVM (Robolectric) test can check on any Compose
// screen, next to the checks of the Accessibility Test Framework that only run on a device:
// every control that reacts to a touch has a name for TalkBack, and a touch target of at least
// 48 dp on each side.

private val isControl: SemanticsMatcher =
    hasClickAction() or isToggleable() or isSelectable() or hasSetTextAction() or
        SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

/** Smallest touch target of the guidelines (WCAG 2.5.8 asks 24 dp, Material and Android ask 48 dp). */
val MINIMUM_TARGET: Dp = 48.dp

/**
 * Fails if a control of the windows now on screen (the screen, its dialogs, menus and sheets) has
 * no text and no content description, or a touch target smaller than [minimumTarget].
 *
 * Controls are the nodes of the merged semantics tree that can be clicked, switched, selected,
 * typed in or slid, which is what TalkBack announces and what a finger can hit.
 *
 * @param minimumTarget smallest side of the touch target of a control.
 */
fun ComposeTestRule.assertControlsAreAccessible(minimumTarget: Dp = MINIMUM_TARGET) {
    val controls = onAllNodes(isControl).fetchSemanticsNodes()
    assertWithMessage("no control on screen: the check would prove nothing").that(controls).isNotEmpty()
    val problems = controls.flatMap { node -> problemsOf(node, density, minimumTarget) }
    assertWithMessage("accessibility problems:\n" + problems.joinToString("\n")).that(problems).isEmpty()
}

private fun problemsOf(node: SemanticsNode, density: Density, minimumTarget: Dp): List<String> {
    val problems = mutableListOf<String>()
    if (!node.hasName()) problems += "${describe(node)}: no text and no content description"
    val minimumPx = with(density) { minimumTarget.toPx() }
    val bounds = node.touchBoundsInRoot
    if (bounds.width < minimumPx - TOLERANCE_PX || bounds.height < minimumPx - TOLERANCE_PX) {
        problems += "${describe(node)}: touch target ${bounds.width} x ${bounds.height} px, " +
            "less than $minimumTarget ($minimumPx px)"
    }
    return problems
}

private fun SemanticsNode.hasName(): Boolean {
    val texts = config.getOrNull(SemanticsProperties.Text).orEmpty()
    val descriptions = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    val typed = config.getOrNull(SemanticsProperties.EditableText)
    return (texts + descriptions).any { it.toString().isNotBlank() } || !typed.isNullOrBlank() ||
        config.contains(SemanticsProperties.IsEditable) || children.any { it.hasName() }
}

private fun describe(node: SemanticsNode): String {
    val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString()
    val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
    val role = node.config.getOrNull(SemanticsProperties.Role)
    return "node #${node.id} (role=$role, text='$text', description='$description', bounds=${node.boundsInRoot})"
}

/** Rounding of the bounds to whole pixels. */
private const val TOLERANCE_PX = 1f
