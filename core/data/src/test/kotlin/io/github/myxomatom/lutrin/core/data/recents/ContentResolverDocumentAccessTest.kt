// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.recents

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
class ContentResolverDocumentAccessTest {
    /** Describes documents by their last path segment: "named", "nameless", "empty", "denied", "unknown". */
    class DetailsProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = when (uri.lastPathSegment) {
            "named" -> cursor(arrayOf("Contrat de location.pdf", 2_048L))
            "nameless" -> cursor(arrayOf(null, null))
            "empty" -> MatrixCursor(COLUMNS)
            "denied" -> throw SecurityException("No grant")
            "unknown" -> throw IllegalArgumentException("Unknown URI")
            else -> null
        }

        private fun cursor(row: Array<Any?>) = MatrixCursor(COLUMNS).apply { addRow(row) }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        private companion object {
            val COLUMNS = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        }
    }

    private val contentResolver = RuntimeEnvironment.getApplication().contentResolver

    private fun TestScope.access(): ContentResolverDocumentAccess {
        Robolectric.buildContentProvider(DetailsProvider::class.java).create(AUTHORITY)
        return ContentResolverDocumentAccess(contentResolver, StandardTestDispatcher(testScheduler))
    }

    private fun uri(segment: String) = "content://$AUTHORITY/document/$segment"

    @Test
    fun `reads the name and size given by the provider`() = runTest {
        assertThat(access().details(uri("named"))).isEqualTo(DocumentDetails("Contrat de location.pdf", 2_048))
    }

    @Test
    fun `missing values, rows or providers give empty details`() = runTest {
        val access = access()

        listOf(uri("nameless"), uri("empty"), uri("null"), "content://com.example.missing/document/1").forEach {
            assertThat(access.details(it)).isEqualTo(DocumentDetails())
        }
    }

    @Test
    fun `a provider that refuses to answer gives empty details`() = runTest {
        val access = access()

        assertThat(access.details(uri("denied"))).isEqualTo(DocumentDetails())
        assertThat(access.details(uri("unknown"))).isEqualTo(DocumentDetails())
    }

    @Test
    fun `keeps and gives back the persisted read permission`() = runTest {
        val access = access()

        assertThat(access.keep(uri("named"))).isTrue()
        assertThat(contentResolver.persistedUriPermissions.map { it.uri.toString() }).containsExactly(uri("named"))

        access.release(uri("named"))
        assertThat(contentResolver.persistedUriPermissions).isEmpty()
    }

    @Test
    fun `releasing a permission that was never persisted does nothing`() = runTest {
        access().release(uri("named"))

        assertThat(contentResolver.persistedUriPermissions).isEmpty()
    }

    @Test
    fun `a temporary grant is not persisted and nothing is thrown`() = runTest {
        val access = ContentResolverDocumentAccess(RefusingResolver(), StandardTestDispatcher(testScheduler))

        assertThat(access.keep(uri("named"))).isFalse()
        access.release(uri("named"))
    }

    /** Answers like the system for a grant without `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`. */
    private class RefusingResolver : ContentResolver(RuntimeEnvironment.getApplication()) {
        override fun takePersistableUriPermission(uri: Uri, modeFlags: Int): Unit =
            throw SecurityException("No persistable permission grants found")

        override fun releasePersistableUriPermission(uri: Uri, modeFlags: Int): Unit =
            throw SecurityException("No permission grants found")
    }

    private companion object {
        const val AUTHORITY = "com.example.files"
    }
}
