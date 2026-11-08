// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakeDocumentTypeResolverTest {
    @Test
    fun `reports the configured type and records the question`() = runTest {
        val resolver = FakeDocumentTypeResolver(mapOf("content://a/1" to "application/pdf"))

        assertThat(resolver.typeOf("content://a/1")).isEqualTo("application/pdf")
        assertThat(resolver.typeOf("content://a/2")).isNull()
        assertThat(resolver.queried).containsExactly("content://a/1", "content://a/2").inOrder()
    }
}
