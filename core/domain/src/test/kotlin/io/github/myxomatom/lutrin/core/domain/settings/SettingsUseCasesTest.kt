// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.settings

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SettingsUseCasesTest {
    private class InMemorySettings : SettingsRepository {
        override val settings: StateFlow<AppSettings> = MutableStateFlow(AppSettings())

        override suspend fun setDynamicColor(enabled: Boolean) {
            (settings as MutableStateFlow).value = settings.value.copy(dynamicColor = enabled)
        }
    }

    private val repository = InMemorySettings()

    @Test
    fun `dynamic colours are on by default`() {
        assertThat(AppSettings().dynamicColor).isTrue()
    }

    @Test
    fun `observing returns the stored settings`() = runTest {
        assertThat(ObserveSettingsUseCase(repository)().first()).isEqualTo(AppSettings(dynamicColor = true))
    }

    @Test
    fun `dynamic colours can be turned off and on again`() = runTest {
        val setDynamicColor = SetDynamicColorUseCase(repository)

        setDynamicColor(false)
        assertThat(repository.settings.value.dynamicColor).isFalse()

        setDynamicColor(true)
        assertThat(repository.settings.value.dynamicColor).isTrue()
    }
}
