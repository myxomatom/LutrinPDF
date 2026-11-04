// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
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
}
