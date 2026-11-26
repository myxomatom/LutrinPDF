// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.document

import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.data.recents.DocumentAccess
import io.github.myxomatom.lutrin.core.data.recents.DocumentDetails
import io.github.myxomatom.lutrin.core.domain.document.DocumentFileInfo
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ContentResolverDocumentInfoResolverTest {
    /** Answers [details] for every URI; never keeps nor releases anything. */
    private class FixedAccess(private val details: DocumentDetails) : DocumentAccess {
        override suspend fun details(uri: String): DocumentDetails = details

        override suspend fun keep(uri: String): Boolean = false

        override suspend fun release(uri: String) = Unit
    }

    private suspend fun infoFrom(details: DocumentDetails) =
        ContentResolverDocumentInfoResolver(FixedAccess(details)).infoOf("content://a/1")

    @Test
    fun `gives the name of the provider as it is, extension included, and the size`() = runTest {
        assertThat(infoFrom(DocumentDetails("Relevé de compte.PDF", 412_000)))
            .isEqualTo(DocumentFileInfo("Relevé de compte.PDF", 412_000))
    }

    @Test
    fun `gives empty information when the provider knows nothing`() = runTest {
        assertThat(infoFrom(DocumentDetails())).isEqualTo(DocumentFileInfo())
    }
}
