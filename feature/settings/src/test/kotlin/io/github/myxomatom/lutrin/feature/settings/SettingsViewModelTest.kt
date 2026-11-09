// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.recents.ClearRecentDocumentsUseCase
import io.github.myxomatom.lutrin.core.domain.recents.DocumentOpening
import io.github.myxomatom.lutrin.core.domain.recents.ObserveRecentDocumentsUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocument
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocumentsRepository
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetDynamicColorUseCase
import io.github.myxomatom.lutrin.core.testing.FakeRecentDocumentsRepository
import io.github.myxomatom.lutrin.core.testing.FakeSettingsRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

    private fun viewModel(
        repository: FakeSettingsRepository,
        recents: RecentDocumentsRepository = FakeRecentDocumentsRepository(),
        applicationScope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
    ) = SettingsViewModel(
        ObserveSettingsUseCase(repository),
        SetDynamicColorUseCase(repository),
        ObserveRecentDocumentsUseCase(recents),
        ClearRecentDocumentsUseCase(recents),
        applicationScope,
    )

    private suspend fun FakeRecentDocumentsRepository.open(vararg ids: Int) = apply {
        ids.forEach { recordOpening(DocumentOpening("content://files/$it", 8, 0, "doc$it")) }
    }

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

    @Test
    fun `counts the recent documents`() = runTest {
        val recents = FakeRecentDocumentsRepository().open(1, 2, 3)

        viewModel(FakeSettingsRepository(), recents).uiState.test {
            assertThat(awaitItem().recentCount).isEqualTo(3)
        }
    }

    @Test
    fun `clearing the recents empties the list and the count follows`() = runTest {
        val recents = FakeRecentDocumentsRepository().open(1, 2)
        val viewModel = viewModel(FakeSettingsRepository(), recents, applicationScope = this)

        viewModel.uiState.test {
            assertThat(awaitItem().recentCount).isEqualTo(2)
            viewModel.onClearRecents()
            advanceUntilIdle()
            assertThat(awaitItem().recentCount).isEqualTo(0)
        }
        assertThat(recents.clearCount).isEqualTo(1)
        assertThat(recents.recentDocuments.value).isEmpty()
    }

    @Test
    fun `clearing an empty list removes nothing`() = runTest {
        val recents = FakeRecentDocumentsRepository()
        val viewModel = viewModel(FakeSettingsRepository(), recents, applicationScope = this)

        viewModel.onClearRecents()
        advanceUntilIdle()

        assertThat(recents.clearCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.recentCount).isEqualTo(0)
    }

    @Test
    fun `a recent list that cannot be read counts as empty`() = runTest {
        val failing =
            object : RecentDocumentsRepository by FakeRecentDocumentsRepository() {
                override val recentDocuments: Flow<List<RecentDocument>> = flow { throw IOException("disk") }
            }

        viewModel(FakeSettingsRepository(AppSettings(dynamicColor = false)), failing).uiState.test {
            assertThat(awaitItem()).isEqualTo(SettingsUiState(loading = false, dynamicColor = false, recentCount = 0))
        }
    }
}
