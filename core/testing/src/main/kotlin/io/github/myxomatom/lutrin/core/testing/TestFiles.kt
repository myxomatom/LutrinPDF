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

        /** 600 text pages, with a two-level outline (12 parts of 5 chapters). */
        const val LARGE = "pdfs/large_600pages.pdf"

        /** Three-level outline with 15 entries over 6 pages. */
        const val NESTED_OUTLINE = "pdfs/nested_outline.pdf"

        /**
         * Page 1 links to page 3 and to `https://example.org/`; page 2 links to the middle of
         * page 4, to a named destination at the top of page 3, and to `mailto:`, `javascript:`,
         * `file:` and relative URIs. Its outline has an entry to the middle of page 4 and an
         * external one.
         */
        const val LINKS = "pdfs/links_internal_external.pdf"

        /**
         * One page for search and selection: a word cut by a hyphen at the end of a line
         * ("biblio-" / "thèque") and an Arabic sentence.
         */
        const val TEXT_SELECTION = "pdfs/text_selection.pdf"

        /** One page with 800 occurrences of "lutrin" (80 lines of 10). */
        const val MANY_HITS = "pdfs/search_many_hits.pdf"

        /**
         * Three pages; page 1 links with explicit destinations to page 2, rotated by 90°
         * (`/FitH 300`, `/FitV 200`, `/FitR 100 200 300 400`, `/XYZ 100 500`, `/XYZ null 500`), then
         * to page 3, upright (`/FitH 300`, `/FitR 100 200 300 400`), in this order.
         */
        const val ROTATED_DESTINATIONS = "pdfs/rotated_destinations.pdf"

        /** AES-256 encrypted; the user password is [ENCRYPTED_PASSWORD]. */
        const val ENCRYPTED = "pdfs/encrypted_password_lutrin.pdf"

        /** Password of [ENCRYPTED]. Test data, not a secret. */
        const val ENCRYPTED_PASSWORD = "lutrin"

        /** RC4 128-bit encrypted (PDF 1.4, revision 3); the user password is [ENCRYPTED_RC4_PASSWORD]. */
        const val ENCRYPTED_RC4 = "pdfs/encrypted_rc4_128.pdf"

        /** Password of [ENCRYPTED_RC4]. Test data, not a secret. */
        const val ENCRYPTED_RC4_PASSWORD = "lutrin-rc4"

        /** AES-128 encrypted (revision 4); the user password is [ENCRYPTED_AES_128_PASSWORD]. */
        const val ENCRYPTED_AES_128 = "pdfs/encrypted_aes_128.pdf"

        /** Password of [ENCRYPTED_AES_128]. Test data, not a secret. */
        const val ENCRYPTED_AES_128_PASSWORD = "lutrin-aes128"

        /**
         * AES-128 encrypted with an empty user password and an owner password only: readers open
         * it without asking (printing and copying are restricted).
         */
        const val OWNER_PASSWORD_ONLY = "pdfs/encrypted_owner_password_only.pdf"

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
