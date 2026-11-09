// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.recents

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.PNG
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import dagger.Lazy
import io.github.myxomatom.lutrin.core.domain.dispatchers.IoDispatcher
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.pdf.PdfEngine
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The directory of the thumbnails of recent documents (ADR 0015): under `noBackupFilesDir`,
 * private and never backed up; one PNG file per entry, named after its identifier and the
 * date it was drawn, so that a new drawing has a new name and the library shows it at once.
 *
 * @property directory where the files are; created on the first write.
 */
internal class ThumbnailStore(val directory: File) {
    /** The file called [name] in [directory]. */
    fun file(name: String): File = File(directory, name)

    /**
     * Name of a new thumbnail file.
     *
     * @param documentId the entry.
     * @param drawnAtMillis when it is drawn, in milliseconds since the epoch.
     */
    fun newName(documentId: String, drawnAtMillis: Long): String = "$documentId-$drawnAtMillis$EXTENSION"

    /** Deletes the file called [name], if it exists. */
    fun delete(name: String) {
        file(name).delete()
    }

    private companion object {
        const val EXTENSION = ".png"
    }
}

/** Draws the first page of a document into a picture file. */
internal fun interface ThumbnailRenderer {
    /**
     * @param uri the document.
     * @param destination file to (re)write; left untouched on failure or cancellation.
     * @return whether the file was written; `false` when the document cannot be opened or drawn.
     */
    suspend fun render(uri: String, destination: File): Boolean
}

/**
 * [ThumbnailRenderer] going through [PdfEngine], the only way to reach MuPDF (ADR 0001, 0015).
 * The page fits in [MAX_WIDTH_PX] × [MAX_HEIGHT_PX] pixels: 44 × 58 dp of the E1 mock-up at
 * the densest screens (xxxhdpi, × 4). The PNG is written to a temporary file next to the
 * destination, then moved over it, so that no partial file is ever visible.
 *
 * @param engine opens the document, independently of the reader. Lazy: the library must not load
 *   the MuPDF native library at start-up, before a thumbnail is actually drawn.
 * @param ioDispatcher where the bitmap is allocated, encoded and written.
 */
internal class PdfThumbnailRenderer @Inject constructor(
    private val engine: Lazy<PdfEngine>,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ThumbnailRenderer {
    override suspend fun render(uri: String, destination: File): Boolean {
        val document = engine.get().open(DocumentSource.FromUri(uri.toUri())).getOrElse { return false }
        return document.use { draw(it, destination) }
    }

    private suspend fun draw(document: PdfDocument, destination: File): Boolean {
        val bitmap =
            try {
                firstPage(document)
            } catch (expected: PdfError) {
                // A damaged first page: no thumbnail, the library shows its placeholder.
                return false
            }
        return withContext(ioDispatcher) { write(bitmap, destination) }
    }

    private suspend fun firstPage(document: PdfDocument): Bitmap {
        val page = document.pageSize(0)
        val scale = min(MAX_WIDTH_PX / page.width, MAX_HEIGHT_PX / page.height)
        val bitmap =
            withContext(ioDispatcher) {
                createBitmap(pixels(page.width * scale), pixels(page.height * scale)).apply { eraseColor(Color.WHITE) }
            }
        document.renderPage(0, bitmap, PdfRect(0f, 0f, page.width, page.height), scale, nightMode = false)
        return bitmap
    }

    @Suppress("SwallowedException") // A failed write is an expected outcome: no thumbnail.
    private fun write(bitmap: Bitmap, destination: File): Boolean {
        val directory = destination.absoluteFile.parentFile ?: return false
        val temporary = File(directory, destination.name + TEMPORARY_SUFFIX)
        return try {
            Files.createDirectories(directory.toPath())
            val encoded = FileOutputStream(temporary).use { bitmap.compress(PNG, PNG_QUALITY, it) }
            if (encoded) Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            encoded
        } catch (error: IOException) {
            false
        } finally {
            temporary.delete()
        }
    }

    private fun pixels(size: Float): Int = max(1, size.roundToInt())

    /** Size limits of a thumbnail. */
    companion object {
        /** Largest width of a thumbnail, in pixels. */
        const val MAX_WIDTH_PX: Float = 176f

        /** Largest height of a thumbnail, in pixels. */
        const val MAX_HEIGHT_PX: Float = 232f

        /** Ignored by PNG, which is lossless; required by [Bitmap.compress]. */
        private const val PNG_QUALITY = 100

        private const val TEMPORARY_SUFFIX = ".tmp"
    }
}
