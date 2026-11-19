// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.GetDocumentNameUseCase
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import io.github.myxomatom.lutrin.core.domain.recents.GetStartPageUseCase
import io.github.myxomatom.lutrin.core.domain.recents.IsRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecordDocumentOpeningUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RemoveRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.SaveLastPageUseCase
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetScrollModeUseCase
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakeDocumentNameResolver
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import io.github.myxomatom.lutrin.core.testing.FakeRecentDocumentsRepository
import io.github.myxomatom.lutrin.core.testing.FakeSettingsRepository
import io.github.myxomatom.lutrin.feature.reader.selection.SelectionEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Selection of [ReaderViewModel] (F08, ADR 0024): the copy permission follows the document and the setting, live. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderViewModelSelectionTest {
    private val dispatcher = StandardTestDispatcher()
    private val area = PdfQuad.of(PdfRect(10f, 10f, 100f, 30f))
    private val document = FakePdfDocument(pageSizes = List(5) { A4 })
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.openViewModel(): ReaderViewModel {
        val recents = FakeRecentDocumentsRepository()
        return ReaderViewModel(
            SavedStateHandle(mapOf(READER_URI_ARG to "content://com.example.provider/document/a.pdf")),
            FakePdfEngine(Result.success(document)),
            dispatcher,
            ObserveSettingsUseCase(settings),
            SetScrollModeUseCase(settings),
            ReadingHistory(
                GetStartPageUseCase(recents),
                RecordDocumentOpeningUseCase(recents),
                SaveLastPageUseCase(recents),
                IsRecentDocumentUseCase(recents),
                RemoveRecentDocumentUseCase(recents),
                GetDocumentNameUseCase(recents, FakeDocumentNameResolver()),
                this,
            ),
        ).also { advanceUntilIdle() }
    }

    private fun ReaderViewModel.canCopy() = (uiState.value as ReaderUiState.Ready).canCopyText

    @Test
    fun `a document that allows the copy can be copied`() = runTest(dispatcher) {
        assertThat(openViewModel().canCopy()).isTrue()
    }

    @Test
    fun `a document that forbids the copy cannot, until the setting ignores it, live`() = runTest(dispatcher) {
        document.canCopyText = false
        val viewModel = openViewModel()
        assertThat(viewModel.canCopy()).isFalse()

        settings.setIgnoreCopyRestriction(true)
        advanceUntilIdle()
        assertThat(viewModel.canCopy()).isTrue()

        settings.setIgnoreCopyRestriction(false)
        advanceUntilIdle()
        assertThat(viewModel.canCopy()).isFalse()
    }

    @Test
    fun `the selection controller copies under the same rule`() = runTest(dispatcher) {
        document.canCopyText = false
        document.textAreas = mapOf(0 to listOf(area.bounds))
        document.selections =
            mapOf(0 to TextSelection(0, listOf(area), "bail", PdfPoint(10f, 30f), PdfPoint(100f, 30f)))
        val viewModel = openViewModel()
        val selection = viewModel.selectionController
        assertThat(selection.onLongPress(0, PdfPoint(50f, 20f))).isTrue()

        selection.effects.test {
            selection.onCopy()
            assertThat(awaitItem()).isEqualTo(SelectionEffect.CopyRefused)
            settings.setIgnoreCopyRestriction(true)
            advanceUntilIdle()
            selection.onCopy()
            // Opened without a password: copied as is.
            assertThat(awaitItem()).isEqualTo(SelectionEffect.Copy("bail", isSensitive = false))
        }
    }
}
