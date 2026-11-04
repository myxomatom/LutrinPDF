// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.artifex.mupdf.fitz.Context
import com.artifex.mupdf.fitz.Document
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfMetadata
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.pdf.PdfEngine
import java.io.Closeable
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.withContext

private const val PDF_MAGIC = "application/pdf"

/** Opens the file descriptor of a content URI; abstracted so that the engine is testable. */
fun interface ContentOpener {
    /**
     * Opens [uri] for reading.
     *
     * @throws java.io.FileNotFoundException if the document no longer exists.
     * @throws SecurityException if the read permission was revoked.
     */
    fun open(uri: Uri): ParcelFileDescriptor
}

/** [PdfEngine] backed by MuPDF (ADR 0001). */
internal class MuPdfEngine @Inject constructor(
    private val contentOpener: ContentOpener,
    private val dispatchers: DocumentDispatcherFactory,
) : PdfEngine {
    init {
        // MuPDF warnings may quote document data: never write them to the system log.
        Context.setLog(SilentLog)
    }

    override suspend fun open(source: DocumentSource, password: String?): Result<PdfDocument> {
        val dispatcher = dispatchers.create()
        return try {
            Result.success(withContext(dispatcher) { openOnDocumentThread(source, password, dispatcher) })
        } catch (error: CancellationException) {
            dispatcher.close()
            throw error
        } catch (error: PdfError) {
            dispatcher.close()
            Result.failure(error)
        }
    }

    private fun openOnDocumentThread(
        source: DocumentSource,
        password: String?,
        dispatcher: ExecutorCoroutineDispatcher,
    ): PdfDocument {
        val (document, resources) = load(source)
        return try {
            val metadata = translatingMuPdfErrors { prepare(document, password) }
            MuPdfDocument(document, dispatcher, metadata, resources)
        } catch (error: PdfError) {
            document.destroy()
            resources?.close()
            throw error
        }
    }

    /** Unlocks the document, turns JavaScript off and reads its metadata. */
    private fun prepare(document: Document, password: String?): PdfMetadata {
        unlock(document, password)
        document.asPDF()?.disableJs() // PDF JavaScript is never run (AGENTS.md §9).
        val pageCount = document.countPages()
        if (pageCount < 1) throw PdfError.Unreadable()
        return document.readMetadata(pageCount)
    }

    private fun load(source: DocumentSource): Pair<Document, Closeable?> = when (source) {
        is DocumentSource.FromFile -> {
            if (!source.file.canRead()) throw PdfError.NotAccessible()
            checkHeader(readSafely { source.file.inputStream().use { it.readUpTo(PDF_HEADER_SEARCH_LENGTH) } })
            translatingMuPdfErrors { Document.openDocument(source.file.absolutePath) } to null
        }

        is DocumentSource.FromBytes -> {
            checkHeader(source.bytes)
            translatingMuPdfErrors { Document.openDocument(source.bytes, PDF_MAGIC) } to null
        }

        is DocumentSource.FromUri -> loadUri(source.uri)
    }

    private fun loadUri(uri: Uri): Pair<Document, Closeable?> {
        val descriptor = readSafely { contentOpener.open(uri) }
        try {
            val channel = FileInputStream(descriptor.fileDescriptor).channel
            val start = ByteBuffer.allocate(PDF_HEADER_SEARCH_LENGTH)
            readSafely { channel.read(start, 0) }
            checkHeader(start.array().copyOf(start.position()))
            val document =
                translatingMuPdfErrors { Document.openDocument(ChannelSeekableInputStream(channel), PDF_MAGIC) }
            return document to descriptor
        } catch (error: PdfError) {
            descriptor.close()
            throw error
        }
    }

    private fun checkHeader(start: ByteArray) {
        if (!hasPdfHeader(start)) throw PdfError.UnsupportedFormat()
    }

    private fun unlock(document: Document, password: String?) {
        if (!document.needsPassword()) return
        if (password == null) throw PdfError.PasswordRequired()
        if (!document.authenticatePassword(password)) throw PdfError.IncorrectPassword()
    }

    /** Turns I/O and permission failures into [PdfError.NotAccessible]. */
    private inline fun <T> readSafely(block: () -> T): T = try {
        block()
    } catch (error: IOException) {
        throw PdfError.NotAccessible(error)
    } catch (error: SecurityException) {
        throw PdfError.NotAccessible(error)
    }
}

/** Discards MuPDF's diagnostic output (`AGENTS.md` §1.8). */
private object SilentLog : Context.Log {
    override fun error(message: String?) = Unit

    override fun warning(message: String?) = Unit
}
