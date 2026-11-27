// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.recents.ClearRecentDocumentsUseCase
import io.github.myxomatom.lutrin.core.domain.recents.DocumentOpening
import io.github.myxomatom.lutrin.core.domain.recents.ObserveRecentDocumentsUseCase
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocument
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocumentsRepository
import io.github.myxomatom.lutrin.core.domain.settings.AppLanguage
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.AppTheme
import io.github.myxomatom.lutrin.core.domain.settings.DocumentColorMode
import io.github.myxomatom.lutrin.core.domain.settings.DocumentColors
import io.github.myxomatom.lutrin.core.domain.settings.GetAppLanguageUseCase
import io.github.myxomatom.lutrin.core.domain.settings.ObserveSettingsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetAppLanguageUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetAppThemeUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetDynamicColorUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetFollowDarkThemeUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetIgnoreDocumentRestrictionsUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetKeepScreenOnUseCase
import io.github.myxomatom.lutrin.core.domain.settings.SetPagePreviewUseCase
import io.github.myxomatom.lutrin.core.testing.FakeAppLanguageController
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
        language: FakeAppLanguageController = FakeAppLanguageController(isSupported = false),
    ) = SettingsViewModel(
        ObserveSettingsUseCase(repository),
        SetDynamicColorUseCase(repository),
        SetPagePreviewUseCase(repository),
        ObserveRecentDocumentsUseCase(recents),
        ClearRecentDocumentsUseCase(recents),
        applicationScope,
        SetIgnoreDocumentRestrictionsUseCase(repository),
        SetAppThemeUseCase(repository),
        SetFollowDarkThemeUseCase(repository),
        SetKeepScreenOnUseCase(repository),
        GetAppLanguageUseCase(language),
        SetAppLanguageUseCase(language),
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
    fun `the screen may sleep by default and the switch stores the choice`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertThat(awaitItem().keepScreenOn).isFalse()
            viewModel.onKeepScreenOnChange(true)
            assertThat(awaitItem().keepScreenOn).isTrue()
        }
        assertThat(repository.settings.value.keepScreenOn).isTrue()
        assertThat(repository.settings.value.dynamicColor).isTrue()
    }

    @Test
    fun `there is no language to choose where the system does not offer it`() = runTest {
        val language = FakeAppLanguageController(isSupported = false, initial = AppLanguage.FRENCH)

        viewModel(FakeSettingsRepository(), language = language).uiState.test {
            assertThat(awaitItem().appLanguage).isNull()
        }
    }

    @Test
    fun `shows the language chosen in the system settings`() = runTest {
        val language = FakeAppLanguageController(isSupported = true, initial = AppLanguage.FRENCH)

        viewModel(FakeSettingsRepository(), language = language).uiState.test {
            assertThat(expectMostRecentItem().appLanguage).isEqualTo(AppLanguage.FRENCH)
        }
    }

    @Test
    fun `choosing a language shows it and hands it to the system`() = runTest {
        val language = FakeAppLanguageController(isSupported = true)
        val viewModel = viewModel(FakeSettingsRepository(), language = language)

        viewModel.uiState.test {
            assertThat(expectMostRecentItem().appLanguage).isEqualTo(AppLanguage.SYSTEM)
            viewModel.onAppLanguageChange(AppLanguage.ENGLISH)
            assertThat(awaitItem().appLanguage).isEqualTo(AppLanguage.ENGLISH)
        }
        assertThat(language.changes).containsExactly(AppLanguage.ENGLISH)
    }

    @Test
    fun `choosing a language where the system does not offer it changes nothing`() = runTest {
        val language = FakeAppLanguageController(isSupported = false)
        val viewModel = viewModel(FakeSettingsRepository(), language = language)

        viewModel.onAppLanguageChange(AppLanguage.FRENCH)

        assertThat(language.changes).isEmpty()
        assertThat(viewModel.uiState.value.appLanguage).isNull()
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
    fun `the page preview of the reader is on by default`() = runTest {
        viewModel(FakeSettingsRepository()).uiState.test {
            assertThat(awaitItem().pagePreview).isTrue()
        }
    }

    @Test
    fun `shows the stored page preview choice`() = runTest {
        viewModel(FakeSettingsRepository(AppSettings(pagePreview = false))).uiState.test {
            assertThat(awaitItem().pagePreview).isFalse()
        }
    }

    @Test
    fun `turning the page preview off and back on stores the choice`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertThat(awaitItem().pagePreview).isTrue()
            viewModel.onPagePreviewChange(false)
            assertThat(awaitItem().pagePreview).isFalse()
            assertThat(repository.settings.value.pagePreview).isFalse()

            viewModel.onPagePreviewChange(true)
            assertThat(awaitItem().pagePreview).isTrue()
        }
        assertThat(repository.settings.value.pagePreview).isTrue()
        assertThat(repository.settings.value.dynamicColor).isTrue()
    }

    @Test
    fun `the copy restriction of documents is respected by default`() = runTest {
        viewModel(FakeSettingsRepository()).uiState.test {
            assertThat(awaitItem().ignoreDocumentRestrictions).isFalse()
        }
    }

    @Test
    fun `ignoring the copy restriction and respecting it again stores the choice`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertThat(awaitItem().ignoreDocumentRestrictions).isFalse()
            viewModel.onIgnoreDocumentRestrictionsChange(true)
            assertThat(awaitItem().ignoreDocumentRestrictions).isTrue()
            assertThat(repository.settings.value.ignoreDocumentRestrictions).isTrue()

            viewModel.onIgnoreDocumentRestrictionsChange(false)
            assertThat(awaitItem().ignoreDocumentRestrictions).isFalse()
        }
        assertThat(repository.settings.value.pagePreview).isTrue()
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

    @Test
    fun `the app follows the system theme, and documents do not follow it, by default`() = runTest {
        viewModel(FakeSettingsRepository()).uiState.test {
            val state = awaitItem()
            assertThat(state.appTheme).isEqualTo(AppTheme.SYSTEM)
            assertThat(state.followDarkTheme).isFalse()
        }
    }

    @Test
    fun `choosing the app theme stores it`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertThat(awaitItem().appTheme).isEqualTo(AppTheme.SYSTEM)
            viewModel.onAppThemeChange(AppTheme.DARK)
            assertThat(awaitItem().appTheme).isEqualTo(AppTheme.DARK)
        }
        assertThat(repository.settings.value.appTheme).isEqualTo(AppTheme.DARK)
    }

    @Test
    fun `following the dark theme stores it and forgets the colours chosen in the reader`() = runTest {
        val colors = DocumentColors(chosen = DocumentColorMode.SEPIA)
        val repository = FakeSettingsRepository(AppSettings(documentColors = colors))
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertThat(awaitItem().followDarkTheme).isFalse()
            viewModel.onFollowDarkThemeChange(true)
            assertThat(expectMostRecentItem().followDarkTheme).isTrue()
        }
        assertThat(repository.settings.value.documentColors).isEqualTo(DocumentColors(followDarkTheme = true))

        viewModel.onFollowDarkThemeChange(false)
        assertThat(repository.settings.value.documentColors.followDarkTheme).isFalse()
    }
}
