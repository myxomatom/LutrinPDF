// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.document

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
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
class ContentResolverDocumentTypeResolverTest {
    /** Answers `getType` by the last path segment: "pdf", "denied" or "unknown". */
    class TypesProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String? = when (uri.lastPathSegment) {
            "pdf" -> "application/pdf"
            "denied" -> throw SecurityException("No grant")
            "unknown" -> throw IllegalArgumentException("Unknown URI")
            else -> null
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

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private val dispatcher = StandardTestDispatcher()

    private fun TestScope.resolver(): ContentResolverDocumentTypeResolver {
        Robolectric.buildContentProvider(TypesProvider::class.java).create(AUTHORITY)
        return ContentResolverDocumentTypeResolver(
            RuntimeEnvironment.getApplication().contentResolver,
            StandardTestDispatcher(testScheduler),
        )
    }

    @Test
    fun `returns the type the provider reports`() = runTest(dispatcher) {
        assertThat(resolver().typeOf("content://$AUTHORITY/document/pdf")).isEqualTo("application/pdf")
    }

    @Test
    fun `returns null when the provider gives no type`() = runTest(dispatcher) {
        assertThat(resolver().typeOf("content://$AUTHORITY/document/other")).isNull()
    }

    @Test
    fun `returns null when no provider serves the authority`() = runTest(dispatcher) {
        assertThat(resolver().typeOf("content://com.example.missing/document/pdf")).isNull()
    }

    @Test
    fun `returns null when the provider refuses to answer`() = runTest(dispatcher) {
        assertThat(resolver().typeOf("content://$AUTHORITY/document/denied")).isNull()
    }

    @Test
    fun `returns null when the provider does not recognise the URI`() = runTest(dispatcher) {
        assertThat(resolver().typeOf("content://$AUTHORITY/document/unknown")).isNull()
    }

    private companion object {
        const val AUTHORITY = "com.example.documents"
    }
}
