// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.render

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageRotation
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.RenderOptions
import io.github.myxomatom.lutrin.core.domain.settings.DocumentColorMode
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.testing.FAKE_PAGE_COLOR
import io.github.myxomatom.lutrin.core.testing.FAKE_PAGE_COLOR_NIGHT
import io.github.myxomatom.lutrin.core.testing.FAKE_PAGE_COLOR_SEPIA
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
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
        val bitmap = renderer().renderPage(pageIndex = 1, widthPx = 595).bitmapOrNull

        assertThat(bitmap?.width).isEqualTo(595)
        assertThat(bitmap?.height).isEqualTo(842)
        assertThat(bitmap?.getPixel(10, 10)).isEqualTo(FAKE_PAGE_COLOR)
        assertThat(document.renders.single().region).isEqualTo(PdfRect(0f, 0f, 595f, 842f))
        assertThat(document.renders.single().scale).isEqualTo(1f)
    }

    @Test
    fun `serves a repeated base rendering from the cache`() = runTest {
        val renderer = renderer()

        val first = renderer.renderPage(0, 300).bitmapOrNull
        val second = renderer.renderPage(0, 300).bitmapOrNull

        assertThat(second).isSameInstanceAs(first)
        assertThat(document.renders).hasSize(1)
    }

    @Test
    fun `passes the render options down to the engine, for pages, parts and previews`() = runTest {
        val renderer = renderer()

        renderer.renderPage(0, 300, NIGHT)
        renderer.renderPart(0, pageWidthPx = 1190, part = PixelRect(0, 0, 595, 842), options = SEPIA)
        renderer.renderPreview(1, 128, NIGHT)

        assertThat(document.renders.map { it.options }).containsExactly(NIGHT, SEPIA, NIGHT).inOrder()
        assertThat(renderer.renderPage(1, 300, SEPIA).bitmapOrNull?.getPixel(5, 5)).isEqualTo(FAKE_PAGE_COLOR_SEPIA)
    }

    @Test
    fun `a rendering with other options is drawn again, never taken from the cache`() = runTest {
        val renderer = renderer()

        val normal = renderer.renderPage(0, 300).bitmapOrNull
        val night = renderer.renderPage(0, 300, NIGHT).bitmapOrNull

        assertThat(night).isNotSameInstanceAs(normal)
        assertThat(night?.getPixel(5, 5)).isEqualTo(FAKE_PAGE_COLOR_NIGHT)
        assertThat(document.renders).hasSize(2)
        assertThat(renderer.renderPage(0, 300, NIGHT).bitmapOrNull).isSameInstanceAs(night)
    }

    @Test
    fun `a change of options empties both caches, so that no bitmap of the old colours stays in memory`() = runTest {
        val pages = BitmapCache(maxBytes = 64L * 1024 * 1024)
        val previews = BitmapCache(maxBytes = 64L * 1024 * 1024)
        val renderer =
            PageRenderer(document, document.pageSizes, StandardTestDispatcher(testScheduler), pages, previews)
        renderer.renderPage(0, 300)
        renderer.renderPage(1, 300)
        renderer.renderPreview(0, 128)

        renderer.renderPage(0, 300, NIGHT)

        assertThat(pages.sizeBytes).isEqualTo(300 * pageHeightPx(document.pageSizes[0], 300) * 4)
        assertThat(previews.sizeBytes).isEqualTo(0)
        assertThat(renderer.cachedPreview(0, 128)).isNull()
        assertThat(renderer.cachedPreview(0, 128, NIGHT)).isNull()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class) // runCurrent
    fun `a rendering that ends after a change of options is shown but not cached`() = runTest {
        val gated = GatedDocument(document)
        val pages = BitmapCache(maxBytes = 64L * 1024 * 1024)
        val renderer = PageRenderer(gated, document.pageSizes, StandardTestDispatcher(testScheduler), pages)
        val late = async { renderer.renderPage(0, 300) }
        runCurrent()
        assertThat(gated.waiting).isTrue()

        val night = renderer.renderPage(1, 300, NIGHT).bitmapOrNull
        gated.gate.complete(Unit)
        val stale = late.await().bitmapOrNull

        assertThat(stale).isNotNull()
        assertThat(pages.sizeBytes).isEqualTo(night?.allocationByteCount)
    }

    @Test
    fun `renders a visible part at full sharpness`() = runTest {
        // Page displayed 1190 px wide (scale 2), part = the bottom-right quarter.
        val bitmap = renderer().renderPart(
            0,
            pageWidthPx = 1190,
            part = PixelRect(595, 842, 1190, 1684),
        ).bitmapOrNull

        assertThat(bitmap?.width).isEqualTo(595)
        assertThat(bitmap?.height).isEqualTo(842)
        with(document.renders.single()) {
            assertThat(region).isEqualTo(PdfRect(297.5f, 421f, 595f, 842f))
            assertThat(scale).isEqualTo(2f)
        }
    }

    @Test
    fun `a page turned a quarter is rendered with its sides swapped, the rotation down to the engine`() = runTest {
        val bitmap = renderer().renderPage(pageIndex = 1, widthPx = 842, options = QUARTER).bitmapOrNull

        assertThat(bitmap?.width).isEqualTo(842)
        assertThat(bitmap?.height).isEqualTo(595)
        with(document.renders.single()) {
            assertThat(region).isEqualTo(PdfRect(0f, 0f, 842f, 595f))
            assertThat(scale).isEqualTo(1f)
            assertThat(options.rotation).isEqualTo(PageRotation.QUARTER)
        }
    }

    @Test
    fun `a page upside down keeps its sides`() = runTest {
        val bitmap = renderer().renderPage(0, 595, RenderOptions(rotation = PageRotation.HALF)).bitmapOrNull

        assertThat(bitmap?.height).isEqualTo(842)
        assertThat(document.renders.single().region).isEqualTo(PdfRect(0f, 0f, 595f, 842f))
    }

    @Test
    fun `a part of a turned page is a region of the turned page`() = runTest {
        // Turned page displayed 1684 px wide (scale 2): its bottom-right quarter.
        renderer().renderPart(0, pageWidthPx = 1684, part = PixelRect(842, 595, 1684, 1190), options = QUARTER)

        with(document.renders.single()) {
            assertThat(region).isEqualTo(PdfRect(421f, 297.5f, 842f, 595f))
            assertThat(scale).isEqualTo(2f)
        }
    }

    @Test
    fun `turning the pages empties the caches and renders them again`() = runTest {
        val renderer = renderer()
        val straight = renderer.renderPage(0, 300).bitmapOrNull
        renderer.renderPreview(0, 128)

        val turned = renderer.renderPage(0, 300, QUARTER).bitmapOrNull

        assertThat(turned).isNotSameInstanceAs(straight)
        assertThat(turned?.height).isEqualTo(212)
        assertThat(renderer.cachedPreview(0, 128)).isNull()
        assertThat(renderer.renderPage(0, 300, QUARTER).bitmapOrNull).isSameInstanceAs(turned)
    }

    @Test
    fun `renderPage returns no image for an unreadable page and caches nothing`() = runTest {
        document.unreadablePages = setOf(1)
        val cache = BitmapCache(maxBytes = 64L * 1024 * 1024)

        val result = renderer(cache).renderPage(1, 300)

        assertThat(result).isEqualTo(RenderResult.Unreadable)
        assertThat(cache.sizeBytes).isEqualTo(0)
    }

    @Test
    fun `renderPart returns no image for an unreadable page`() = runTest {
        document.unreadablePages = setOf(0)

        val result = renderer().renderPart(0, pageWidthPx = 1190, part = PixelRect(0, 0, 595, 842))

        assertThat(result).isEqualTo(RenderResult.Unreadable)
    }

    @Test
    fun `renderPreview returns no image for an unreadable page and caches nothing`() = runTest {
        document.unreadablePages = setOf(2)
        val renderer = renderer()

        val result = renderer.renderPreview(2, 128)

        assertThat(result).isEqualTo(RenderResult.Unreadable)
        assertThat(renderer.cachedPreview(2, 128)).isNull()
    }

    @Test
    fun `bitmap cache evicts the least recently used rendering`() {
        val bytesPerBitmap = 100 * 100 * 4L
        val cache = BitmapCache(maxBytes = 2 * bytesPerBitmap)
        val keys = (0..2).map { RenderKey(it, 100, RenderOptions.ORIGINAL) }

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

    @Test
    fun `renders a preview at its width, with the height of the page`() = runTest {
        val renderer = renderer()

        val preview = renderer.renderPreview(pageIndex = 2, widthPx = 128).bitmapOrNull

        assertThat(preview?.width).isEqualTo(128)
        assertThat(preview?.height).isEqualTo(181)
        assertThat(preview?.getPixel(5, 5)).isEqualTo(FAKE_PAGE_COLOR)
        with(document.renders.single()) {
            assertThat(pageIndex).isEqualTo(2)
            assertThat(region).isEqualTo(PdfRect(0f, 0f, 595f, 842f))
        }
    }

    @Test
    fun `serves a repeated preview from its own cache, apart from the pages`() = runTest {
        val pages = BitmapCache(maxBytes = 64L * 1024 * 1024)
        val previews = BitmapCache(maxBytes = 64L * 1024 * 1024)
        val renderer =
            PageRenderer(document, document.pageSizes, StandardTestDispatcher(testScheduler), pages, previews)
        assertThat(renderer.cachedPreview(0, 128)).isNull()

        val first = renderer.renderPreview(0, 128).bitmapOrNull
        val second = renderer.renderPreview(0, 128).bitmapOrNull

        assertThat(second).isSameInstanceAs(first)
        assertThat(renderer.cachedPreview(0, 128)).isSameInstanceAs(first)
        assertThat(document.renders).hasSize(1)
        assertThat(pages.sizeBytes).isEqualTo(0)
        assertThat(previews.sizeBytes).isEqualTo(128 * 181 * 4)
        // A page rendered at the same width is not taken for its preview, nor the other way round.
        assertThat(renderer.renderPage(0, 128).bitmapOrNull).isNotSameInstanceAs(first)
        assertThat(document.renders).hasSize(2)
    }

    @Test
    fun `the preview cache stays within its bounds`() = runTest {
        val bytesPerPreview = 128 * 181 * 4L
        val previews = BitmapCache(maxBytes = 2 * bytesPerPreview)
        val renderer =
            PageRenderer(document, document.pageSizes, StandardTestDispatcher(testScheduler), previewCache = previews)

        (0..2).forEach { renderer.renderPreview(it, 128) }

        assertThat(previews.sizeBytes).isEqualTo(2 * bytesPerPreview)
        assertThat(renderer.cachedPreview(0, 128)).isNull()
        assertThat(renderer.cachedPreview(2, 128)).isNotNull()
    }

    /** Holds the rendering of the original colours until [gate] completes; other options render at once. */
    private class GatedDocument(private val document: FakePdfDocument) : PdfDocument by document {
        val gate = CompletableDeferred<Unit>()
        var waiting = false

        override suspend fun renderPage(
            index: Int,
            target: Bitmap,
            region: PdfRect,
            scale: Float,
            options: RenderOptions,
        ) {
            if (options == RenderOptions.ORIGINAL) {
                waiting = true
                gate.await()
            }
            document.renderPage(index, target, region, scale, options)
        }
    }

    private companion object {
        val NIGHT = RenderOptions(DocumentColorMode.NIGHT)
        val SEPIA = RenderOptions(DocumentColorMode.SEPIA)
        val QUARTER = RenderOptions(rotation = PageRotation.QUARTER)
    }
}
