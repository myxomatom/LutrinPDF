// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import io.github.myxomatom.lutrin.core.domain.document.DocumentFileInfo
import io.github.myxomatom.lutrin.core.domain.document.DocumentInfoResolver

/**
 * In-memory [DocumentInfoResolver] for tests: the file information of a few URIs.
 *
 * @property infos information reported for each URI; any other URI is unknown to its provider.
 */
class FakeDocumentInfoResolver(var infos: Map<String, DocumentFileInfo> = emptyMap()) : DocumentInfoResolver {
    /** Returns the entry of [infos] for [uri], or empty information. */
    override suspend fun infoOf(uri: String): DocumentFileInfo = infos[uri] ?: DocumentFileInfo()
}
