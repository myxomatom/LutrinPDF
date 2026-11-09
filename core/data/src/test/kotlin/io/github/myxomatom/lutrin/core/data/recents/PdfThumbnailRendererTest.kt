// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.recents

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PageSize
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FAKE_PAGE_COLOR
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Real PNG encoding (native graphics) of the first page drawn by a fake engine. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfThumbnailRendererTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val destination get() = File(folder.root, "thumbnails/doc-1.png")

    private fun TestScope.renderer(engine: FakePdfEngine) =
        PdfThumbnailRenderer(engine, StandardTestDispatcher(testScheduler))

    @Test
    fun `draws the first page of a portrait document into a PNG file`() = runTest {
        val document = FakePdfDocument(listOf(A4, PageSize(842f, 595f)))
        val engine = FakePdfEngine(Result.success(document))

        val written = renderer(engine).render(URI, destination)

        assertThat(written).isTrue()
        val source = engine.openedSources.single() as DocumentSource.FromUri
        assertThat(source.uri.toString()).isEqualTo(URI)
        val render = document.renders.single()
        assertThat(render.pageIndex).isEqualTo(0)
        val bitmap = BitmapFactory.decodeFile(destination.path)
        // A4 fits the 176 x 232 box by its height.
        assertThat(bitmap.height).isEqualTo(232)
        assertThat(bitmap.width).isEqualTo(164)
        assertThat(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)).isEqualTo(FAKE_PAGE_COLOR)
        assertThat(document.isClosed).isTrue()
    }

    @Test
    fun `a landscape page fits the width`() = runTest {
        val engine = FakePdfEngine(Result.success(FakePdfDocument(listOf(PageSize(842f, 595f)))))

        renderer(engine).render(URI, destination)

        val bitmap = BitmapFactory.decodeFile(destination.path)
        assertThat(bitmap.width).isEqualTo(176)
        assertThat(bitmap.height).isEqualTo(124)
    }

    @Test
    fun `replaces an existing file and leaves no temporary file`() = runTest {
        destination.parentFile?.mkdirs()
        destination.writeText("old")

        renderer(FakePdfEngine()).render(URI, destination)

        assertThat(BitmapFactory.decodeFile(destination.path)).isNotNull()
        assertThat(destination.parentFile?.list()?.toList()).containsExactly(destination.name)
    }

    @Test
    fun `a document that cannot be opened writes nothing`() = runTest {
        val engine = FakePdfEngine(Result.failure(PdfError.NotAccessible()))

        assertThat(renderer(engine).render(URI, destination)).isFalse()
        assertThat(destination.exists()).isFalse()
    }

    @Test
    fun `a first page that cannot be drawn writes nothing and closes the document`() = runTest {
        val document = BrokenDocument()

        assertThat(renderer(FakePdfEngine(Result.success(document))).render(URI, destination)).isFalse()
        assertThat(destination.exists()).isFalse()
        assertThat(document.isClosed).isTrue()
    }

    /** A document whose pages are damaged. */
    private class BrokenDocument : PdfDocument by FakePdfDocument() {
        var isClosed = false

        override suspend fun pageSize(index: Int): PageSize = throw PdfError.Unreadable()

        override fun close() {
            isClosed = true
        }
    }

    private companion object {
        const val URI = "content://com.example.files/document/1"
    }
}
