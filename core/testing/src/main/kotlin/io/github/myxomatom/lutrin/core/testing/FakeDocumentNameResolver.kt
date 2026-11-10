// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import io.github.myxomatom.lutrin.core.domain.document.DocumentNameResolver

/**
 * In-memory [DocumentNameResolver] for tests: the provider names of a few URIs.
 *
 * @property names name reported for each URI; any other URI has no known name.
 */
class FakeDocumentNameResolver(var names: Map<String, String> = emptyMap()) : DocumentNameResolver {
    /** Returns the entry of [names] for [uri], or `null`. */
    override suspend fun nameOf(uri: String): String? = names[uri]
}
