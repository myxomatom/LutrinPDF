// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.IncomingDocumentResult
import io.github.myxomatom.lutrin.core.domain.document.IncomingIntent
import io.github.myxomatom.lutrin.core.domain.document.IncomingRejection
import io.github.myxomatom.lutrin.core.domain.document.ParseIncomingDocumentUseCase
import io.github.myxomatom.lutrin.core.testing.FakeDocumentTypeResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val types = FakeDocumentTypeResolver()
    private val viewModel by lazy { MainViewModel(ParseIncomingDocumentUseCase(types)) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun view(uri: String?, type: String? = PDF, page: Int? = null) =
        IncomingIntent(IncomingIntent.ACTION_VIEW, data = uri, type = type, page = page, receiverPackage = PACKAGE)

    @Test
    fun `starts without request`() {
        assertThat(viewModel.incomingRequest.value).isNull()
    }

    @Test
    fun `a view intent becomes a request to open the document`() = runTest(dispatcher) {
        viewModel.onIntent(view(URI, page = 4))
        advanceUntilIdle()

        assertThat(viewModel.incomingRequest.value).isEqualTo(IncomingDocumentResult.Accepted(URI, pageIndex = 3))
    }

    @Test
    fun `an invalid intent becomes a refusal`() = runTest(dispatcher) {
        viewModel.onIntent(view("file:///sdcard/a.pdf"))
        advanceUntilIdle()

        assertThat(
            viewModel.incomingRequest.value,
        ).isEqualTo(IncomingDocumentResult.Rejected(IncomingRejection.UNSUPPORTED_SCHEME))
    }

    @Test
    fun `the launcher intent leaves the state unchanged`() = runTest(dispatcher) {
        viewModel.onIntent(view(URI))
        advanceUntilIdle()

        viewModel.onIntent(IncomingIntent("android.intent.action.MAIN", receiverPackage = PACKAGE))
        advanceUntilIdle()

        assertThat(viewModel.incomingRequest.value).isEqualTo(IncomingDocumentResult.Accepted(URI))
    }

    @Test
    fun `a handled request is cleared`() = runTest(dispatcher) {
        viewModel.onIntent(view(URI))
        advanceUntilIdle()

        viewModel.onIncomingRequestHandled(IncomingDocumentResult.Accepted(URI))

        assertThat(viewModel.incomingRequest.value).isNull()
    }

    @Test
    fun `handling an older request keeps the newer one`() = runTest(dispatcher) {
        viewModel.onIntent(view(URI))
        advanceUntilIdle()
        viewModel.onIntent(view(OTHER_URI))
        advanceUntilIdle()

        viewModel.onIncomingRequestHandled(IncomingDocumentResult.Accepted(URI))

        assertThat(viewModel.incomingRequest.value).isEqualTo(IncomingDocumentResult.Accepted(OTHER_URI))
    }

    @Test
    fun `a newer intent replaces one still being checked`() = runTest(dispatcher) {
        viewModel.onIntent(view(URI, type = null))
        viewModel.onIntent(view(OTHER_URI))
        advanceUntilIdle()

        assertThat(viewModel.incomingRequest.value).isEqualTo(IncomingDocumentResult.Accepted(OTHER_URI))
    }

    @Test
    fun `the provider type is asked when the intent has none`() = runTest(dispatcher) {
        types.types = mapOf(URI to "image/png")

        viewModel.onIntent(view(URI, type = null))
        advanceUntilIdle()

        assertThat(types.queried).containsExactly(URI)
        assertThat(
            viewModel.incomingRequest.value,
        ).isEqualTo(IncomingDocumentResult.Rejected(IncomingRejection.UNSUPPORTED_TYPE))
    }

    private companion object {
        const val PACKAGE = "io.github.myxomatom.lutrin"
        const val PDF = "application/pdf"
        const val URI = "content://com.example.files/document/1"
        const val OTHER_URI = "content://com.example.files/document/2"
    }
}
