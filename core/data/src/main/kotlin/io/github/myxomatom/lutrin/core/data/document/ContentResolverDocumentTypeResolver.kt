// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.document

import android.content.ContentResolver
import androidx.core.net.toUri
import io.github.myxomatom.lutrin.core.domain.dispatchers.IoDispatcher
import io.github.myxomatom.lutrin.core.domain.document.DocumentTypeResolver
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [DocumentTypeResolver] backed by [ContentResolver.getType], an IPC to the document's
 * provider, run on the injected I/O dispatcher.
 *
 * @param contentResolver the app's content resolver.
 * @param ioDispatcher where the provider is queried.
 */
internal class ContentResolverDocumentTypeResolver @Inject constructor(
    private val contentResolver: ContentResolver,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DocumentTypeResolver {
    override suspend fun typeOf(uri: String): String? = withContext(ioDispatcher) {
        try {
            contentResolver.getType(uri.toUri())
        } catch (expected: SecurityException) {
            // A provider may refuse to describe a document it did not grant: the type is unknown,
            // and the PDF header decides (ADR 0014).
            null
        } catch (expected: IllegalArgumentException) {
            // Thrown by providers for URIs they do not recognise: unknown type as well.
            null
        }
    }
}
