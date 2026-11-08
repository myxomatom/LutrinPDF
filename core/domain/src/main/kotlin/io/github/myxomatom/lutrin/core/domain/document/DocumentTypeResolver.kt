// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.document

/** Asks the provider of a document which MIME type it serves (the Android `ContentResolver`). */
interface DocumentTypeResolver {
    /**
     * The MIME type the provider reports for [uri].
     *
     * @param uri a `content://` URI.
     * @return the type, or `null` when the provider is unknown, does not say, or refuses to answer.
     *   Never throws: an unknown type is a normal outcome for content sent by another app.
     */
    suspend fun typeOf(uri: String): String?
}
