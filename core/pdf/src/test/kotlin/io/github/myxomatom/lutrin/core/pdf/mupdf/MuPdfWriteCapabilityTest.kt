// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.pdf.mupdf

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.DocumentWriter
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.PDFDocument
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Early check (ROADMAP M1) that MuPDF covers the v1.1 tools: merging PDFs and turning an image
 * into a PDF. The real implementation comes with `PdfWriter` (M6).
 */
class MuPdfWriteCapabilityTest {
    @Before
    fun requireHostEngine() = assumeHostEngine()

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `merges two PDFs by grafting their pages`() {
        val output = folder.newFile("merged.pdf")
        val merged = PDFDocument()
        listOf("pdfs/simple_1page.pdf", "pdfs/links_internal_external.pdf").forEach { path ->
            val source = Document.openDocument(TestAssets.file(path).absolutePath).asPDF()
            (0 until source.countPages()).forEach { merged.graftPage(-1, source, it) }
            source.destroy()
        }
        merged.save(output.absolutePath, "garbage,compress")
        merged.destroy()

        val reopened = Document.openDocument(output.absolutePath)
        assertThat(reopened.countPages()).isEqualTo(5)
        reopened.destroy()
    }

    @Test
    fun `turns an image into a one page PDF`() {
        val output = folder.newFile("image.pdf")
        val image = Document.openDocument(TestAssets.file("tools/image_small.jpg").absolutePath)
        val page = image.loadPage(0)
        val writer = DocumentWriter(output.absolutePath, "pdf", "")
        val device = writer.beginPage(page.bounds)
        page.run(device, Matrix.Identity())
        writer.endPage()
        writer.close()
        writer.destroy()
        page.destroy()
        image.destroy()

        val reopened = Document.openDocument(output.absolutePath)
        assertThat(reopened.countPages()).isEqualTo(1)
        reopened.destroy()
    }
}
