// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.testing.TestFiles
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real native library on a device: every PDF of the test set (`SPEC.md` §9), content URIs
 * and rendering into an Android [Bitmap].
 */
@RunWith(AndroidJUnit4::class)
class MuPdfEngineInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext

    private val engine =
        MuPdfEngine(
            contentOpener = { uri ->
                targetContext.contentResolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException()
            },
            dispatchers = SingleThreadDocumentDispatcherFactory(),
        )

    /** Copies a test asset (packaged in the test APK) into the app cache. */
    private fun copyAsset(path: String): File = File(targetContext.cacheDir, path.substringAfterLast('/')).apply {
        instrumentation.context.assets.open(path).use { input -> outputStream().use { input.copyTo(it) } }
    }

    @Test
    fun opensEveryValidPdf() = runTest {
        val expected =
            mapOf(
                TestFiles.Pdf.SIMPLE to 1,
                TestFiles.Pdf.LARGE to 600,
                TestFiles.Pdf.NESTED_OUTLINE to 6,
                TestFiles.Pdf.LINKS to 4,
                TestFiles.Pdf.FORM to 1,
                TestFiles.Pdf.SCANNED to 2,
                TestFiles.Pdf.MIXED_PAGE_SIZES to 5,
                TestFiles.Pdf.NON_LATIN to 1,
                TestFiles.Pdf.CORRUPTED_TRUNCATED to 1,
            )
        expected.forEach { (path, pages) ->
            engine.open(DocumentSource.FromFile(copyAsset(path))).getOrThrow().use {
                assertThat(it.pageCount).isEqualTo(pages)
            }
        }
    }

    @Test
    fun reportsInvalidFilesWithoutCrashing() = runTest {
        suspend fun errorOf(path: String) = engine.open(DocumentSource.FromFile(copyAsset(path)))

        assertThat(
            errorOf(TestFiles.Pdf.CORRUPTED_GARBAGE).exceptionOrNull(),
        ).isInstanceOf(PdfError.Unreadable::class.java)
        assertThat(
            errorOf(TestFiles.Pdf.NOT_A_PDF).exceptionOrNull(),
        ).isInstanceOf(PdfError.UnsupportedFormat::class.java)
        assertThat(
            errorOf(TestFiles.Pdf.ENCRYPTED).exceptionOrNull(),
        ).isInstanceOf(PdfError.PasswordRequired::class.java)
    }

    @Test
    fun opensAnEncryptedPdfThroughAUri() = runTest {
        val uri = Uri.fromFile(copyAsset(TestFiles.Pdf.ENCRYPTED))

        engine.open(DocumentSource.FromUri(uri), TestFiles.Pdf.ENCRYPTED_PASSWORD).getOrThrow().use { document ->
            assertThat(document.metadata.isEncrypted).isTrue()
            assertThat(document.search("chiffré").toList()).isNotEmpty()
        }
    }

    @Test
    fun reportsAMissingUriAsNotAccessible() = runTest {
        val uri = Uri.fromFile(File(targetContext.cacheDir, "absent.pdf"))

        assertThat(
            engine.open(DocumentSource.FromUri(uri)).exceptionOrNull(),
        ).isInstanceOf(PdfError.NotAccessible::class.java)
    }

    @Test
    fun rendersIntoABitmap() = runTest {
        engine.open(DocumentSource.FromFile(copyAsset(TestFiles.Pdf.SIMPLE))).getOrThrow().use { document ->
            val bitmap = Bitmap.createBitmap(298, 421, Bitmap.Config.ARGB_8888)
            document.renderPage(0, bitmap, PdfRect(0f, 0f, 595f, 842f), scale = 0.5f, nightMode = false)

            assertThat(bitmap.getPixel(290, 415)).isEqualTo(Color.WHITE)
            val pixels = IntArray(bitmap.width * bitmap.height).also {
                bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            }
            assertThat(pixels.any { Color.red(it) < DARK }).isTrue()
            bitmap.recycle()
        }
    }

    private companion object {
        const val DARK = 0x60
    }
}
