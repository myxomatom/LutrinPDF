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

        override suspend fun setScrollMode(mode: ScrollMode) {
            (settings as MutableStateFlow).value = settings.value.copy(scrollMode = mode)
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

    @Test
    fun `pages scroll continuously by default`() {
        assertThat(AppSettings().scrollMode).isEqualTo(ScrollMode.CONTINUOUS)
        assertThat(AppSettings.DEFAULT_SCROLL_MODE).isEqualTo(ScrollMode.CONTINUOUS)
    }

    @Test
    fun `scroll mode can be switched to page by page and back`() = runTest {
        val setScrollMode = SetScrollModeUseCase(repository)

        setScrollMode(ScrollMode.PAGED)
        assertThat(repository.settings.value.scrollMode).isEqualTo(ScrollMode.PAGED)
        assertThat(repository.settings.value.dynamicColor).isTrue()

        setScrollMode(ScrollMode.CONTINUOUS)
        assertThat(repository.settings.value.scrollMode).isEqualTo(ScrollMode.CONTINUOUS)
    }
}
