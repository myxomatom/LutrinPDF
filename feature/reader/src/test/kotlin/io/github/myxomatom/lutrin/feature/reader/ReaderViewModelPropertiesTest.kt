// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.DocumentFileInfo
import io.github.myxomatom.lutrin.core.domain.document.DocumentProperties
import io.github.myxomatom.lutrin.core.domain.document.GetDocumentNameUseCase
import io.github.myxomatom.lutrin.core.domain.document.GetDocumentPropertiesUseCase
import io.github.myxomatom.lutrin.core.domain.pdf.PdfMetadata
import io.github.myxomatom.lutrin.core.domain.recents.GetStartPageUseCase
import io.github.myxomatom.lutrin.core.domain.recents.IsRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecordDocumentOpeningUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RemoveRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.SaveLastPageUseCase
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
import java.time.Instant
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
 * The properties sheet (F16, V5) and the sharing (F15) in [ReaderViewModel] (ADR 0027): what the
 * sheet shows and when it opens, and the request handed to the system chooser.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderViewModelPropertiesTest {
    private val dispatcher = StandardTestDispatcher()
    private val uri = "content://com.example.provider/document/rapport%20annuel.pdf"
    private val created = Instant.parse("2026-09-12T08:14:00Z")
    private val modified = Instant.parse("2026-09-14T14:02:00Z")
    private val document =
        FakePdfDocument(pageSizes = List(8) { A4 }, title = null).apply {
            metadata =
                PdfMetadata(
                    pageCount = 8,
                    title = "Contrat de location",
                    author = "A. Auteur",
                    creationDate = created,
                    modificationDate = modified,
                    format = "PDF 1.7",
                )
        }
    private val infos =
        FakeDocumentInfoResolver(mapOf(uri to DocumentFileInfo("contrat_location_2026.pdf", 421_888)))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.openViewModel(
        engine: FakePdfEngine = FakePdfEngine(Result.success(document)),
        viewModelUri: String = uri,
    ): ReaderViewModel {
        val recents = FakeRecentDocumentsRepository()
        return ReaderViewModel(
            SavedStateHandle(mapOf(READER_URI_ARG to viewModelUri)),
            engine,
            dispatcher,
            dispatcher,
            ObserveSettingsUseCase(FakeSettingsRepository()),
            SetScrollModeUseCase(FakeSettingsRepository()),
            SetDocumentColorModeUseCase(FakeSettingsRepository()),
            SetFitModeUseCase(FakeSettingsRepository()),
            SetLockOrientationUseCase(FakeSettingsRepository()),
            ReadingHistory(
                GetStartPageUseCase(recents),
                RecordDocumentOpeningUseCase(recents),
                SaveLastPageUseCase(recents),
                IsRecentDocumentUseCase(recents),
                RemoveRecentDocumentUseCase(recents),
                GetDocumentNameUseCase(recents, FakeDocumentNameResolver()),
                this,
            ),
            GetDocumentPropertiesUseCase(infos),
        ).also { advanceUntilIdle() }
    }

    @Test
    fun `no properties are shown until they are asked for`() = runTest(dispatcher) {
        assertThat(openViewModel().properties.value).isNull()
    }

    @Test
    fun `asking for the properties gathers the document and file information`() = runTest(dispatcher) {
        val viewModel = openViewModel()

        viewModel.onPropertiesRequested()
        advanceUntilIdle()

        assertThat(viewModel.properties.value)
            .isEqualTo(
                DocumentProperties(
                    fileName = "contrat_location_2026.pdf",
                    title = "Contrat de location",
                    author = "A. Auteur",
                    pageCount = 8,
                    sizeBytes = 421_888,
                    pdfVersion = "1.7",
                    createdAt = created,
                    modifiedAt = modified,
                ),
            )
    }

    @Test
    fun `a provider that knows nothing leaves the file name and size out`() = runTest(dispatcher) {
        infos.infos = emptyMap()
        val viewModel = openViewModel()

        viewModel.onPropertiesRequested()
        advanceUntilIdle()

        assertThat(viewModel.properties.value?.fileName).isNull()
        assertThat(viewModel.properties.value?.sizeBytes).isNull()
        assertThat(viewModel.properties.value?.title).isEqualTo("Contrat de location")
    }

    @Test
    fun `dismissing the sheet forgets the properties`() = runTest(dispatcher) {
        val viewModel = openViewModel()
        viewModel.onPropertiesRequested()
        advanceUntilIdle()

        viewModel.onPropertiesDismissed()

        assertThat(viewModel.properties.value).isNull()
    }

    @Test
    fun `dismissing before the answer arrives keeps the sheet closed`() = runTest(dispatcher) {
        val viewModel = openViewModel()

        viewModel.onPropertiesRequested()
        viewModel.onPropertiesDismissed()
        advanceUntilIdle()

        assertThat(viewModel.properties.value).isNull()
    }

    @Test
    fun `properties asked for before the document is open are ignored`() = runTest(dispatcher) {
        val recents = FakeRecentDocumentsRepository()
        val viewModel =
            ReaderViewModel(
                SavedStateHandle(mapOf(READER_URI_ARG to uri)),
                FakePdfEngine(Result.failure(IllegalStateException("never opens"))),
                dispatcher,
                dispatcher,
                ObserveSettingsUseCase(FakeSettingsRepository()),
                SetScrollModeUseCase(FakeSettingsRepository()),
                SetDocumentColorModeUseCase(FakeSettingsRepository()),
                SetFitModeUseCase(FakeSettingsRepository()),
                SetLockOrientationUseCase(FakeSettingsRepository()),
                ReadingHistory(
                    GetStartPageUseCase(recents),
                    RecordDocumentOpeningUseCase(recents),
                    SaveLastPageUseCase(recents),
                    IsRecentDocumentUseCase(recents),
                    RemoveRecentDocumentUseCase(recents),
                    GetDocumentNameUseCase(recents, FakeDocumentNameResolver()),
                    this,
                ),
                GetDocumentPropertiesUseCase(infos),
            )
        advanceUntilIdle()

        viewModel.onPropertiesRequested()
        advanceUntilIdle()

        assertThat(viewModel.properties.value).isNull()
    }

    @Test
    fun `sharing sends the document as it is, from its own URI`() = runTest(dispatcher) {
        val request = checkNotNull(openViewModel().shareRequest())

        assertThat(request.uri).isEqualTo(uri.toUri())
        // The name of the clip is the one the recent list holds: the title of the document.
        assertThat(request.name).isEqualTo("Contrat de location")
    }

    @Test
    fun `a document opened with its password is shared from the same URI, never a decrypted copy`() =
        runTest(dispatcher) {
            val viewModel = openViewModel(FakePdfEngine(Result.success(document), password = "lutrin"))
            viewModel.onPasswordSubmit("lutrin")
            advanceUntilIdle()

            assertThat(viewModel.shareRequest()?.uri).isEqualTo(uri.toUri())
        }

    @Test
    fun `nothing is shared before the document is open`() = runTest(dispatcher) {
        val viewModel = openViewModel(FakePdfEngine(Result.success(document), password = "lutrin"))

        assertThat(viewModel.shareRequest()).isNull()
    }

    @Test
    fun `a URI that is not a content URI is not shared`() = runTest(dispatcher) {
        val viewModel = openViewModel(viewModelUri = "file:///sdcard/Download/rapport.pdf")

        assertThat(viewModel.shareRequest()).isNull()
    }
}
