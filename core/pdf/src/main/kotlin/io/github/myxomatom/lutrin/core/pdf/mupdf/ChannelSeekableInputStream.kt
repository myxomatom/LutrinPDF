// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.SeekableInputStream as MuPdfInputStream
import com.artifex.mupdf.fitz.SeekableStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Lets MuPDF read a document through a [FileChannel] (for example from a content URI's file
 * descriptor) instead of a path, without copying the file.
 */
internal class ChannelSeekableInputStream(private val channel: FileChannel) : MuPdfInputStream {
    override fun read(b: ByteArray): Int = channel.read(ByteBuffer.wrap(b))

    override fun seek(offset: Long, whence: Int): Long {
        val target =
            when (whence) {
                SeekableStream.SEEK_SET -> offset
                SeekableStream.SEEK_CUR -> channel.position() + offset
                SeekableStream.SEEK_END -> channel.size() + offset
                else -> throw IOException("Unknown seek origin $whence")
            }
        if (target < 0) throw IOException("Negative position $target")
        channel.position(target)
        return target
    }

    override fun position(): Long = channel.position()
}
