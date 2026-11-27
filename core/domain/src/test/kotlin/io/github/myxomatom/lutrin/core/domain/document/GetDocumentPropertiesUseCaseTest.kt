// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.document

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfMetadata
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetDocumentPropertiesUseCaseTest {
    private val uri = "content://com.example.provider/document/1"

    private class FixedInfo(private val info: DocumentFileInfo) : DocumentInfoResolver {
        override suspend fun infoOf(uri: String): DocumentFileInfo = info
    }

    private suspend fun propertiesOf(
        metadata: PdfMetadata,
        info: DocumentFileInfo = DocumentFileInfo("contrat.pdf", 421_888),
    ) = GetDocumentPropertiesUseCase(FixedInfo(info))(uri, metadata)

    @Test
    fun `gathers the document information and the file information`() = runTest {
        val created = Instant.parse("2026-09-12T08:14:00Z")
        val modified = Instant.parse("2026-09-14T14:02:00Z")
        val metadata =
            PdfMetadata(
                pageCount = 8,
                title = "Contrat de location",
                author = "A. Auteur",
                creationDate = created,
                modificationDate = modified,
                format = "PDF 1.7",
            )

        assertThat(propertiesOf(metadata))
            .isEqualTo(
                DocumentProperties(
                    fileName = "contrat.pdf",
                    title = "Contrat de location",
                    author = "A. Auteur",
                    pageCount = 8,
                    sizeBytes = 421_888,
                    pdfVersion = "1.7",
                    createdAt = created,
                    modifiedAt = modified,
                ),
            )
    }

    @Test
    fun `leaves out what nobody knows`() = runTest {
        val properties = propertiesOf(PdfMetadata(pageCount = 1), DocumentFileInfo())

        assertThat(properties)
            .isEqualTo(DocumentProperties(null, null, null, 1, null, null, null, null))
    }

    @Test
    fun `treats blank text and a negative size as unknown`() = runTest {
        val properties =
            propertiesOf(PdfMetadata(pageCount = 2, title = " ", author = ""), DocumentFileInfo("  ", -1))

        assertThat(properties.title).isNull()
        assertThat(properties.author).isNull()
        assertThat(properties.fileName).isNull()
        assertThat(properties.sizeBytes).isNull()
    }

    @Test
    fun `keeps an empty file as a size of zero`() = runTest {
        assertThat(propertiesOf(PdfMetadata(pageCount = 1), DocumentFileInfo("a.pdf", 0)).sizeBytes).isEqualTo(0)
    }

    @Test
    fun `reads the version out of the format of the engine`() {
        fun versionOf(format: String?) = PdfMetadata(pageCount = 1, format = format).pdfVersion()

        assertThat(versionOf("PDF 1.7")).isEqualTo("1.7")
        assertThat(versionOf("PDF 2.0")).isEqualTo("2.0")
        assertThat(versionOf("pdf 1.4")).isEqualTo("1.4")
        assertThat(versionOf("EPUB")).isNull()
        assertThat(versionOf("PDF")).isNull()
        assertThat(versionOf(null)).isNull()
    }
}
