// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.links

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CheckExternalLinkUseCaseTest {
    private val check = CheckExternalLinkUseCase()

    private fun allowed(uri: String): String? = (check(uri) as? ExternalLinkCheck.Allowed)?.uri

    @Test
    fun `accepts http, https and mailto links unchanged`() {
        assertThat(allowed("http://example.org")).isEqualTo("http://example.org")
        assertThat(allowed("https://example.org/conditions?x=1#a")).isEqualTo("https://example.org/conditions?x=1#a")
        assertThat(allowed("mailto:contact@example.org")).isEqualTo("mailto:contact@example.org")
    }

    @Test
    fun `accepts a scheme in capitals and writes it in lower case`() {
        assertThat(allowed("HTTPS://Example.org/Page")).isEqualTo("https://Example.org/Page")
        assertThat(allowed("MailTo:someone@example.org")).isEqualTo("mailto:someone@example.org")
    }

    @Test
    fun `trims the spaces around the link`() {
        assertThat(allowed("  https://example.org \n")).isEqualTo("https://example.org")
    }

    @Test
    fun `refuses dangerous or local schemes`() {
        listOf(
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            " javascript:alert(1)",
            "file:///sdcard/secret.pdf",
            "content://com.example.provider/document/1",
            "intent://scan/#Intent;scheme=zxing;end",
            "data:text/html,<b>hi</b>",
            "tel:+33123456789",
            "ftp://example.org/file",
            "market://details?id=com.example",
        ).forEach { assertThat(check(it)).isEqualTo(ExternalLinkCheck.Refused) }
    }

    @Test
    fun `refuses relative links`() {
        listOf("chapitre2.pdf", "../autre.pdf", "/page", "#section", "//example.org/path")
            .forEach { assertThat(check(it)).isEqualTo(ExternalLinkCheck.Refused) }
    }

    @Test
    fun `refuses empty or blank links`() {
        assertThat(check("")).isEqualTo(ExternalLinkCheck.Refused)
        assertThat(check("   ")).isEqualTo(ExternalLinkCheck.Refused)
    }

    @Test
    fun `refuses malformed links`() {
        listOf(
            "https:",
            "https://",
            "https:///path",
            "http:example.org",
            "mailto:",
            "https://exa mple.org",
            "https://example.org/\u0000",
            ":https://example.org",
            "1http://example.org",
            "ht tp://example.org",
            // A right-to-left override would display "https://example.org/exe.pdf".
            "https://example.org/\u202Efdp.exe",
            "https://example.org/\u200Bpage",
        ).forEach { assertThat(check(it)).isEqualTo(ExternalLinkCheck.Refused) }
    }

    @Test
    fun `refuses a link longer than the limit`() {
        val long = "https://example.org/" + "a".repeat(MAX_EXTERNAL_LINK_LENGTH)

        assertThat(check(long)).isEqualTo(ExternalLinkCheck.Refused)
    }

    @Test
    fun `accepts a link right at the limit`() {
        val prefix = "https://example.org/"
        val uri = prefix + "a".repeat(MAX_EXTERNAL_LINK_LENGTH - prefix.length)

        assertThat(allowed(uri)).isEqualTo(uri)
    }

    @Test
    fun `accepts a host with user information and a port`() {
        assertThat(allowed("https://user@example.org:8443/x")).isEqualTo("https://user@example.org:8443/x")
    }
}
