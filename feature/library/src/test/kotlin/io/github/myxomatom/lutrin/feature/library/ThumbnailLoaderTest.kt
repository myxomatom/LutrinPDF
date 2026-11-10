// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Real PNG decoding (native graphics) of thumbnail files at the size they are shown. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbnailLoaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.loader() = ThumbnailLoader(StandardTestDispatcher(testScheduler))

    /** A white A4 page drawn for the resume card: 390 x 552 px. */
    private fun thumbnailFile(): File {
        val file = folder.newFile("page.v2.png")
        val bitmap = Bitmap.createBitmap(A4_WIDTH, A4_HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        return file
    }

    @Test
    fun `the card decodes the whole picture`() = runTest {
        val picture = loader().load(thumbnailFile().path, CARD_XXXHDPI)

        assertThat(picture?.width).isEqualTo(A4_WIDTH)
        assertThat(picture?.height).isEqualTo(A4_HEIGHT)
    }

    @Test
    fun `a row decodes a subsampled picture, never smaller than shown`() = runTest {
        val file = thumbnailFile()

        val xxxhdpi = loader().load(file.path, ROW_XXXHDPI)
        val xhdpi = loader().load(file.path, ROW_XHDPI)

        // Fitted in 208 x 272, the page is shown 192 x 272: 195 x 276 is enough.
        assertThat(xxxhdpi?.width).isEqualTo(A4_WIDTH / 2)
        assertThat(xxxhdpi?.height).isEqualTo(A4_HEIGHT / 2)
        assertThat(xhdpi?.height).isEqualTo(A4_HEIGHT / 4)
    }

    @Test
    fun `a missing or unreadable file gives no picture`() = runTest {
        val notImage = folder.newFile("broken.v2.png").apply { writeText("not a picture") }

        assertThat(loader().load(File(folder.root, "missing.png").path, ROW_XXXHDPI)).isNull()
        assertThat(loader().load(notImage.path, ROW_XXXHDPI)).isNull()
    }

    @Test
    fun `the sample size never enlarges the fitted picture`() {
        val picture = IntSize(A4_WIDTH, A4_HEIGHT)

        assertThat(sampleSize(picture, CARD_XXXHDPI)).isEqualTo(1)
        assertThat(sampleSize(picture, ROW_XXXHDPI)).isEqualTo(2)
        assertThat(sampleSize(picture, ROW_XHDPI)).isEqualTo(4)
        // A frame larger than the picture: never more than 1.
        assertThat(sampleSize(picture, IntSize(2_000, 2_000))).isEqualTo(1)
        // A landscape page in a portrait frame: bound by its width.
        assertThat(sampleSize(IntSize(416, 294), IntSize(104, 136))).isEqualTo(4)
    }

    @Test
    fun `an empty size decodes the whole picture`() {
        assertThat(sampleSize(IntSize(A4_WIDTH, A4_HEIGHT), IntSize.Zero)).isEqualTo(1)
        assertThat(sampleSize(IntSize.Zero, ROW_XHDPI)).isEqualTo(1)
    }

    private companion object {
        const val A4_WIDTH = 390
        const val A4_HEIGHT = 552

        /** Ignored by PNG, which is lossless. */
        const val PNG_QUALITY = 100

        /** 104 x 138 dp at x 4. */
        val CARD_XXXHDPI = IntSize(416, 552)

        /** 52 x 68 dp at x 4 and x 2. */
        val ROW_XXXHDPI = IntSize(208, 272)
        val ROW_XHDPI = IntSize(104, 136)
    }
}
