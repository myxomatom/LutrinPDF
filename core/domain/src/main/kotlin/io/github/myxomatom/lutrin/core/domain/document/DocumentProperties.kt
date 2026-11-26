// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.document

import io.github.myxomatom.lutrin.core.domain.pdf.PdfMetadata
import java.time.Instant
import javax.inject.Inject

/**
 * What the provider of a document says about its file (the Android `ContentResolver`).
 *
 * @property fileName the file name as the provider gives it, extension included; `null` when
 *   unknown.
 * @property sizeBytes the size of the file in bytes; `null` when unknown.
 */
data class DocumentFileInfo(val fileName: String? = null, val sizeBytes: Long? = null)

/** Asks the provider of a document for what it knows about its file. */
interface DocumentInfoResolver {
    /**
     * The name and size the provider gives the document at [uri].
     *
     * @param uri a `content://` URI.
     * @return what the provider answered; empty ([DocumentFileInfo] without values) when the
     *   provider is unknown, refuses to answer or does not know. Never throws.
     */
    suspend fun infoOf(uri: String): DocumentFileInfo
}

/**
 * The properties of a document shown by the « Propriétés » sheet (F16, V5, ADR 0027). Every value
 * the document or its provider does not give is `null`: the screen says it is not filled in.
 *
 * @property fileName file name, extension included.
 * @property title title of the document information, not the file name.
 * @property author author of the document information.
 * @property pageCount number of pages, at least 1.
 * @property sizeBytes size of the file in bytes.
 * @property pdfVersion version of the PDF format, such as `1.7`.
 * @property createdAt creation date of the document.
 * @property modifiedAt date of its last modification.
 */
data class DocumentProperties(
    val fileName: String?,
    val title: String?,
    val author: String?,
    val pageCount: Int,
    val sizeBytes: Long?,
    val pdfVersion: String?,
    val createdAt: Instant?,
    val modifiedAt: Instant?,
)

/** Version in a format such as `PDF 1.7`, as MuPDF reports it. */
private val PDF_FORMAT = Regex("^\\s*PDF[ -]?(\\d+\\.\\d+)", RegexOption.IGNORE_CASE)

/**
 * Version of the PDF format out of the [PdfMetadata.format] the engine reports (`PDF 1.7`).
 *
 * @return the version (`1.7`), or `null` when the format is unknown or is not a PDF version.
 */
internal fun PdfMetadata.pdfVersion(): String? = format?.let { PDF_FORMAT.find(it) }?.groupValues?.get(1)

/**
 * Gathers the properties of an open document (F16): what the engine read in the document
 * ([PdfMetadata]) and the name and size of the file, which only its provider knows.
 *
 * @param infos the providers of documents.
 */
class GetDocumentPropertiesUseCase @Inject constructor(private val infos: DocumentInfoResolver) {
    /**
     * @param uri the document.
     * @param metadata what the engine read in it.
     * @return its properties; a value nobody knows is `null`.
     */
    suspend operator fun invoke(uri: String, metadata: PdfMetadata): DocumentProperties {
        val file = infos.infoOf(uri)
        return DocumentProperties(
            fileName = file.fileName?.takeIf { it.isNotBlank() },
            title = metadata.title?.takeIf { it.isNotBlank() },
            author = metadata.author?.takeIf { it.isNotBlank() },
            pageCount = metadata.pageCount,
            sizeBytes = file.sizeBytes?.takeIf { it >= 0 },
            pdfVersion = metadata.pdfVersion(),
            createdAt = metadata.creationDate,
            modifiedAt = metadata.modificationDate,
        )
    }
}
