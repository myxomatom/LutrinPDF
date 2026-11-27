// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.settings.FitMode
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The display options of the reader (V6, ADR 0029): fit, full screen and orientation lock. */
@RunWith(AndroidJUnit4::class)
class ReaderOptionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val document = FakePdfDocument()
    private var state by mutableStateOf(ready())

    private fun ready(
        scrollMode: ScrollMode = ScrollMode.CONTINUOUS,
        chosenFitMode: FitMode? = null,
        lockOrientation: Boolean = false,
    ) = ReaderUiState.Ready(
        title = "Contrat de location",
        pageSizes = document.pageSizes,
        renderer = PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
        scrollMode = scrollMode,
        chosenFitMode = chosenFitMode,
        lockOrientation = lockOrientation,
    )

    /** Shows the reader wired like its ViewModel: the choices come back in the state. */
    private fun show(fontScale: Float = 1f, fits: MutableList<FitMode> = mutableListOf()) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
            ) {
                LutrinTheme(dynamicColor = false) {
                    ReaderScreen(
                        state = state,
                        onBack = {},
                        onScrollModeChange = { state = state.copy(scrollMode = it) },
                        onFitModeChange = {
                            fits += it
                            state = state.copy(chosenFitMode = it)
                        },
                        onLockOrientationChange = { state = state.copy(lockOrientation = it) },
                    )
                }
            }
        }
    }

    private fun openOptions() {
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Display").performClick()
    }

    @Test
    fun `the fit is the width when scrolling and the whole page page by page until one is chosen`() {
        show()
        openOptions()

        composeRule.onNodeWithText("Fit").assertIsDisplayed()
        composeRule.onNodeWithText("Width").assertIsSelected()
        composeRule.onNodeWithText("Whole page").assertIsNotSelected()

        composeRule.onNodeWithText("Page by page").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Whole page").assertIsSelected()
        composeRule.onNodeWithText("Width").assertIsNotSelected()
    }

    @Test
    fun `a chosen fit wins in both scroll modes`() {
        state = ready(chosenFitMode = FitMode.PAGE)
        show()
        openOptions()

        composeRule.onNodeWithText("Whole page").assertIsSelected()

        composeRule.onNodeWithText("Page by page").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Whole page").assertIsSelected()
    }

    @Test
    fun `choosing the whole page reports it and keeps the sheet open`() {
        val fits = mutableListOf<FitMode>()
        show(fits = fits)
        openOptions()

        composeRule.onNodeWithText("Whole page").performClick()
        composeRule.waitForIdle()

        assertThat(fits).containsExactly(FitMode.PAGE)
        composeRule.onNodeWithText("Whole page").assertIsSelected()
        composeRule.onNodeWithText("Display").assertIsDisplayed()
    }

    @Test
    fun `the full screen switch reflects and drives the bars`() {
        show()
        openOptions()
        val fullScreen = composeRule.onNodeWithText("Full screen")
        fullScreen.assertIsOff()
        composeRule.onNodeWithText("Contrat de location").assertIsDisplayed()

        fullScreen.performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Full screen").assertIsOn()
        composeRule.onNodeWithText("Contrat de location").assertDoesNotExist()

        composeRule.onNodeWithText("Full screen").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Full screen").assertIsOff()
        composeRule.onNodeWithText("Contrat de location").assertIsDisplayed()
    }

    @Test
    fun `the orientation lock switch is off by default and reports its changes`() {
        show()
        openOptions()
        composeRule.onNodeWithText("Lock orientation").assertIsOff()

        composeRule.onNodeWithText("Lock orientation").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Lock orientation").assertIsOn()
        assertThat(state.lockOrientation).isTrue()
    }

    @Test
    fun `the orientation switch shows the stored lock`() {
        state = ready(lockOrientation = true)
        show()
        openOptions()

        composeRule.onNodeWithText("Lock orientation").assertIsOn()
    }

    @Test
    fun `a large font turns the segmented choices into radio buttons without cutting a label`() {
        show(fontScale = LARGE_FONT)
        openOptions()

        for (label in listOf("Continuous", "Page by page", "Width", "Whole page")) {
            composeRule.onNode(hasText(label) and isRadioButton()).assertExists()
        }
        composeRule.onNode(hasText("Continuous") and isRadioButton()).assertIsSelected()
        composeRule.onNode(hasText("Width") and isRadioButton()).assertIsSelected()
    }

    private fun isRadioButton() =
        SemanticsMatcher("is a radio button") { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }

    private companion object {
        const val LARGE_FONT = 2f
    }
}
