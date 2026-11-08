// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import io.github.myxomatom.lutrin.core.domain.document.DocumentTypeResolver
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory [DocumentTypeResolver] for tests: the provider types of a few URIs.
 *
 * @property types MIME type reported for each URI; any other URI has no known type.
 */
class FakeDocumentTypeResolver(var types: Map<String, String?> = emptyMap()) : DocumentTypeResolver {
    /** Every URI asked, in order (each one is an IPC on a device). */
    val queried: MutableList<String> = CopyOnWriteArrayList()

    /** Records [uri] in [queried] and returns its entry of [types], or `null`. */
    override suspend fun typeOf(uri: String): String? {
        queried += uri
        return types[uri]
    }
}
