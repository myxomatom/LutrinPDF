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

        override suspend fun setPagePreview(enabled: Boolean) {
            (settings as MutableStateFlow).value = settings.value.copy(pagePreview = enabled)
        }

        override suspend fun setIgnoreDocumentRestrictions(enabled: Boolean) {
            (settings as MutableStateFlow).value = settings.value.copy(ignoreDocumentRestrictions = enabled)
        }

        override suspend fun setAppTheme(theme: AppTheme) {
            (settings as MutableStateFlow).value = settings.value.copy(appTheme = theme)
        }

        override suspend fun setDocumentColorMode(mode: DocumentColorMode?) {
            val colors = settings.value.documentColors.copy(chosen = mode)
            (settings as MutableStateFlow).value = settings.value.copy(documentColors = colors)
        }

        override suspend fun setFollowDarkTheme(enabled: Boolean) {
            val colors = settings.value.documentColors.copy(followDarkTheme = enabled)
            (settings as MutableStateFlow).value = settings.value.copy(documentColors = colors)
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

    @Test
    fun `the page preview of the slider is on by default`() {
        assertThat(AppSettings().pagePreview).isTrue()
        assertThat(AppSettings.DEFAULT_PAGE_PREVIEW).isTrue()
    }

    @Test
    fun `the page preview can be turned off and on again without touching the other settings`() = runTest {
        val setPagePreview = SetPagePreviewUseCase(repository)
        SetScrollModeUseCase(repository)(ScrollMode.PAGED)

        setPagePreview(false)
        assertThat(repository.settings.value)
            .isEqualTo(AppSettings(dynamicColor = true, scrollMode = ScrollMode.PAGED, pagePreview = false))

        setPagePreview(true)
        assertThat(repository.settings.value.pagePreview).isTrue()
        assertThat(repository.settings.value.scrollMode).isEqualTo(ScrollMode.PAGED)
    }

    @Test
    fun `the copy restriction of documents is respected by default`() {
        assertThat(AppSettings().ignoreDocumentRestrictions).isFalse()
    }

    @Test
    fun `the copy restriction can be ignored and respected again`() = runTest {
        val setIgnoreDocumentRestrictions = SetIgnoreDocumentRestrictionsUseCase(repository)

        setIgnoreDocumentRestrictions(true)
        assertThat(repository.settings.value).isEqualTo(AppSettings(ignoreDocumentRestrictions = true))

        setIgnoreDocumentRestrictions(false)
        assertThat(repository.settings.value.ignoreDocumentRestrictions).isFalse()
    }

    @Test
    fun `text copy is allowed by the document or by the setting`() {
        val respecting = AppSettings(ignoreDocumentRestrictions = false)
        val ignoring = AppSettings(ignoreDocumentRestrictions = true)

        assertThat(respecting.allowsTextCopy(documentAllowsCopy = true)).isTrue()
        assertThat(respecting.allowsTextCopy(documentAllowsCopy = false)).isFalse()
        assertThat(ignoring.allowsTextCopy(documentAllowsCopy = true)).isTrue()
        assertThat(ignoring.allowsTextCopy(documentAllowsCopy = false)).isTrue()
    }

    @Test
    fun `printing is allowed by the document or by the same setting`() {
        val respecting = AppSettings(ignoreDocumentRestrictions = false)
        val ignoring = AppSettings(ignoreDocumentRestrictions = true)

        assertThat(respecting.allowsPrinting(documentAllowsPrinting = true)).isTrue()
        assertThat(respecting.allowsPrinting(documentAllowsPrinting = false)).isFalse()
        assertThat(ignoring.allowsPrinting(documentAllowsPrinting = true)).isTrue()
        assertThat(ignoring.allowsPrinting(documentAllowsPrinting = false)).isTrue()
    }

    @Test
    fun `the app theme can be forced light or dark and follow the system again`() = runTest {
        val setAppTheme = SetAppThemeUseCase(repository)

        setAppTheme(AppTheme.DARK)
        assertThat(repository.settings.value).isEqualTo(AppSettings(appTheme = AppTheme.DARK))

        setAppTheme(AppTheme.LIGHT)
        assertThat(repository.settings.value.appTheme).isEqualTo(AppTheme.LIGHT)

        setAppTheme(AppTheme.SYSTEM)
        assertThat(repository.settings.value).isEqualTo(AppSettings())
    }

    @Test
    fun `the colours chosen in the reader are kept as an explicit choice`() = runTest {
        val setDocumentColorMode = SetDocumentColorModeUseCase(repository)

        setDocumentColorMode(DocumentColorMode.SEPIA)
        assertThat(repository.settings.value.documentColors).isEqualTo(DocumentColors(chosen = DocumentColorMode.SEPIA))

        // Normal chosen explicitly is a choice too: the dark theme no longer changes it.
        setDocumentColorMode(DocumentColorMode.NORMAL)
        assertThat(repository.settings.value.documentColors.chosen).isEqualTo(DocumentColorMode.NORMAL)
    }

    @Test
    fun `turning on the dark theme follow forgets the colours chosen so that it applies at once`() = runTest {
        SetDocumentColorModeUseCase(repository)(DocumentColorMode.SEPIA)
        val setFollowDarkTheme = SetFollowDarkThemeUseCase(repository)

        setFollowDarkTheme(true)

        assertThat(repository.settings.value.documentColors).isEqualTo(DocumentColors(followDarkTheme = true))
        val mode = repository.settings.value.documentColors.modeFor(darkTheme = true)
        assertThat(mode).isEqualTo(DocumentColorMode.NIGHT)
    }

    @Test
    fun `turning off the dark theme follow keeps the colours chosen`() = runTest {
        val setFollowDarkTheme = SetFollowDarkThemeUseCase(repository)
        setFollowDarkTheme(true)
        SetDocumentColorModeUseCase(repository)(DocumentColorMode.NIGHT)

        setFollowDarkTheme(false)

        assertThat(repository.settings.value.documentColors).isEqualTo(DocumentColors(chosen = DocumentColorMode.NIGHT))
    }
}
