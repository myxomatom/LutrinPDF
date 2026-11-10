// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.GetDocumentNameUseCase
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.recents.GetStartPageUseCase
import io.github.myxomatom.lutrin.core.domain.recents.IsRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocument
import io.github.myxomatom.lutrin.core.domain.recents.RecordDocumentOpeningUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RemoveRecentDocumentUseCase
import io.github.myxomatom.lutrin.core.domain.recents.SaveLastPageUseCase
import io.github.myxomatom.lutrin.core.domain.recents.documentIdFor
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetPagePreviewUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetScrollModeUseCase
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FAKE_NOW
import io.github.myxomatom.lutrin.core.testing.FakeDocumentNameResolver
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import io.github.myxomatom.lutrin.core.testing.FakeRecentDocumentsRepository
import io.github.myxomatom.lutrin.core.testing.FakeSettingsRepository
import java.lang.reflect.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
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
 * Encrypted documents in the reader (F10, V4, ADR 0017): the password is asked, a wrong one asks
 * again, the right one opens the document through the normal path, and the password is never
 * kept anywhere the ViewModel can reach.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderViewModelPasswordTest {
    private val dispatcher = StandardTestDispatcher()
    private val uri = "content://com.example.provider/document/releve%20de%20compte.pdf"
    private val engine = FakePdfEngine(Result.success(FakePdfDocument(List(PAGE_COUNT) { A4 })), password = PASSWORD)
    private var recents = FakeRecentDocumentsRepository()
    private val names = FakeDocumentNameResolver()
    private val handle = SavedStateHandle(mapOf(READER_URI_ARG to uri))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): ReaderViewModel = ReaderViewModel(
        handle,
        engine,
        dispatcher,
        ObserveSettingsUseCase(FakeSettingsRepository()),
        SetScrollModeUseCase(FakeSettingsRepository()),
        SetPagePreviewUseCase(FakeSettingsRepository()),
        ReadingHistory(
            GetStartPageUseCase(recents),
            RecordDocumentOpeningUseCase(recents),
            SaveLastPageUseCase(recents),
            IsRecentDocumentUseCase(recents),
            RemoveRecentDocumentUseCase(recents),
            GetDocumentNameUseCase(recents, names),
            this,
        ),
    ).also { advanceUntilIdle() }

    private val ReaderViewModel.asked: ReaderUiState.PasswordRequired
        get() = uiState.value as ReaderUiState.PasswordRequired

    private fun TestScope.submit(viewModel: ReaderViewModel, password: String) {
        viewModel.onPasswordSubmit(StringBuilder(password))
        advanceUntilIdle()
    }

    @Test
    fun `an encrypted document asks for its password`() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertThat(viewModel.asked).isEqualTo(ReaderUiState.PasswordRequired("releve de compte"))
        assertThat(engine.givenPasswords).containsExactly(null)
    }

    @Test
    fun `the question names the document as its provider does`() = runTest(dispatcher) {
        names.names = mapOf(uri to "Relevé de compte")

        assertThat(viewModel().asked.documentName).isEqualTo("Relevé de compte")
    }

    @Test
    fun `the question names a recent document as the library does`() = runTest(dispatcher) {
        recents = FakeRecentDocumentsRepository(listOf(recentEntry(lastPage = 0, name = "Relevé d'octobre")))
        names.names = mapOf(uri to "other name")

        assertThat(viewModel().asked.documentName).isEqualTo("Relevé d'octobre")
    }

    @Test
    fun `a wrong password asks again, then the right one opens the document`() = runTest(dispatcher) {
        val viewModel = viewModel()

        submit(viewModel, "wrong")
        assertThat(viewModel.asked).isEqualTo(ReaderUiState.PasswordRequired("releve de compte", isIncorrect = true))

        submit(viewModel, PASSWORD)
        assertThat(viewModel.uiState.value).isInstanceOf(ReaderUiState.Ready::class.java)
        assertThat(engine.givenPasswords).containsExactly(null, "wrong", PASSWORD).inOrder()
    }

    @Test
    fun `every attempt is allowed, without limit`() = runTest(dispatcher) {
        val viewModel = viewModel()

        repeat(ATTEMPTS) { submit(viewModel, "wrong $it") }
        submit(viewModel, PASSWORD)

        assertThat(viewModel.uiState.value).isInstanceOf(ReaderUiState.Ready::class.java)
    }

    @Test
    fun `waits for the check of a password before another one`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onPasswordSubmit("first")
        viewModel.onPasswordSubmit("second")
        advanceUntilIdle()

        assertThat(engine.givenPasswords).containsExactly(null, "first").inOrder()
        assertThat(viewModel.asked.isChecking).isFalse()
    }

    @Test
    fun `an empty password is not tried`() = runTest(dispatcher) {
        val viewModel = viewModel()

        submit(viewModel, "")

        assertThat(viewModel.asked).isEqualTo(ReaderUiState.PasswordRequired("releve de compte"))
        assertThat(engine.givenPasswords).containsExactly(null)
    }

    @Test
    fun `a password is ignored when none is asked`() = runTest(dispatcher) {
        engine.password = null
        val viewModel = viewModel()

        submit(viewModel, PASSWORD)

        assertThat(engine.givenPasswords).containsExactly(null)
        assertThat(viewModel.uiState.value).isInstanceOf(ReaderUiState.Ready::class.java)
    }

    @Test
    fun `a document opened with its password enters the recents as protected`() = runTest(dispatcher) {
        submit(viewModel(), PASSWORD)

        val entry = recents.recentDocuments.value.single()
        assertThat(entry.isProtected).isTrue()
        assertThat(entry.isThumbnailOutdated).isFalse()
        assertThat(recents.thumbnailRequests).isEmpty()
    }

    @Test
    fun `a document restricted by its owner only opens at once and is not protected`() = runTest(dispatcher) {
        engine.password = null

        val viewModel = viewModel()

        assertThat(viewModel.uiState.value).isInstanceOf(ReaderUiState.Ready::class.java)
        assertThat(recents.recentDocuments.value.single().isProtected).isFalse()
    }

    @Test
    fun `a protected document resumes at its saved page once unlocked`() = runTest(dispatcher) {
        recents = FakeRecentDocumentsRepository(listOf(recentEntry(lastPage = 4)))
        val viewModel = viewModel()

        submit(viewModel, PASSWORD)

        assertThat((viewModel.uiState.value as ReaderUiState.Ready).currentPage).isEqualTo(4)
    }

    @Test
    fun `a document that became inaccessible meanwhile shows the error screen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        engine.password = null
        engine.result = Result.failure(PdfError.NotAccessible())

        submit(viewModel, PASSWORD)

        assertThat(viewModel.uiState.value).isEqualTo(ReaderUiState.Error(ReaderError.NOT_ACCESSIBLE))
    }

    @Test
    fun `leaving while the password is asked records nothing`() = runTest(dispatcher) {
        val store = ViewModelStore()
        val viewModel = viewModel().also { store.put("reader", it) }
        submit(viewModel, "wrong")

        store.clear()
        advanceUntilIdle()

        assertThat(recents.recentDocuments.value).isEmpty()
        assertThat(recents.savedPages).isEmpty()
    }

    @Test
    fun `the state never holds the password`() = runTest(dispatcher) {
        val viewModel = viewModel()
        submit(viewModel, SECRET)
        val asked = viewModel.uiState.value.toString()
        engine.password = SECRET
        submit(viewModel, SECRET)

        assertThat(asked).doesNotContain(SECRET)
        assertThat(viewModel.uiState.value.toString()).doesNotContain(SECRET)
    }

    @Test
    fun `the ViewModel keeps no reference to a password once tried`() = runTest(dispatcher) {
        val viewModel = viewModel()
        submit(viewModel, SECRET)
        assertThat(heldStrings(viewModel)).doesNotContain(SECRET)

        engine.password = SECRET
        submit(viewModel, SECRET)

        assertThat(viewModel.uiState.value).isInstanceOf(ReaderUiState.Ready::class.java)
        assertThat(heldStrings(viewModel)).doesNotContain(SECRET)
    }

    @Test
    fun `the password is never saved in the saved state`() = runTest(dispatcher) {
        val viewModel = viewModel()
        submit(viewModel, SECRET)

        assertThat(handle.keys()).containsExactly(READER_URI_ARG)
        assertThat(handle.keys().map { handle.get<Any?>(it).toString() }).doesNotContain(SECRET)
    }

    private fun recentEntry(lastPage: Int, name: String = "Relevé de compte") = RecentDocument(
        documentId = documentIdFor(uri),
        uri = uri,
        displayName = name,
        sizeBytes = null,
        pageCount = PAGE_COUNT,
        lastOpenedAt = FAKE_NOW,
        lastPage = lastPage,
        thumbnailPath = null,
        isProtected = true,
    )

    /**
     * Every string, character sequence and character array reachable from the fields of
     * [viewModel], following its own objects (not the injected collaborators, nor the JDK).
     */
    private fun heldStrings(viewModel: ReaderViewModel): List<String> {
        val found = mutableListOf<String>()
        val seen = mutableSetOf<Int>()
        fun visit(value: Any?, depth: Int) {
            if (value == null || depth > MAX_DEPTH || !seen.add(System.identityHashCode(value))) return
            when (value) {
                is CharSequence -> found += value.toString()

                is CharArray -> found += String(value)

                is Iterable<*> -> value.forEach { visit(it, depth + 1) }

                is StateFlow<*> -> visit(value.value, depth + 1)

                else -> if (value.javaClass.name.startsWith(APP_PACKAGE)) {
                    fieldsOf(value).forEach {
                        visit(it, depth + 1)
                    }
                }
            }
        }
        fieldsOf(viewModel).forEach { visit(it, 0) }
        return found
    }

    private fun fieldsOf(value: Any): List<Any?> = generateSequence(value.javaClass as Class<*>) { it.superclass }
        .takeWhile { it != Any::class.java }
        .flatMap { it.declaredFields.asSequence() }
        .filterNot { Modifier.isStatic(it.modifiers) }
        .map { field -> runCatching { field.apply { isAccessible = true }.get(value) }.getOrNull() }
        // The fake engine records the passwords it is given, on purpose: it is the test's, not the reader's.
        .filterNot { it is FakePdfEngine }
        .toList()

    private companion object {
        const val PAGE_COUNT = 6
        const val ATTEMPTS = 25

        /** Test data, not a secret. */
        const val PASSWORD = "lutrin"
        const val SECRET = "s3cr3t-pa55"
        const val APP_PACKAGE = "io.github.myxomatom.lutrin"
        const val MAX_DEPTH = 6
    }
}
