// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.render

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.testing.FAKE_PAGE_COLOR
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageRendererTest {
    private val document = FakePdfDocument()

    private fun TestScope.renderer(cache: BitmapCache = BitmapCache(maxBytes = 64L * 1024 * 1024)) =
        PageRenderer(document, document.pageSizes, StandardTestDispatcher(testScheduler), cache)

    @Test
    fun `renders a whole page at the requested width`() = runTest {
        val bitmap = renderer().renderPage(pageIndex = 1, widthPx = 595, nightMode = false)

        assertThat(bitmap?.width).isEqualTo(595)
        assertThat(bitmap?.height).isEqualTo(842)
        assertThat(bitmap?.getPixel(10, 10)).isEqualTo(FAKE_PAGE_COLOR)
        assertThat(document.renders.single().region).isEqualTo(PdfRect(0f, 0f, 595f, 842f))
        assertThat(document.renders.single().scale).isEqualTo(1f)
    }

    @Test
    fun `serves a repeated base rendering from the cache`() = runTest {
        val renderer = renderer()

        val first = renderer.renderPage(0, 300, nightMode = false)
        val second = renderer.renderPage(0, 300, nightMode = false)
        renderer.renderPage(0, 300, nightMode = true)

        assertThat(second).isSameInstanceAs(first)
        assertThat(document.renders).hasSize(2)
    }

    @Test
    fun `renders a visible part at full sharpness`() = runTest {
        // Page displayed 1190 px wide (scale 2), part = the bottom-right quarter.
        val bitmap = renderer().renderPart(
            0,
            pageWidthPx = 1190,
            part = PixelRect(595, 842, 1190, 1684),
            nightMode = false,
        )

        assertThat(bitmap?.width).isEqualTo(595)
        assertThat(bitmap?.height).isEqualTo(842)
        with(document.renders.single()) {
            assertThat(region).isEqualTo(PdfRect(297.5f, 421f, 595f, 842f))
            assertThat(scale).isEqualTo(2f)
        }
    }

    @Test
    fun `bitmap cache evicts the least recently used rendering`() {
        val bytesPerBitmap = 100 * 100 * 4L
        val cache = BitmapCache(maxBytes = 2 * bytesPerBitmap)
        val keys = (0..2).map { RenderKey(it, 100, nightMode = false) }

        keys.forEach { cache[it] = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888) }

        assertThat(cache[keys[0]]).isNull()
        assertThat(cache[keys[2]]).isNotNull()
        assertThat(cache.sizeBytes).isEqualTo(2 * bytesPerBitmap)
        cache.clear()
        assertThat(cache.sizeBytes).isEqualTo(0)
    }

    @Test
    fun `default cache size is a quarter of the heap, capped`() {
        assertThat(BitmapCache.defaultMaxBytes(256L * 1024 * 1024)).isEqualTo(64L * 1024 * 1024)
        assertThat(BitmapCache.defaultMaxBytes(4096L * 1024 * 1024)).isEqualTo(128L * 1024 * 1024)
    }
}
