// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.document

import io.github.myxomatom.lutrin.core.data.recents.DocumentAccess
import io.github.myxomatom.lutrin.core.data.recents.withoutPdfExtension
import io.github.myxomatom.lutrin.core.domain.document.DocumentNameResolver
import javax.inject.Inject

/**
 * [DocumentNameResolver] asking the provider for `OpenableColumns.DISPLAY_NAME`, through the
 * same [DocumentAccess] as the recent list, so that a document has the same name in the password
 * dialog (V4) and, once open, in the library (ADR 0015, 0017).
 *
 * @param access the provider's description of documents.
 */
internal class ContentResolverDocumentNameResolver @Inject constructor(private val access: DocumentAccess) :
    DocumentNameResolver {
    override suspend fun nameOf(uri: String): String? =
        access.details(uri).displayName?.withoutPdfExtension()?.takeIf { it.isNotBlank() }
}
