// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Content provider of the instrumented test APK: serves the test PDF files of `core:testing`
 * (`TestFiles.Pdf`) at `content://lutrin.androidtest.documents/<asset path>`, as a file manager
 * would serve a document picked with « Ouvrir avec » or « Partager ».
 */
class TestDocumentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = if (uri.path.orEmpty().endsWith(".pdf")) PDF_MIME_TYPE else null

    /** A real file descriptor, seekable like the ones of the system providers. */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = checkNotNull(context)
        val asset = uri.path?.removePrefix("/") ?: throw FileNotFoundException("No path")
        val file = File(context.cacheDir, asset.substringAfterLast('/'))
        if (!file.exists()) {
            context.assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        0

    /** Builds the URIs of the served files. */
    companion object {
        /** Authority declared in `src/androidTest/AndroidManifest.xml`. */
        const val AUTHORITY = "lutrin.androidtest.documents"

        private const val PDF_MIME_TYPE = "application/pdf"

        /** URI of a test asset, such as `TestFiles.Pdf.SIMPLE`. */
        fun uriOf(asset: String): Uri = Uri.parse("content://$AUTHORITY/$asset")
    }
}
