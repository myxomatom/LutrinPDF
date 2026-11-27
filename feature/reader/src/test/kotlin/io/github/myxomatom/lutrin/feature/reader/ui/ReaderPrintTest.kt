// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.GetDocumentNameUseCase
import io.github.myxomatom.lutrin.core.domain.document.GetDocumentPropertiesUseCase
import io.github.myxomatom.lutrin.core.domain.recents.GetStartPageUseCase
import io.github.myxomatom.lutrin.core.domain.recents.IsRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecordDocumentOpeningUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RemoveRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.SaveLastPageUseCase
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetDocumentColorModeUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetScrollModeUseCase
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakeDocumentInfoResolver
import io.github.myxomatom.lutrin.core.testing.FakeDocumentNameResolver
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import io.github.myxomatom.lutrin.core.testing.FakeRecentDocumentsRepository
import io.github.myxomatom.lutrin.core.testing.FakeSettingsRepository
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.READER_URI_ARG
import io.github.myxomatom.lutrin.feature.reader.ReaderViewModel
import io.github.myxomatom.lutrin.feature.reader.ReadingHistory
import io.github.myxomatom.lutrin.feature.reader.print.PrintJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The print button of the floating bar (F15, ADR 0026), wired to [ReaderViewModel] as the route
 * does, but recording the print jobs instead of opening the print dialog of the system.
 */
@RunWith(AndroidJUnit4::class)
class ReaderPrintTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val document = FakePdfDocument(pageSizes = List(3) { A4 }, title = null)
    private val settings = FakeSettingsRepository()
    private val jobs = mutableListOf<PrintJob?>()

    private val printButton get() = composeRule.onNodeWithContentDescription("Print")

    private fun showReader(canPrintOnDevice: Boolean = true, printLabel: String = "Print") {
        val recents = FakeRecentDocumentsRepository()
        val viewModel =
            ReaderViewModel(
                SavedStateHandle(mapOf(READER_URI_ARG to "content://com.example.provider/document/a.pdf")),
                FakePdfEngine(Result.success(document)),
                Dispatchers.Unconfined,
                Dispatchers.Unconfined,
                ObserveSettingsUseCase(settings),
                SetScrollModeUseCase(settings),
                SetDocumentColorModeUseCase(settings),
                ReadingHistory(
                    GetStartPageUseCase(recents),
                    RecordDocumentOpeningUseCase(recents),
                    SaveLastPageUseCase(recents),
                    IsRecentDocumentUseCase(recents),
                    RemoveRecentDocumentUseCase(recents),
                    GetDocumentNameUseCase(recents, FakeDocumentNameResolver()),
                    CoroutineScope(Dispatchers.Unconfined),
                ),
                GetDocumentPropertiesUseCase(FakeDocumentInfoResolver()),
            )
        composeRule.setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LutrinTheme(dynamicColor = false) {
                ReaderScreen(
                    state = state,
                    onBack = {},
                    onPrint = {
                        jobs += viewModel.printJob()
                        canPrintOnDevice
                    },
                )
            }
        }
        composeRule.waitUntil {
            runCatching { composeRule.onNodeWithContentDescription(printLabel).assertExists() }.isSuccess
        }
    }

    @Test
    fun `the floating bar has a print button, with a 48 dp target, that prints the document`() {
        showReader()

        printButton.assertIsDisplayed().assertHasClickAction().assertTouchWidthIsEqualTo(48.dp)
        printButton.assertTouchHeightIsEqualTo(48.dp)
        printButton.performClick()

        assertThat(jobs).hasSize(1)
        assertThat(jobs.single()?.name).isEqualTo("a")
    }

    @Test
    fun `the fit button is gone from the floating bar`() {
        showReader()

        composeRule.onNodeWithContentDescription("Fit to width").assertDoesNotExist()
    }

    @Test
    fun `the print button is hidden in full screen, like the rest of the bar`() {
        showReader()

        composeRule.onNodeWithContentDescription("Document, page 1 of 3").performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MS)

        printButton.assertDoesNotExist()
        composeRule.onNodeWithText("Page 1 / 3").assertIsDisplayed()
    }

    @Test
    fun `a double tap still zooms in`() {
        showReader()
        composeRule.waitUntil { document.renders.isNotEmpty() }
        val fitted = document.renders.maxOf { it.scale }

        composeRule.onNodeWithContentDescription("Document, page 1 of 3").performTouchInput { doubleClick(center) }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MS)
        composeRule.waitForIdle()

        composeRule.waitUntil { document.renders.any { it.scale > fitted } }
        printButton.assertIsDisplayed()
        assertThat(jobs).isEmpty()
    }

    @Test
    fun `a document whose author forbids printing has the button dimmed, read as disabled, explaining why`() {
        document.canPrint = false
        showReader()

        printButton
            .assertIsDisplayed()
            .assert(hasStateDescription("Disabled, forbidden by the author"))
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("The author of this document forbids printing it").assertIsDisplayed()
        assertThat(jobs).isEmpty()
    }

    @Test
    fun `the setting that ignores the restrictions of the document allows printing, live`() {
        document.canPrint = false
        showReader()

        runBlocking { settings.setIgnoreDocumentRestrictions(true) }
        composeRule.waitForIdle()
        printButton.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)).performClick()

        assertThat(jobs).hasSize(1)
        assertThat(jobs.single()).isNotNull()
    }

    @Test
    fun `says so when the device cannot print`() {
        showReader(canPrintOnDevice = false)

        printButton.performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Printing is not available on this device").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the button and its explanation are translated into French`() {
        document.canPrint = false
        showReader(printLabel = "Imprimer")

        composeRule
            .onNodeWithContentDescription("Imprimer")
            .assert(hasStateDescription("Désactivé, interdit par l’auteur"))
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("L’auteur de ce document interdit son impression").assertIsDisplayed()
    }

    private fun hasStateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private companion object {
        /** Longer than the double-tap timeout: a single tap is only confirmed after it. */
        const val DOUBLE_TAP_WAIT_MS = 500L
    }
}
