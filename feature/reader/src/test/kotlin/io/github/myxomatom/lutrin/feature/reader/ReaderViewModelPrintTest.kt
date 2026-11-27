// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.GetDocumentNameUseCase
import io.github.myxomatom.lutrin.core.domain.document.GetDocumentPropertiesUseCase
import io.github.myxomatom.lutrin.core.domain.recents.GetSavedRotationUseCase
import io.github.myxomatom.lutrin.core.domain.recents.GetStartPageUseCase
import io.github.myxomatom.lutrin.core.domain.recents.IsRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecordDocumentOpeningUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RemoveRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.SaveLastPageUseCase
import io.github.myxomatom.lutrin.core.domain.recents.SaveRotationUseCase
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetDocumentColorModeUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetFitModeUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetLockOrientationUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetScrollModeUseCase
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakeDocumentInfoResolver
import io.github.myxomatom.lutrin.core.testing.FakeDocumentNameResolver
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import io.github.myxomatom.lutrin.core.testing.FakeRecentDocumentsRepository
import io.github.myxomatom.lutrin.core.testing.FakeSettingsRepository
import io.github.myxomatom.lutrin.feature.reader.print.PrintSource
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

/**
 * Printing in [ReaderViewModel] (F15, ADR 0026): the print permission of the document and the
 * setting that ignores it, followed live; the original file for a document opened without a
 * password, images of the pages for a protected one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderViewModelPrintTest {
    private val dispatcher = StandardTestDispatcher()
    private val io = StandardTestDispatcher(dispatcher.scheduler)
    private val uri = "content://com.example.provider/document/rapport%20annuel.pdf"
    private val document = FakePdfDocument(pageSizes = List(4) { A4 }, title = null)
    private val settings = FakeSettingsRepository()
    private val names = FakeDocumentNameResolver()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.openViewModel(
        engine: FakePdfEngine = FakePdfEngine(Result.success(document)),
    ): ReaderViewModel {
        val recents = FakeRecentDocumentsRepository()
        return ReaderViewModel(
            SavedStateHandle(mapOf(READER_URI_ARG to uri)),
            engine,
            dispatcher,
            io,
            ObserveSettingsUseCase(settings),
            SetScrollModeUseCase(settings),
            SetDocumentColorModeUseCase(settings),
            SetFitModeUseCase(settings),
            SetLockOrientationUseCase(settings),
            ReadingHistory(
                GetStartPageUseCase(recents),
                GetSavedRotationUseCase(recents),
                SaveRotationUseCase(recents),
                RecordDocumentOpeningUseCase(recents),
                SaveLastPageUseCase(recents),
                IsRecentDocumentUseCase(recents),
                RemoveRecentDocumentUseCase(recents),
                GetDocumentNameUseCase(recents, names),
                this,
            ),
            GetDocumentPropertiesUseCase(FakeDocumentInfoResolver()),
        ).also { advanceUntilIdle() }
    }

    private fun ReaderViewModel.canPrint() = (uiState.value as ReaderUiState.Ready).canPrint

    @Test
    fun `a document that allows printing can be printed`() = runTest(dispatcher) {
        assertThat(openViewModel().canPrint()).isTrue()
    }

    @Test
    fun `printing follows the permission of the document and the setting, live`() = runTest(dispatcher) {
        document.canPrint = false
        val viewModel = openViewModel()

        assertThat(viewModel.canPrint()).isFalse()
        assertThat(viewModel.printJob()).isNull()

        settings.setIgnoreDocumentRestrictions(true)
        advanceUntilIdle()
        assertThat(viewModel.canPrint()).isTrue()
        assertThat(viewModel.printJob()).isNotNull()

        settings.setIgnoreDocumentRestrictions(false)
        advanceUntilIdle()
        assertThat(viewModel.canPrint()).isFalse()
        assertThat(viewModel.printJob()).isNull()
    }

    @Test
    fun `the copy permission does not decide printing`() = runTest(dispatcher) {
        document.canCopyText = false
        val viewModel = openViewModel()

        assertThat(viewModel.canPrint()).isTrue()
        assertThat((viewModel.uiState.value as ReaderUiState.Ready).canCopyText).isFalse()
    }

    @Test
    fun `a document opened without a password is printed from its file, named after it`() = runTest(dispatcher) {
        val job = checkNotNull(openViewModel().printJob())

        assertThat(job.name).isEqualTo("rapport annuel")
        assertThat(job.dispatcher).isSameInstanceAs(io)
        assertThat(job.source).isEqualTo(PrintSource.Original(uri.toUri(), pageCount = 4))
    }

    @Test
    fun `the print job takes the name of the document in the recent list`() = runTest(dispatcher) {
        val titled = FakePdfDocument(pageSizes = List(4) { A4 }, title = "Rapport 2026")

        assertThat(openViewModel(FakePdfEngine(Result.success(titled))).printJob()?.name).isEqualTo("Rapport 2026")
    }

    @Test
    fun `a document opened with its password is printed as images of its pages`() = runTest(dispatcher) {
        val viewModel = openViewModel(FakePdfEngine(Result.success(document), password = "lutrin"))
        viewModel.onPasswordSubmit("lutrin")
        advanceUntilIdle()

        val source = viewModel.printJob()?.source

        assertThat(source).isInstanceOf(PrintSource.Rendered::class.java)
        source as PrintSource.Rendered
        assertThat(source.document).isSameInstanceAs(document)
        assertThat(source.pageSizes).isEqualTo(document.pageSizes)
    }

    @Test
    fun `nothing to print while the password is asked`() = runTest(dispatcher) {
        val viewModel = openViewModel(FakePdfEngine(Result.success(document), password = "lutrin"))

        assertThat(viewModel.printJob()).isNull()
    }
}
