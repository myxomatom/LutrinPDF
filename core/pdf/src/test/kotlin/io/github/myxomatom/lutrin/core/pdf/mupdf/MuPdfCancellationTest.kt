// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MuPdfCancellationTest {
    @Test
    fun `a cancelled rendering leaves the document usable`() = runBlocking<Unit> {
        openTestPdf("pdfs/scanned_images_only.pdf").use { document ->
            // A large rendering, cancelled right away.
            val rendering =
                async(Dispatchers.Default) {
                    document.renderPixels(0, RenderSpec(wholePage, 8f, 4760, 6736, nightMode = false))
                }
            rendering.cancelAndJoin()

            assertThat(
                runCatching {
                    rendering.await()
                }.exceptionOrNull(),
            ).isInstanceOf(CancellationException::class.java)
            assertThat(document.pageSize(1).width).isGreaterThan(0f)
        }
    }
}
