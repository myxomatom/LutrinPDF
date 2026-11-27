// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.document

import io.github.myxomatom.lutrin.core.data.recents.DocumentAccess
import io.github.myxomatom.lutrin.core.domain.document.DocumentFileInfo
import io.github.myxomatom.lutrin.core.domain.document.DocumentInfoResolver
import javax.inject.Inject

/**
 * [DocumentInfoResolver] asking the provider for `OpenableColumns.DISPLAY_NAME` and
 * `OpenableColumns.SIZE`, through the same [DocumentAccess] as the recent list and the password
 * dialog (ADR 0027). The file is never opened.
 *
 * @param access the provider's description of documents.
 */
internal class ContentResolverDocumentInfoResolver @Inject constructor(private val access: DocumentAccess) :
    DocumentInfoResolver {
    override suspend fun infoOf(uri: String): DocumentFileInfo {
        val details = access.details(uri)
        return DocumentFileInfo(fileName = details.displayName, sizeBytes = details.sizeBytes)
    }
}
