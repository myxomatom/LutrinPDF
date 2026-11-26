// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AboutViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(source: LicensesSource = FakeLicensesSource(), version: String? = "1.2.3") =
        AboutViewModel({ version }, source, dispatcher)

    private fun AboutViewModel.components() = (uiState.value.licenses as LicensesState.Loaded).components

    @Test
    fun `the version comes from the provider`() {
        assertThat(viewModel(version = "1.2.3").uiState.value.versionName).isEqualTo("1.2.3")
    }

    @Test
    fun `an unknown version stays null`() {
        assertThat(viewModel(version = null).uiState.value.versionName).isNull()
    }

    @Test
    fun `the licenses are read once and sorted`() {
        val source = FakeLicensesSource()

        val viewModel = viewModel(source)

        assertThat(viewModel.components().map { it.name }).containsExactly("alpha", "MuPDF", "Zeta").inOrder()
        assertThat(source.reads).isEqualTo(1)
    }

    @Test
    fun `the licenses are unavailable when the file cannot be read`() {
        assertThat(viewModel(FakeLicensesSource(json = null)).uiState.value.licenses)
            .isEqualTo(LicensesState.Unavailable)
    }

    @Test
    fun `the licenses are unavailable when the file is not valid`() {
        assertThat(viewModel(FakeLicensesSource(json = "{")).uiState.value.licenses)
            .isEqualTo(LicensesState.Unavailable)
    }

    @Test
    fun `a file without any library counts as unreadable`() {
        val viewModel = viewModel(FakeLicensesSource(json = """{"libraries": [], "licenses": {}}"""))

        assertThat(viewModel.uiState.value.licenses).isEqualTo(LicensesState.Unavailable)
    }

    @Test
    fun `the main page is shown first and cannot be left by back from a page`() {
        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.page).isEqualTo(AboutPage.Main)
        assertThat(viewModel.onBackFromPage()).isFalse()
    }

    @Test
    fun `opening the privacy policy then going back shows the main page again`() {
        val viewModel = viewModel()

        viewModel.onOpenPrivacy()
        assertThat(viewModel.uiState.value.page).isEqualTo(AboutPage.Privacy)

        assertThat(viewModel.onBackFromPage()).isTrue()
        assertThat(viewModel.uiState.value.page).isEqualTo(AboutPage.Main)
    }

    @Test
    fun `opening a component shows its page`() {
        val viewModel = viewModel()

        viewModel.onOpenComponent("com.artifex.mupdf:mupdf")

        assertThat(viewModel.uiState.value.page).isEqualTo(AboutPage.Component("com.artifex.mupdf:mupdf"))
    }

    @Test
    fun `tapping the source code asks for confirmation and sends nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onSourceCodeClick()

            assertThat(viewModel.uiState.value.confirmingSourceLink).isTrue()
            expectNoEvents()
        }
    }

    @Test
    fun `cancelling the confirmation opens nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onSourceCodeClick()
            viewModel.onSourceCodeDismiss()

            assertThat(viewModel.uiState.value.confirmingSourceLink).isFalse()
            expectNoEvents()
        }
    }

    @Test
    fun `confirming asks to open the repository once and closes the confirmation`() = runTest {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onSourceCodeClick()
            viewModel.onSourceCodeConfirm()

            assertThat(awaitItem()).isEqualTo(AboutEffect.OpenSourceCode("https://github.com/myxomatom/LutrinPDF"))
            assertThat(viewModel.uiState.value.confirmingSourceLink).isFalse()
            expectNoEvents()
        }
    }
}
