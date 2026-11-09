// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.recents

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.core.net.toUri
import io.github.myxomatom.lutrin.core.domain.dispatchers.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * What the provider of a document says about it.
 *
 * @property displayName file name (`OpenableColumns.DISPLAY_NAME`), or `null`.
 * @property sizeBytes file size (`OpenableColumns.SIZE`), or `null`.
 */
internal data class DocumentDetails(val displayName: String? = null, val sizeBytes: Long? = null)

/** The platform side of the recent list: the provider's description and the read permissions. */
internal interface DocumentAccess {
    /**
     * Asks the provider for the name and size of the document. Never throws: a provider that
     * refuses or does not know gives empty details.
     */
    suspend fun details(uri: String): DocumentDetails

    /**
     * Keeps the read permission on [uri] across restarts, when the grant allows it.
     *
     * @return `true` when the permission is persisted; `false` when the grant is temporary
     *   (most « Ouvrir avec » and « Partager »), or the URI was not granted at all.
     */
    suspend fun keep(uri: String): Boolean

    /** Gives back the persisted read permission on [uri]; nothing happens if there is none. */
    suspend fun release(uri: String)
}

/**
 * [DocumentAccess] backed by the [ContentResolver]: `query` for the details, and the persisted
 * URI permissions of the Storage Access Framework, on the injected I/O dispatcher (these are IPC).
 *
 * @param contentResolver the app's content resolver.
 * @param ioDispatcher where the providers and the system are called.
 */
internal class ContentResolverDocumentAccess @Inject constructor(
    private val contentResolver: ContentResolver,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DocumentAccess {
    override suspend fun details(uri: String): DocumentDetails = withContext(ioDispatcher) {
        try {
            contentResolver.query(uri.toUri(), DETAILS_COLUMNS, null, null, null)?.use(::readDetails)
                ?: DocumentDetails()
        } catch (expected: SecurityException) {
            // The grant is gone or never covered the query: the reader's name is used instead.
            DocumentDetails()
        } catch (expected: IllegalArgumentException) {
            // Providers throw it for URIs or columns they do not know.
            DocumentDetails()
        }
    }

    override suspend fun keep(uri: String): Boolean = withContext(ioDispatcher) {
        try {
            contentResolver.takePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (expected: SecurityException) {
            // Temporary grant (no FLAG_GRANT_PERSISTABLE_URI_PERMISSION): the entry is kept anyway
            // and the document may be inaccessible after a restart (ADR 0015).
            false
        }
    }

    override suspend fun release(uri: String) {
        withContext(ioDispatcher) {
            try {
                contentResolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (expected: SecurityException) {
                // No persisted permission to give back: the entry never had one.
            }
        }
    }

    private fun readDetails(cursor: Cursor): DocumentDetails {
        if (!cursor.moveToFirst()) return DocumentDetails()
        val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val size = cursor.getColumnIndex(OpenableColumns.SIZE)
        return DocumentDetails(
            displayName = name.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString),
            sizeBytes = size.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong),
        )
    }

    private companion object {
        val DETAILS_COLUMNS = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    }
}
