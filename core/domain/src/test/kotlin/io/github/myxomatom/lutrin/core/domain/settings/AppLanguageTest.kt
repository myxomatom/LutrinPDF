// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.settings

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppLanguageTest {
    private class FakeController(override val isSupported: Boolean, var language: AppLanguage = AppLanguage.SYSTEM) :
        AppLanguageController {
        override suspend fun current(): AppLanguage = language

        override suspend fun set(language: AppLanguage) {
            this.language = language
        }
    }

    @Test
    fun `no tag is the language of the system`() {
        assertThat(AppLanguage.fromLanguageTags(emptyList())).isEqualTo(AppLanguage.SYSTEM)
    }

    @Test
    fun `a tag of the app is its language, whatever the region`() {
        assertThat(AppLanguage.fromLanguageTags(listOf("fr"))).isEqualTo(AppLanguage.FRENCH)
        assertThat(AppLanguage.fromLanguageTags(listOf("fr-CA"))).isEqualTo(AppLanguage.FRENCH)
        assertThat(AppLanguage.fromLanguageTags(listOf("en_GB"))).isEqualTo(AppLanguage.ENGLISH)
        assertThat(AppLanguage.fromLanguageTags(listOf("EN-us"))).isEqualTo(AppLanguage.ENGLISH)
    }

    @Test
    fun `a language the app does not have is the language of the system`() {
        assertThat(AppLanguage.fromLanguageTags(listOf("de-DE", "fr"))).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.fromLanguageTags(listOf(""))).isEqualTo(AppLanguage.SYSTEM)
    }

    @Test
    fun `the tags of the languages are stable`() {
        assertThat(AppLanguage.entries.map { it.languageTag }).containsExactly(null, "fr", "en").inOrder()
    }

    @Test
    fun `the language is read when the system lets the app choose it`() = runTest {
        val controller = FakeController(isSupported = true, language = AppLanguage.FRENCH)

        assertThat(GetAppLanguageUseCase(controller)()).isEqualTo(AppLanguage.FRENCH)
    }

    @Test
    fun `there is no language to read when the system does not let the app choose it`() = runTest {
        val controller = FakeController(isSupported = false, language = AppLanguage.FRENCH)

        assertThat(GetAppLanguageUseCase(controller)()).isNull()
    }

    @Test
    fun `choosing a language hands it to the system`() = runTest {
        val controller = FakeController(isSupported = true)

        SetAppLanguageUseCase(controller)(AppLanguage.ENGLISH)

        assertThat(controller.language).isEqualTo(AppLanguage.ENGLISH)
    }
}
