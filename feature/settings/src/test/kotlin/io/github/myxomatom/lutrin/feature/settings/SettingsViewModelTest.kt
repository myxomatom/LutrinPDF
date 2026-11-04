// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetDynamicColorUseCase
import io.github.myxomatom.lutrin.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeSettingsRepository) =
        SettingsViewModel(ObserveSettingsUseCase(repository), SetDynamicColorUseCase(repository))

    @Test
    fun `is loading until the settings are read`() {
        assertThat(viewModel(FakeSettingsRepository()).uiState.value).isEqualTo(SettingsUiState())
    }

    @Test
    fun `shows the stored settings`() = runTest {
        viewModel(FakeSettingsRepository(AppSettings(dynamicColor = false))).uiState.test {
            assertThat(awaitItem()).isEqualTo(SettingsUiState(loading = false, dynamicColor = false))
        }
    }

    @Test
    fun `turning dynamic colours off stores the choice`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertThat(awaitItem().dynamicColor).isTrue()
            viewModel.onDynamicColorChange(false)
            assertThat(awaitItem().dynamicColor).isFalse()
        }
        assertThat(repository.settings.value.dynamicColor).isFalse()
    }
}
