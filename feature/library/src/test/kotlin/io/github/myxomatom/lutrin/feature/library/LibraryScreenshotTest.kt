// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot references of the library with recent documents (E1): light, dark, large font and
 * French. Rows with a thumbnail and rows still waiting for theirs (placeholder).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class LibraryScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val documents =
        listOf(
            item("1", "Contrat de location", lastPage = 2, pageCount = 8, OpenedOn.Today(LocalTime.of(20, 41))),
            item("2", "Facture octobre 2026", lastPage = 0, pageCount = 2, OpenedOn.Today(LocalTime.of(18, 5))),
            item("3", "Relevé de compte", lastPage = 3, pageCount = 4, OpenedOn.Yesterday, thumbnail = false),
            item("4", "Manuel lave-linge", lastPage = 21, pageCount = 64, OpenedOn.ThisYear(LocalDate.of(2026, 10, 28))),
            item("5", "Mémoire — chapitre 2", lastPage = 86, pageCount = 112, OpenedOn.ThisYear(LocalDate.of(2026, 10, 25))),
            item("6", "Billet de train Lyon–Paris", lastPage = 0, pageCount = 1, OpenedOn.OtherYear(LocalDate.of(2025, 10, 19))),
        )

    private fun item(
        id: String,
        name: String,
        lastPage: Int,
        pageCount: Int,
        openedOn: OpenedOn,
        thumbnail: Boolean = true,
    ) = RecentDocumentItem(
        documentId = id,
        uri = "content://com.example.files/document/$id",
        name = name,
        pageCount = pageCount,
        lastPage = lastPage,
        openedOn = openedOn,
        thumbnailPath = if (thumbnail) "/thumbnails/$id.png" else null,
    )

    private fun capture(name: String, darkTheme: Boolean, fontScale: Float = 1f) {
        val page = fakePage()
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LutrinTheme(darkTheme = darkTheme, dynamicColor = false) {
                    LibraryScreen(
                        state = LibraryUiState.Content(documents),
                        onOpenFile = {},
                        onOpenSettings = {},
                        onOpenDocument = {},
                        loadThumbnail = { page },
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    /** A white page with a title and lines of text, as a drawn first page would look. */
    private fun fakePage(): ImageBitmap {
        val bitmap = ImageBitmap(PAGE_WIDTH, PAGE_HEIGHT)
        val canvas = Canvas(bitmap)
        canvas.drawRect(Rect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat()), Paint().apply { color = Color.White })
        val ink = Paint().apply { color = Color(0xFF8A8E96) }
        canvas.drawRect(Rect(Offset(20f, 24f), Offset(110f, 34f)), ink)
        (0 until LINES).forEach { line ->
            val top = 52f + line * 16f
            canvas.drawRect(Rect(Offset(20f, top), Offset(if (line % 4 == 3) 90f else 144f, top + 5f)), ink)
        }
        return bitmap
    }

    @Test
    fun light() = capture("library_recents_light", darkTheme = false)

    @Test
    fun dark() = capture("library_recents_dark", darkTheme = true)

    @Test
    fun largeFont() = capture("library_recents_light_font200", darkTheme = false, fontScale = 2f)

    @Test
    @Config(qualifiers = "+fr")
    fun french() = capture("library_recents_light_fr", darkTheme = false)

    private companion object {
        const val PAGE_WIDTH = 164
        const val PAGE_HEIGHT = 232
        const val LINES = 10
    }
}
