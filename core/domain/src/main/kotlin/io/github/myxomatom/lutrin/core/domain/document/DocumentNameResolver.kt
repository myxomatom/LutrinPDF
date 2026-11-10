// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.document

import io.github.myxomatom.lutrin.core.domain.recents.RecentDocumentsRepository
import javax.inject.Inject

/** Asks the provider of a document for its file name (the Android `ContentResolver`). */
interface DocumentNameResolver {
    /**
     * The name the provider gives the document at [uri], without its `.pdf` extension.
     *
     * @param uri a `content://` URI.
     * @return the name, or `null` when the provider is unknown, gives none, or refuses to answer.
     *   Never throws.
     */
    suspend fun nameOf(uri: String): String?
}

/**
 * The name to show for a document before it is open, for example in the password dialog (V4,
 * ADR 0017): its name in the recent list, otherwise the name its provider gives.
 *
 * @param recents the recent list.
 * @param names the providers of documents.
 */
class GetDocumentNameUseCase @Inject constructor(
    private val recents: RecentDocumentsRepository,
    private val names: DocumentNameResolver,
) {
    /**
     * @param uri the document.
     * @return its name, or `null` when neither the list nor the provider knows one.
     */
    suspend operator fun invoke(uri: String): String? =
        recents.findByUri(uri)?.displayName ?: names.nameOf(uri)?.takeIf { it.isNotBlank() }
}
