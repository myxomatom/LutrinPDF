// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.GetDocumentNameUseCase
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
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

/** Links of [ReaderViewModel] (F09, ADR 0022). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderViewModelLinksTest {
    private val dispatcher = StandardTestDispatcher()
    private val area = PdfRect(10f, 10f, 100f, 30f)
    private val document = FakePdfDocument(pageSizes = List(5) { A4 })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.openViewModel(): ReaderViewModel {
        val recents = FakeRecentDocumentsRepository()
        val settings = FakeSettingsRepository()
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

    private val ReaderViewModel.ready: ReaderUiState.Ready get() = uiState.value as ReaderUiState.Ready

    @Test
    fun `an internal link goes to its destination and offers to come back`() = runTest(dispatcher) {
        val viewModel = openViewModel()
        val destination = PdfDestination(pageIndex = 3, x = 72f, y = 400f)

        viewModel.linkController.effects.test {
            viewModel.linkController.onLinkClick(PdfLink.Internal(area, destination), fromPage = 1)

            assertThat(awaitItem()).isEqualTo(LinkEffect.GoTo(destination, returnPage = 1))
        }
    }

    @Test
    fun `an internal link within the page shown offers no return`() = runTest(dispatcher) {
        val viewModel = openViewModel()
        val destination = PdfDestination(pageIndex = 2, y = 500f)

        viewModel.linkController.effects.test {
            viewModel.linkController.onLinkClick(PdfLink.Internal(area, destination), fromPage = 2)

            assertThat(awaitItem()).isEqualTo(LinkEffect.GoTo(destination, returnPage = null))
        }
    }

    @Test
    fun `an internal link beyond the last page is ignored`() = runTest(dispatcher) {
        val viewModel = openViewModel()

        viewModel.linkController.effects.test {
            viewModel.linkController.onLinkClick(PdfLink.Internal(area, PdfDestination(pageIndex = 5)), fromPage = 0)

            expectNoEvents()
        }
    }

    @Test
    fun `an accepted external link waits for confirmation`() = runTest(dispatcher) {
        val viewModel = openViewModel()

        viewModel.linkController.externalLink.test {
            assertThat(awaitItem()).isNull()

            viewModel.linkController.onLinkClick(PdfLink.External(area, " HTTPS://example.org/a "), fromPage = 0)

            assertThat(awaitItem()).isEqualTo("https://example.org/a")
        }
    }

    @Test
    fun `confirming opens the link once and closes the dialog`() = runTest(dispatcher) {
        val viewModel = openViewModel()
        viewModel.linkController.onExternalLink("mailto:contact@example.org")

        viewModel.linkController.effects.test {
            viewModel.linkController.onExternalLinkConfirm()
            viewModel.linkController.onExternalLinkConfirm()

            assertThat(awaitItem()).isEqualTo(LinkEffect.OpenExternalLink("mailto:contact@example.org"))
            expectNoEvents()
        }
        assertThat(viewModel.linkController.externalLink.value).isNull()
    }

    @Test
    fun `dismissing closes the dialog without opening the link`() = runTest(dispatcher) {
        val viewModel = openViewModel()
        viewModel.linkController.onExternalLink("https://example.org")

        viewModel.linkController.effects.test {
            viewModel.linkController.onExternalLinkDismiss()
            viewModel.linkController.onExternalLinkConfirm()

            expectNoEvents()
        }
        assertThat(viewModel.linkController.externalLink.value).isNull()
    }

    @Test
    fun `a refused scheme is reported and never asks for confirmation`() = runTest(dispatcher) {
        val viewModel = openViewModel()

        viewModel.linkController.effects.test {
            listOf("javascript:alert(1)", "file:///sdcard/a.pdf", "content://x/y", "intent://z", "chapitre.pdf")
                .forEach { uri ->
                    viewModel.linkController.onLinkClick(PdfLink.External(area, uri), fromPage = 0)
                    assertThat(awaitItem()).isEqualTo(LinkEffect.LinkRefused)
                }
        }
        assertThat(viewModel.linkController.externalLink.value).isNull()
    }

    @Test
    fun `a second external link is ignored while the first one is asked about`() = runTest(dispatcher) {
        val viewModel = openViewModel()
        viewModel.linkController.onExternalLink("https://example.org/first")

        viewModel.linkController.onExternalLink("https://example.org/second")

        assertThat(viewModel.linkController.externalLink.value).isEqualTo("https://example.org/first")
    }

    @Test
    fun `the links of a page are read from the document and cached`() = runTest(dispatcher) {
        val link = PdfLink.External(area, "https://example.org")
        document.links = mapOf(2 to listOf(link))
        val viewModel = openViewModel()
        val links = viewModel.ready.links

        assertThat(links.cached(2)).isNull()
        assertThat(links.linksOf(2)).containsExactly(link)
        assertThat(links.cached(2)).containsExactly(link)
        assertThat(links.linksOf(0)).isEmpty()
    }
}
