// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SearchOptions
import io.github.myxomatom.lutrin.core.domain.pdf.SelectionMode
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.pdf.PdfDocument
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
            releaseFailures = LogReleaseFailureReporter(),
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
                TestFiles.Pdf.TEXT_SELECTION to 1,
                TestFiles.Pdf.MANY_HITS to 1,
                TestFiles.Pdf.ROTATED_DESTINATIONS to 3,
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

    /** Encrypted test documents → their user password (F10, ADR 0017). */
    private val userPasswords =
        mapOf(
            TestFiles.Pdf.ENCRYPTED_RC4 to TestFiles.Pdf.ENCRYPTED_RC4_PASSWORD,
            TestFiles.Pdf.ENCRYPTED_AES_128 to TestFiles.Pdf.ENCRYPTED_AES_128_PASSWORD,
            TestFiles.Pdf.ENCRYPTED to TestFiles.Pdf.ENCRYPTED_PASSWORD,
        )

    @Test
    fun asksForThePasswordOfEveryEncryptionMethod() = runTest {
        userPasswords.keys.forEach { path ->
            val uri = Uri.fromFile(copyAsset(path))

            assertThat(
                engine.open(DocumentSource.FromUri(uri)).exceptionOrNull(),
            ).isInstanceOf(PdfError.PasswordRequired::class.java)
        }
    }

    @Test
    fun refusesAWrongPasswordForEveryEncryptionMethod() = runTest {
        userPasswords.forEach { (path, password) ->
            val uri = Uri.fromFile(copyAsset(path))

            assertThat(
                engine.open(DocumentSource.FromUri(uri), password.uppercase()).exceptionOrNull(),
            ).isInstanceOf(PdfError.IncorrectPassword::class.java)
        }
    }

    @Test
    fun opensEveryEncryptionMethodWithItsPassword() = runTest {
        userPasswords.forEach { (path, password) ->
            val uri = Uri.fromFile(copyAsset(path))

            engine.open(DocumentSource.FromUri(uri), password).getOrThrow().use { document ->
                assertThat(document.metadata.isEncrypted).isTrue()
                assertThat(document.allText()).contains(password)
            }
        }
    }

    @Test
    fun opensADocumentRestrictedByItsOwnerOnlyWithoutAPassword() = runTest {
        val uri = Uri.fromFile(copyAsset(TestFiles.Pdf.OWNER_PASSWORD_ONLY))

        engine.open(DocumentSource.FromUri(uri)).getOrThrow().use { document ->
            assertThat(document.metadata.isEncrypted).isTrue()
            assertThat(document.pageCount).isEqualTo(1)
            // Its author restricted copying (ADR 0020): reported, not enforced, by the engine.
            assertThat(document.canCopyText).isFalse()
            assertThat(document.allText()).contains("Document restreint")
        }
    }

    @Test
    fun allowsCopyingTheTextOfAnUnrestrictedDocument() = runTest {
        open(TestFiles.Pdf.SIMPLE).use { assertThat(it.canCopyText).isTrue() }
    }

    @Test
    fun readsOutlineDestinationsWithTheirPosition() = runTest {
        open(TestFiles.Pdf.LINKS).use { document ->
            val (middle, external) = document.outline()

            assertThat(middle.destination?.pageIndex).isEqualTo(3)
            assertThat(middle.destination?.y).isWithin(1f).of(MIDDLE_OF_PAGE)
            assertThat(external.destination).isNull()
            assertThat(external.uri).isEqualTo("https://example.org/sommaire")
        }
        open(TestFiles.Pdf.LARGE).use { document ->
            val outline = document.outline()

            assertThat(outline).hasSize(12)
            assertThat(outline.last().children.last().destination?.pageIndex).isEqualTo(590)
        }
    }

    @Test
    fun readsLinksWithTheirPositionAndRawUris() = runTest {
        open(TestFiles.Pdf.LINKS).use { document ->
            val links = document.links(1)
            val (middle, named) = links.filterIsInstance<PdfLink.Internal>().map { it.destination }

            assertThat(middle.pageIndex).isEqualTo(3)
            assertThat(middle.y).isWithin(1f).of(MIDDLE_OF_PAGE)
            assertThat(named.pageIndex).isEqualTo(2)
            assertThat(named.y).isWithin(1f).of(NAMED_DESTINATION_Y)
            assertThat(links.filterIsInstance<PdfLink.External>().map { it.uri })
                .containsExactly(
                    "mailto:lutrin@example.org",
                    "javascript:alert(1)",
                    "file:///sdcard/secret.pdf",
                    "file://annexe.html",
                ).inOrder()
        }
    }

    @Test
    fun searchReportsEveryPageAndFindsAHyphenatedWord() = runTest {
        open(TestFiles.Pdf.NESTED_OUTLINE).use { document ->
            val pages = document.search("section 2.2").toList()

            assertThat(pages.map { it.pageIndex }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
            assertThat(pages.flatMap { it.hits }.map { it.pageIndex }).containsExactly(3)
        }
        open(TestFiles.Pdf.TEXT_SELECTION).use { document ->
            val hit = document.search("bibliothèque").toList().flatMap { it.hits }.single()

            assertThat(hit.areas).hasSize(2)
            assertThat(hit.excerpt?.match).isEqualTo("bibliothèque")
        }
    }

    @Test
    fun searchStopsAtTheLimit() = runTest {
        open(TestFiles.Pdf.LARGE).use { document ->
            val pages = document.search("lutrin", SearchOptions(maxHits = 10)).toList()

            assertThat(pages.sumOf { it.hits.size }).isEqualTo(10)
            assertThat(pages.last().isLimitReached).isTrue()
        }
    }

    @Test
    fun searchesAndSelectsArabicText() = runTest {
        open(TestFiles.Pdf.TEXT_SELECTION).use { document ->
            val word = document.search("مكتبة").toList().flatMap { it.hits }.single().bounds
            val press = PdfPoint((word.left + word.right) / 2, (word.top + word.bottom) / 2)

            val selection = document.selectText(0, press, press, SelectionMode.Words)

            assertThat(selection.text).isEqualTo("مكتبة")
            assertThat(selection.quads).hasSize(1)
        }
    }

    @Test
    fun selectsAWordAndALine() = runTest {
        open(TestFiles.Pdf.TEXT_SELECTION).use { document ->
            // Inside "mot" on the first line of the body.
            val press = PdfPoint(98f, 136f)

            assertThat(document.selectText(0, press, press, SelectionMode.Words).text).isEqualTo("mot")
            assertThat(document.selectText(0, press, press, SelectionMode.Lines).text)
                .isEqualTo("Le mot suivant est coupé en fin de ligne : la biblio-")
        }
    }

    @Test
    fun resolvesDestinationsOnARotatedPage() = runTest {
        open(TestFiles.Pdf.ROTATED_DESTINATIONS).use { document ->
            val destinations = document.links(0).filterIsInstance<PdfLink.Internal>().map { it.destination }

            // FitH, FitV, FitR, XYZ, XYZ without x to the page turned by 90°, then FitH, FitR upright.
            assertThat(destinations)
                .containsExactly(
                    PdfDestination(1),
                    PdfDestination(1),
                    PdfDestination(1, x = 200f, y = 100f, width = 200f, height = 200f),
                    PdfDestination(1, x = 500f, y = 100f),
                    PdfDestination(1),
                    PdfDestination(2, y = 542f),
                    PdfDestination(2, x = 100f, y = 442f, width = 200f, height = 200f),
                ).inOrder()
        }
    }

    @Test
    fun searchReturnsEveryOccurrenceOfAPageAndStartsAtTheGivenPage() = runTest {
        open(TestFiles.Pdf.MANY_HITS).use { document ->
            val hits = document.search("lutrin", SearchOptions(maxHits = 5_000)).toList().flatMap { it.hits }

            assertThat(hits).hasSize(800)
        }
        open(TestFiles.Pdf.NESTED_OUTLINE).use { document ->
            val pages = document.search("section", startPage = 4).toList()

            assertThat(pages.map { it.pageIndex }).containsExactly(4, 5, 0, 1, 2, 3).inOrder()
        }
    }

    @Test
    fun tellsWhetherAPointIsOnTextAndPlacesTheHandles() = runTest {
        open(TestFiles.Pdf.TEXT_SELECTION).use { document ->
            val press = PdfPoint(98f, 136f)
            val selection = document.selectText(0, press, press, SelectionMode.Words)

            assertThat(document.isOnText(0, press)).isTrue()
            assertThat(document.isOnText(0, PdfPoint(20f, 20f))).isFalse()
            assertThat(document.selectText(0, checkNotNull(selection.start), checkNotNull(selection.end)).text)
                .isEqualTo("mot")
        }
    }

    @Test
    fun textFunctionsHandleEdgeCases() = runTest {
        val origin = PdfPoint(0f, 0f)
        open(TestFiles.Pdf.SCANNED).use { document ->
            assertThat(document.selectText(0, origin, PdfPoint(595f, 842f), SelectionMode.Lines).isEmpty).isTrue()
            assertThat(document.search("page").toList().flatMap { it.hits }).isEmpty()
            assertThat(document.search("   ").toList()).isEmpty()
            assertThat(runCatching { document.selectText(2, origin, origin) }.exceptionOrNull())
                .isInstanceOf(PdfError.PageOutOfRange::class.java)
        }
        val closed = open(TestFiles.Pdf.LINKS).apply { close() }
        assertThat(runCatching { closed.outline() }.exceptionOrNull()).isInstanceOf(PdfError.Closed::class.java)
        assertThat(runCatching { closed.selectText(0, origin, origin) }.exceptionOrNull())
            .isInstanceOf(PdfError.Closed::class.java)
    }

    private suspend fun open(path: String): PdfDocument =
        engine.open(DocumentSource.FromFile(copyAsset(path))).getOrThrow()

    /** The whole text of the first page. */
    private suspend fun PdfDocument.allText(): String =
        selectText(0, PdfPoint(0f, 0f), PdfPoint(PAGE_WIDTH, PAGE_HEIGHT)).text

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
        const val PAGE_WIDTH = 595f
        const val PAGE_HEIGHT = 842f

        // Targets of the links of TestFiles.Pdf.LINKS, in points from the top of the page.
        const val MIDDLE_OF_PAGE = 421f
        const val NAMED_DESTINATION_Y = 100f
    }
}
