// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.testing

/**
 * Asset paths of the generated test file set (`SPEC.md` §9), relative to the assets of this
 * module. Each file is described in `core/testing/README.md`.
 *
 * Example, in an instrumented test: `context.assets.open(TestFiles.Pdf.SIMPLE)`.
 */
object TestFiles {
    /** PDF documents for the reader and the engine. */
    object Pdf {
        /** One page, text only. */
        const val SIMPLE = "pdfs/simple_1page.pdf"

        /** 600 text pages. */
        const val LARGE = "pdfs/large_600pages.pdf"

        /** Three-level outline with 15 entries over 6 pages. */
        const val NESTED_OUTLINE = "pdfs/nested_outline.pdf"

        /** Page 1 links to page 3 and to `https://example.org/`. */
        const val LINKS = "pdfs/links_internal_external.pdf"

        /** AES-256 encrypted; the user password is [ENCRYPTED_PASSWORD]. */
        const val ENCRYPTED = "pdfs/encrypted_password_lutrin.pdf"

        /** Password of [ENCRYPTED]. Test data, not a secret. */
        const val ENCRYPTED_PASSWORD = "lutrin"

        /** AcroForm with a text field, a checkbox and a combo box. */
        const val FORM = "pdfs/form_acroform.pdf"

        /** Two image-only pages without any text layer. */
        const val SCANNED = "pdfs/scanned_images_only.pdf"

        /** A4, Letter landscape, A5, square and a page rotated by 90°. */
        const val MIXED_PAGE_SIZES = "pdfs/mixed_page_sizes.pdf"

        /** Accented French, Arabic (right to left) and Chinese text. */
        const val NON_LATIN = "pdfs/non_latin_text.pdf"

        /** A valid file cut in half; may be repaired by the engine. */
        const val CORRUPTED_TRUNCATED = "pdfs/corrupted_truncated.pdf"

        /** A `%PDF` header followed by noise; cannot be opened. */
        const val CORRUPTED_GARBAGE = "pdfs/corrupted_garbage.pdf"

        /** A text file renamed to `.pdf`. */
        const val NOT_A_PDF = "pdfs/not_a_pdf.pdf"
    }

    /** Inputs of the PDF tools (merge, conversion). */
    object Tools {
        /** JPEG, 640 × 480. */
        const val JPEG = "tools/image_small.jpg"

        /** PNG, 1600 × 1200. */
        const val PNG = "tools/image_medium.png"

        /** WebP, 1200 × 900. */
        const val WEBP = "tools/image.webp"

        /** JPEG stored as 800 × 600 with EXIF orientation 6: displayed as portrait. */
        const val EXIF_ROTATED_JPEG = "tools/photo_exif_orientation_6.jpg"

        /** PNG, 8256 × 6192 (51.1 megapixels): must be downsampled. */
        const val HUGE_PNG = "tools/image_huge_51mpx.png"

        /** UTF-8 text with accents, emoji, Arabic and Chinese. */
        const val TEXT = "tools/text_utf8.txt"

        /** UTF-8 Markdown with headings, list, emphasis and a code block. */
        const val MARKDOWN = "tools/markdown_utf8.md"

        /** Local HTML referencing remote CSS, script and image, which must all be blocked. */
        const val HTML_WITH_REMOTE_RESOURCE = "tools/local_with_remote_resource.html"
    }
}
