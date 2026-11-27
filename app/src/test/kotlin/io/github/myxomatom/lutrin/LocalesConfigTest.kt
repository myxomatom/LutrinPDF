// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.settings.AppLanguage
import java.io.File
import org.junit.Test

/**
 * `locales_config.xml` (ADR 0029) lists the languages of the per-app language settings of Android
 * 13 and later: it must be the ones the app offers in its own settings, no more, no less.
 */
class LocalesConfigTest {
    private val config = File("src/main/res/xml/locales_config.xml").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    private val listed: List<String> =
        Regex("""<locale\s+android:name="([^"]+)"""").findAll(config).map { it.groupValues[1] }.toList()

    @Test
    fun `lists every language of the app, and only them`() {
        val offered = AppLanguage.entries.mapNotNull { it.languageTag }

        assertThat(listed).containsExactlyElementsIn(offered)
    }

    @Test
    fun `English, the default language, is listed`() {
        assertThat(listed).contains("en")
    }

    @Test
    fun `every listed language other than English has its translations`() {
        listed.filter { it != "en" }.forEach { language ->
            assertThat(File("src/main/res/values-$language").exists()).isTrue()
        }
    }

    @Test
    fun `the manifest points to it`() {
        assertThat(manifest).contains("android:localeConfig=\"@xml/locales_config\"")
    }
}
