// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Runs under Robolectric on purpose: DataStore replaces its file with `Files.move` only when
 * `Build.VERSION.SDK_INT` is at least 26, and falls back to `File.renameTo` otherwise. On a plain
 * JVM the stubbed SDK_INT is 0, and `renameTo` cannot overwrite a file on Windows, so every
 * second write failed there.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.repository(file: File = File(folder.root, "settings.preferences_pb")) =
        DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file }),
        )

    @Test
    fun `reads the defaults when nothing is stored`() = runTest(UnconfinedTestDispatcher()) {
        val settings = repository().settings.first()

        assertThat(settings).isEqualTo(AppSettings())
        assertThat(settings.dynamicColor).isTrue()
        assertThat(settings.scrollMode).isEqualTo(ScrollMode.CONTINUOUS)
        assertThat(settings.pagePreview).isTrue()
    }

    @Test
    fun `stores the dynamic colour choice and emits every change`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()

        repository.settings.test {
            assertThat(awaitItem().dynamicColor).isTrue()
            repository.setDynamicColor(false)
            assertThat(awaitItem().dynamicColor).isFalse()
            repository.setDynamicColor(true)
            assertThat(awaitItem().dynamicColor).isTrue()
        }
    }

    @Test
    fun `does not emit again when the value does not change`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()

        repository.settings.test {
            assertThat(awaitItem().dynamicColor).isTrue()
            repository.setDynamicColor(true)
            expectNoEvents()
        }
    }

    @Test
    fun `keeps the choice in the file`() = runTest(UnconfinedTestDispatcher()) {
        val file = File(folder.root, "kept.preferences_pb")
        // DataStore allows one active instance per file: close the first one before reopening.
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }))
            .setDynamicColor(false)
        firstScope.cancel()

        assertThat(file.exists()).isTrue()
        assertThat(repository(file).settings.first().dynamicColor).isFalse()
    }

    @Test
    fun `stores the scroll mode and emits every change`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()

        repository.settings.test {
            assertThat(awaitItem().scrollMode).isEqualTo(ScrollMode.CONTINUOUS)
            repository.setScrollMode(ScrollMode.PAGED)
            assertThat(awaitItem()).isEqualTo(AppSettings(dynamicColor = true, scrollMode = ScrollMode.PAGED))
            repository.setScrollMode(ScrollMode.CONTINUOUS)
            assertThat(awaitItem().scrollMode).isEqualTo(ScrollMode.CONTINUOUS)
        }
    }

    @Test
    fun `changing one setting keeps the other`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()

        repository.setScrollMode(ScrollMode.PAGED)
        repository.setDynamicColor(false)

        val expected = AppSettings(dynamicColor = false, scrollMode = ScrollMode.PAGED)
        assertThat(repository.settings.first()).isEqualTo(expected)
    }

    @Test
    fun `keeps the scroll mode in the file`() = runTest(UnconfinedTestDispatcher()) {
        val file = File(folder.root, "scroll.preferences_pb")
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }))
            .setScrollMode(ScrollMode.PAGED)
        firstScope.cancel()

        assertThat(repository(file).settings.first().scrollMode).isEqualTo(ScrollMode.PAGED)
    }

    @Test
    fun `an unknown stored scroll mode reads as the default`() = runTest(UnconfinedTestDispatcher()) {
        val file = File(folder.root, "odd.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file })
        // A value written by a future version, or a damaged file.
        dataStore.edit { it[stringPreferencesKey("scroll_mode")] = "spiral" }

        assertThat(DataStoreSettingsRepository(dataStore).settings.first().scrollMode).isEqualTo(ScrollMode.CONTINUOUS)
    }

    @Test
    fun `stored values of the scroll modes are stable`() {
        // Written on the users' devices: changing them would reset everyone's choice.
        assertThat(ScrollMode.CONTINUOUS.storedValue).isEqualTo("continuous")
        assertThat(ScrollMode.PAGED.storedValue).isEqualTo("paged")
        assertThat(ScrollMode.entries.map { scrollModeOf(it.storedValue) }).isEqualTo(ScrollMode.entries)
        assertThat(scrollModeOf(null)).isEqualTo(ScrollMode.CONTINUOUS)
    }

    @Test
    fun `stores the page preview choice and emits every change`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()

        repository.settings.test {
            assertThat(awaitItem().pagePreview).isTrue()
            repository.setPagePreview(false)
            assertThat(awaitItem()).isEqualTo(AppSettings(pagePreview = false))
            repository.setPagePreview(true)
            assertThat(awaitItem().pagePreview).isTrue()
        }
    }

    @Test
    fun `changing the page preview keeps the other settings`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()

        repository.setScrollMode(ScrollMode.PAGED)
        repository.setDynamicColor(false)
        repository.setPagePreview(false)

        val expected = AppSettings(dynamicColor = false, scrollMode = ScrollMode.PAGED, pagePreview = false)
        assertThat(repository.settings.first()).isEqualTo(expected)
    }

    @Test
    fun `keeps the page preview choice in the file`() = runTest(UnconfinedTestDispatcher()) {
        val file = File(folder.root, "preview.preferences_pb")
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }))
            .setPagePreview(false)
        firstScope.cancel()

        assertThat(repository(file).settings.first().pagePreview).isFalse()
    }

    @Test
    fun `the page preview is stored under a stable key`() = runTest(UnconfinedTestDispatcher()) {
        // Written on the users' devices: renaming the key would turn the preview back on for everyone.
        val file = File(folder.root, "key.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file })
        dataStore.edit { it[booleanPreferencesKey("page_preview")] = false }

        assertThat(DataStoreSettingsRepository(dataStore).settings.first().pagePreview).isFalse()
    }
}
