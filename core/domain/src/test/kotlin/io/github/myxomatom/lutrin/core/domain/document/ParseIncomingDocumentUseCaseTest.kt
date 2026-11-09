// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.document

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.document.IncomingDocumentResult.Accepted
import io.github.myxomatom.lutrin.core.domain.document.IncomingDocumentResult.NoDocument
import io.github.myxomatom.lutrin.core.domain.document.IncomingDocumentResult.Rejected
import io.github.myxomatom.lutrin.core.domain.document.IncomingIntent.Companion.ACTION_SEND
import io.github.myxomatom.lutrin.core.domain.document.IncomingIntent.Companion.ACTION_VIEW
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ParseIncomingDocumentUseCaseTest {
    /** Provider types by URI; records every question, since each one is an IPC on a device. */
    private class InMemoryTypes(private val types: Map<String, String?> = emptyMap()) : DocumentTypeResolver {
        val queried = mutableListOf<String>()

        override suspend fun typeOf(uri: String): String? {
            queried += uri
            return types[uri]
        }
    }

    private val pdfUri = "content://com.example.files/document/42"
    private val ownPackage = "io.github.myxomatom.lutrin"

    private fun view(data: String? = pdfUri, type: String? = PDF, page: Int? = null) =
        IncomingIntent(ACTION_VIEW, data = data, type = type, page = page, receiverPackage = ownPackage)

    private fun send(stream: String? = pdfUri, type: String? = PDF, page: Int? = null) =
        IncomingIntent(ACTION_SEND, stream = stream, type = type, page = page, receiverPackage = ownPackage)

    private suspend fun parse(intent: IncomingIntent, types: InMemoryTypes = InMemoryTypes()) =
        ParseIncomingDocumentUseCase(types)(intent)

    // Actions

    @Test
    fun `view with a PDF content URI is accepted at the first page`() = runTest {
        assertThat(parse(view())).isEqualTo(Accepted(pdfUri, pageIndex = null))
    }

    @Test
    fun `send with a PDF stream is accepted`() = runTest {
        assertThat(parse(send())).isEqualTo(Accepted(pdfUri, pageIndex = null))
    }

    @Test
    fun `launcher intent is not a document request`() = runTest {
        val main = IncomingIntent("android.intent.action.MAIN", receiverPackage = ownPackage)

        assertThat(parse(main)).isEqualTo(NoDocument)
    }

    @Test
    fun `intent without action is not a document request`() = runTest {
        assertThat(
            parse(IncomingIntent(action = null, data = pdfUri, receiverPackage = ownPackage)),
        ).isEqualTo(NoDocument)
    }

    @Test
    fun `send multiple is not handled`() = runTest {
        val multiple =
            IncomingIntent("android.intent.action.SEND_MULTIPLE", stream = pdfUri, receiverPackage = ownPackage)

        assertThat(parse(multiple)).isEqualTo(NoDocument)
    }

    @Test
    fun `view without data is rejected`() = runTest {
        assertThat(parse(view(data = null))).isEqualTo(Rejected(IncomingRejection.MISSING_URI))
    }

    @Test
    fun `send without stream is rejected`() = runTest {
        assertThat(parse(send(stream = null))).isEqualTo(Rejected(IncomingRejection.MISSING_URI))
    }

    @Test
    fun `view reads the data URI, not the stream`() = runTest {
        val intent = view(data = null).copy(stream = pdfUri)

        assertThat(parse(intent)).isEqualTo(Rejected(IncomingRejection.MISSING_URI))
    }

    // URIs

    @Test
    fun `file URI is rejected`() = runTest {
        assertThat(
            parse(view(data = "file:///sdcard/Download/a.pdf")),
        ).isEqualTo(Rejected(IncomingRejection.UNSUPPORTED_SCHEME))
    }

    @Test
    fun `web URIs are rejected`() = runTest {
        listOf("http://example.org/a.pdf", "https://example.org/a.pdf").forEach { uri ->
            assertThat(parse(view(data = uri))).isEqualTo(Rejected(IncomingRejection.UNSUPPORTED_SCHEME))
        }
    }

    @Test
    fun `other schemes are rejected`() = runTest {
        listOf(
            "android.resource://com.example/raw/a",
            "data:application/pdf;base64,JVBERi0=",
            "intent:#Intent;end",
        ).forEach { uri ->
            assertThat(parse(view(data = uri))).isEqualTo(Rejected(IncomingRejection.UNSUPPORTED_SCHEME))
        }
    }

    @Test
    fun `content scheme is case insensitive`() = runTest {
        assertThat(parse(view(data = "CONTENT://com.example.files/1"))).isInstanceOf(Accepted::class.java)
    }

    @Test
    fun `malformed URIs are rejected`() = runTest {
        val malformed =
            listOf(
                "",
                "   ",
                "no scheme at all",
                "/sdcard/a.pdf",
                "1content://com.example/1",
                "content:com.example/1",
                "content:///document/1",
                "content://",
                "content://com.example/a\u0000b.pdf",
                "content://com.example/a\nb.pdf",
            )
        malformed.forEach { uri ->
            assertThat(parse(view(data = uri))).isEqualTo(Rejected(IncomingRejection.MALFORMED_URI))
        }
    }

    @Test
    fun `URIs of the app's own providers are rejected`() = runTest {
        listOf(
            "content://$ownPackage/files/settings",
            "content://$ownPackage.fileprovider/cache/a.pdf",
            "content://0@$ownPackage.androidx-startup/x",
        ).forEach { uri ->
            assertThat(parse(view(data = uri))).isEqualTo(Rejected(IncomingRejection.APP_OWN_URI))
        }
    }

    @Test
    fun `an authority merely starting with the package name is not the app's`() = runTest {
        assertThat(parse(view(data = "content://${ownPackage}reader.files/1"))).isInstanceOf(Accepted::class.java)
    }

    @Test
    fun `URI of another user profile is accepted`() = runTest {
        assertThat(parse(view(data = "content://10@com.example.files/1"))).isInstanceOf(Accepted::class.java)
    }

    @Test
    fun `URI is kept exactly as sent`() = runTest {
        val uri = "content://com.android.providers.downloads.documents/document/msf%3A1000"

        assertThat(parse(view(data = uri))).isEqualTo(Accepted(uri))
    }

    // MIME types

    @Test
    fun `declared PDF type does not query the provider`() = runTest {
        val types = InMemoryTypes()

        parse(view(type = "application/pdf"), types)

        assertThat(types.queried).isEmpty()
    }

    @Test
    fun `PDF type variants are accepted`() = runTest {
        listOf("application/x-pdf", "APPLICATION/PDF", "application/pdf; charset=binary").forEach { type ->
            assertThat(parse(view(type = type))).isInstanceOf(Accepted::class.java)
        }
    }

    @Test
    fun `missing declared type falls back to the provider type`() = runTest {
        val types = InMemoryTypes(mapOf(pdfUri to PDF))

        assertThat(parse(view(type = null), types)).isInstanceOf(Accepted::class.java)
        assertThat(types.queried).containsExactly(pdfUri)
    }

    @Test
    fun `unknown type everywhere is left to the PDF header check`() = runTest {
        assertThat(parse(view(type = null))).isInstanceOf(Accepted::class.java)
        assertThat(parse(send(type = ""))).isInstanceOf(Accepted::class.java)
    }

    @Test
    fun `generic types are left to the PDF header check`() = runTest {
        listOf("application/octet-stream", "binary/octet-stream", "*/*", "application/*").forEach { type ->
            assertThat(parse(send(type = type))).isInstanceOf(Accepted::class.java)
        }
    }

    @Test
    fun `generic declared type with a PDF provider type is accepted`() = runTest {
        val types = InMemoryTypes(mapOf(pdfUri to PDF))

        assertThat(parse(send(type = "application/octet-stream"), types)).isInstanceOf(Accepted::class.java)
    }

    @Test
    fun `generic declared type with another provider type is rejected`() = runTest {
        val types = InMemoryTypes(mapOf(pdfUri to "image/png"))

        assertThat(
            parse(send(type = "application/octet-stream"), types),
        ).isEqualTo(Rejected(IncomingRejection.UNSUPPORTED_TYPE))
    }

    @Test
    fun `other declared type with an unknown provider type is rejected`() = runTest {
        listOf("image/jpeg", "text/plain", "application/vnd.oasis.opendocument.text").forEach { type ->
            assertThat(parse(send(type = type))).isEqualTo(Rejected(IncomingRejection.UNSUPPORTED_TYPE))
        }
    }

    @Test
    fun `other declared type is accepted when the provider says PDF`() = runTest {
        val types = InMemoryTypes(mapOf(pdfUri to PDF))

        assertThat(parse(view(type = "text/plain"), types)).isInstanceOf(Accepted::class.java)
    }

    @Test
    fun `the provider is not queried for a rejected URI`() = runTest {
        val types = InMemoryTypes()

        parse(view(data = "file:///a.pdf", type = null), types)

        assertThat(types.queried).isEmpty()
    }

    // Page extra

    @Test
    fun `page extra is one based`() = runTest {
        assertThat(parse(view(page = 1))).isEqualTo(Accepted(pdfUri, pageIndex = 0))
        assertThat(parse(view(page = 12))).isEqualTo(Accepted(pdfUri, pageIndex = 11))
    }

    @Test
    fun `zero or negative page is ignored, as if no page was given`() = runTest {
        // No explicit page: the reader resumes the saved page, or opens the first one (ADR 0015).
        listOf(0, -1, Int.MIN_VALUE).forEach { page ->
            assertThat(parse(view(page = page))).isEqualTo(Accepted(pdfUri, pageIndex = null))
        }
    }

    @Test
    fun `page beyond any document is passed on for the reader to clamp`() = runTest {
        assertThat(parse(view(page = Int.MAX_VALUE))).isEqualTo(Accepted(pdfUri, pageIndex = Int.MAX_VALUE - 1))
    }

    @Test
    fun `page extra is ignored when sharing`() = runTest {
        assertThat(parse(send(page = 5))).isEqualTo(Accepted(pdfUri, pageIndex = null))
    }

    private companion object {
        const val PDF = "application/pdf"
    }
}
