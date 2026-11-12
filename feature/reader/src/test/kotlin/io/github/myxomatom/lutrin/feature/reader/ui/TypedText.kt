// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert

/** Asserts what is typed in a text field, whatever its label or supporting text. */
internal fun SemanticsNodeInteraction.assertTypedText(expected: String): SemanticsNodeInteraction = assert(
    SemanticsMatcher("the typed text is \"$expected\"") {
        it.config.getOrNull(SemanticsProperties.EditableText)?.text == expected
    },
)
