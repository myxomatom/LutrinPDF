// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakeSettingsRepositoryTest {
    @Test
    fun `starts with the given settings`() {
        val initial = AppSettings(dynamicColor = false, scrollMode = ScrollMode.PAGED)

        assertThat(FakeSettingsRepository(initial).settings.value).isEqualTo(initial)
        assertThat(FakeSettingsRepository().settings.value).isEqualTo(AppSettings())
    }

    @Test
    fun `each setter changes only its own setting`() = runTest {
        val repository = FakeSettingsRepository()

        repository.setScrollMode(ScrollMode.PAGED)
        assertThat(repository.settings.value).isEqualTo(AppSettings(dynamicColor = true, scrollMode = ScrollMode.PAGED))

        repository.setDynamicColor(false)
        assertThat(
            repository.settings.value,
        ).isEqualTo(AppSettings(dynamicColor = false, scrollMode = ScrollMode.PAGED))
    }

    @Test
    fun `the page preview setter changes only the page preview`() = runTest {
        val repository = FakeSettingsRepository(AppSettings(dynamicColor = false, scrollMode = ScrollMode.PAGED))

        repository.setPagePreview(false)

        assertThat(repository.settings.value)
            .isEqualTo(AppSettings(dynamicColor = false, scrollMode = ScrollMode.PAGED, pagePreview = false))
    }
}
