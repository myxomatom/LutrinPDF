// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import io.github.myxomatom.lutrin.core.domain.dispatchers.IoDispatcher
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Decodes the thumbnail files of recent documents (PNG files, ADR 0015) for the library.
 * Only the cards and rows on screen load theirs, so that memory stays proportional to what is
 * shown. A file is drawn for the resume card (up to 416 × 552 px, ADR 0018); a row, four times
 * smaller in area, decodes it subsampled ([sampleSize]): about 230 KB per row instead of 920 KB.
 *
 * @param ioDispatcher where the files are read and decoded.
 */
class ThumbnailLoader @Inject constructor(@param:IoDispatcher private val ioDispatcher: CoroutineDispatcher) {
    /**
     * @param path absolute path of a thumbnail file.
     * @param size size of the frame the picture is fitted in, in pixels; the picture is decoded
     *   no smaller than it is shown. An empty size decodes the whole picture.
     * @return the picture, or `null` when the file is missing or not an image.
     */
    suspend fun load(path: String, size: IntSize): ImageBitmap? = withContext(ioDispatcher) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(IntSize(bounds.outWidth, bounds.outHeight), size)
        }
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    }
}

/**
 * The largest power of two by which a picture of [image] pixels can be subsampled and still not
 * be enlarged once fitted (`ContentScale.Fit`) in a frame of [frame] pixels: the value of
 * [BitmapFactory.Options.inSampleSize].
 *
 * @param image size of the picture in its file, in pixels.
 * @param frame size of the frame it is shown in, in pixels.
 * @return 1 or more; 1 when either size is empty.
 */
internal fun sampleSize(image: IntSize, frame: IntSize): Int {
    if (image.isEmpty() || frame.isEmpty()) return 1
    // Fitted, the picture is shown at min(frame / image) of its size: it can shrink by the inverse.
    val shrink = max(image.width.toDouble() / frame.width, image.height.toDouble() / frame.height)
    var sample = 1
    while (sample * 2 <= shrink) sample *= 2
    return sample
}

/** Whether this size has no area. */
private fun IntSize.isEmpty(): Boolean = width <= 0 || height <= 0
