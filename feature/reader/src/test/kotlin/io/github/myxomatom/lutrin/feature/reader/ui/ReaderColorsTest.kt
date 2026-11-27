// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.RenderOptions
import io.github.myxomatom.lutrin.core.domain.settings.DocumentColorMode
import io.github.myxomatom.lutrin.core.domain.settings.DocumentColors
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Colours of the document (F13, ADR 0028): the choice in « Affichage » (V6), and the pages drawn with it. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp")
class ReaderColorsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val document = FakePdfDocument()
    private val chosen = mutableListOf<DocumentColorMode>()

    private fun ready(colors: DocumentColors = DocumentColors()) = ReaderUiState.Ready(
        "Contrat de location",
        document.pageSizes,
        PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
        documentColors = colors,
    )

    /** Shows the reader wired like the ViewModel: the colours chosen come back in the state. */
    private fun show(initial: ReaderUiState.Ready, darkTheme: Boolean = false) {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) {
                ReaderScreen(
                    state = state,
                    onBack = {},
                    onDocumentColorModeChange = {
                        chosen += it
                        state = state.copy(documentColors = state.documentColors.copy(chosen = it))
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun openDisplayOptions(more: String = "More options", display: String = "Display") {
        composeRule.onNodeWithContentDescription(more).performClick()
        composeRule.onNodeWithText(display).performClick()
        composeRule.waitForIdle()
    }

    private fun swatch(label: String) = composeRule.onNode(hasText(label) and isSelectable())

    @Test
    fun `the display options offer the colours of the document, normal by default`() {
        show(ready())

        openDisplayOptions()

        composeRule.onNodeWithText("Document colours").performScrollTo().assertIsDisplayed()
        swatch("Normal").performScrollTo().assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        swatch("Night").assertIsNotSelected()
        swatch("Sepia").assertIsNotSelected()
    }

    @Test
    fun `normal stays the default in the dark theme`() {
        show(ready(), darkTheme = true)

        openDisplayOptions()

        swatch("Normal").performScrollTo().assertIsSelected()
        assertThat(document.renders.map { it.options }.toSet()).containsExactly(RenderOptions.ORIGINAL)
    }

    @Test
    fun `choosing sepia reports it, keeps the sheet open and draws the pages again in sepia`() {
        show(ready())
        assertThat(document.renders.map { it.options }.toSet()).containsExactly(RenderOptions.ORIGINAL)
        openDisplayOptions()

        swatch("Sepia").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertThat(chosen).containsExactly(DocumentColorMode.SEPIA)
        swatch("Sepia").assertIsSelected()
        composeRule.onNodeWithText("Display").assertIsDisplayed()
        assertThat(document.renders.last().options).isEqualTo(RenderOptions(DocumentColorMode.SEPIA))
    }

    @Test
    fun `documents following the dark theme are drawn in night colours in the dark theme`() {
        show(ready(DocumentColors(followDarkTheme = true)), darkTheme = true)

        assertThat(document.renders).isNotEmpty()
        assertThat(document.renders.map { it.options }.toSet()).containsExactly(RenderOptions(DocumentColorMode.NIGHT))
        openDisplayOptions()
        swatch("Night").performScrollTo().assertIsSelected()
    }

    @Test
    fun `documents following the dark theme keep their colours in the light theme`() {
        show(ready(DocumentColors(followDarkTheme = true)), darkTheme = false)

        assertThat(document.renders.map { it.options }.toSet()).containsExactly(RenderOptions.ORIGINAL)
    }

    @Test
    fun `colours chosen explicitly win over the dark theme`() {
        show(ready(DocumentColors(chosen = DocumentColorMode.NORMAL, followDarkTheme = true)), darkTheme = true)

        assertThat(document.renders.map { it.options }.toSet()).containsExactly(RenderOptions.ORIGINAL)
    }

    @Test
    fun `a recomposition with the same colours draws nothing again`() {
        show(ready(DocumentColors(chosen = DocumentColorMode.NIGHT)))
        val drawn = document.renders.size

        openDisplayOptions()
        composeRule.onNodeWithText("Display").assertIsDisplayed()

        assertThat(document.renders).hasSize(drawn)
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp")
    fun `the colours of the document are translated in French`() {
        show(ready())

        openDisplayOptions(more = "Plus d’options", display = "Affichage")

        composeRule.onNodeWithText("Couleurs du document").performScrollTo().assertIsDisplayed()
        swatch("Normal").assertIsSelected()
        swatch("Nuit").assertIsNotSelected()
        swatch("Sépia").assertIsNotSelected()
    }
}
