// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.SeekableStream
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.io.RandomAccessFile
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChannelSeekableInputStreamTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun stream(content: String): ChannelSeekableInputStream {
        val file = folder.newFile().apply { writeText(content) }
        return ChannelSeekableInputStream(RandomAccessFile(file, "r").channel)
    }

    @Test
    fun `reads and seeks from every origin`() {
        val stream = stream("0123456789")
        val buffer = ByteArray(3)

        assertThat(stream.seek(2, SeekableStream.SEEK_SET)).isEqualTo(2)
        assertThat(stream.read(buffer)).isEqualTo(3)
        assertThat(String(buffer)).isEqualTo("234")
        assertThat(stream.seek(1, SeekableStream.SEEK_CUR)).isEqualTo(6)
        assertThat(stream.seek(-2, SeekableStream.SEEK_END)).isEqualTo(8)
        assertThat(stream.position()).isEqualTo(8)
        assertThat(stream.read(ByteArray(5))).isEqualTo(2)
        assertThat(stream.read(ByteArray(5))).isEqualTo(-1)
    }

    @Test
    fun `rejects invalid seeks`() {
        val stream = stream("abc")

        assertThrows(IOException::class.java) { stream.seek(-1, SeekableStream.SEEK_SET) }
        assertThrows(IOException::class.java) { stream.seek(0, 42) }
    }

    @Test
    fun `lets MuPDF open a document through a channel`() {
        assumeHostEngine()
        val channel = RandomAccessFile(TestAssets.file("pdfs/links_internal_external.pdf"), "r").channel
        val document = Document.openDocument(ChannelSeekableInputStream(channel), "application/pdf")
        try {
            assertThat(document.countPages()).isEqualTo(4)
        } finally {
            document.destroy()
            channel.close()
        }
    }
}
