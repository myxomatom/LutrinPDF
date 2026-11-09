// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.myxomatom.lutrin.core.domain.dispatchers.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Decodes the thumbnail files of recent documents (small PNG files, ADR 0015) for the library.
 * Only the rows on screen load theirs, so that memory stays proportional to what is shown.
 *
 * @param ioDispatcher where the files are read and decoded.
 */
class ThumbnailLoader @Inject constructor(@param:IoDispatcher private val ioDispatcher: CoroutineDispatcher) {
    /**
     * @param path absolute path of a thumbnail file.
     * @return the picture, or `null` when the file is missing or not an image.
     */
    suspend fun load(path: String): ImageBitmap? = withContext(ioDispatcher) {
        BitmapFactory.decodeFile(path)?.asImageBitmap()
    }
}
