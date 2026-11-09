// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.document

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.data.recents.DocumentAccess
import io.github.myxomatom.lutrin.core.data.recents.DocumentDetails
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ContentResolverDocumentNameResolverTest {
    /** Answers [details] for every URI; never keeps nor releases anything. */
    private class FixedAccess(private val details: DocumentDetails) : DocumentAccess {
        override suspend fun details(uri: String): DocumentDetails = details

        override suspend fun keep(uri: String): Boolean = false

        override suspend fun release(uri: String) = Unit
    }

    private suspend fun nameFrom(displayName: String?): String? =
        ContentResolverDocumentNameResolver(FixedAccess(DocumentDetails(displayName))).nameOf("content://a/1")

    @Test
    fun `gives the provider's name without its PDF extension`() = runTest {
        assertThat(nameFrom("Relevé de compte.PDF")).isEqualTo("Relevé de compte")
        assertThat(nameFrom("notes.txt")).isEqualTo("notes.txt")
    }

    @Test
    fun `gives no name when the provider has none`() = runTest {
        assertThat(nameFrom(null)).isNull()
        assertThat(nameFrom(".pdf")).isNull()
        assertThat(nameFrom("  ")).isNull()
    }
}
