// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

/**
 * Business errors of the PDF engine (`SPEC.md` §6). The UI maps each one to a message; none
 * of them may crash the app. Messages are for logs and never contain document content,
 * passwords or file paths (`AGENTS.md` §1.8).
 */
sealed class PdfError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The document is encrypted and no password was given. */
    class PasswordRequired : PdfError("A password is required")

    /** The given password does not open the document; the user may try again. */
    class IncorrectPassword : PdfError("Incorrect password")

    /** The file is not a PDF (for example another format renamed to `.pdf`). */
    class UnsupportedFormat : PdfError("Not a PDF document")

    /** The file is a PDF but is damaged beyond repair. */
    class Unreadable(cause: Throwable? = null) : PdfError("The document cannot be read", cause)

    /** The file no longer exists or the permission to read it was revoked. */
    class NotAccessible(cause: Throwable? = null) : PdfError("The document is not accessible", cause)

    /**
     * A page index outside `0 until pageCount` was requested.
     *
     * @property pageIndex the requested index.
     * @property pageCount number of pages of the document.
     */
    class PageOutOfRange(val pageIndex: Int, val pageCount: Int) :
        PdfError("Page $pageIndex is out of range 0 until $pageCount")

    /** The document was used after being closed. */
    class Closed : PdfError("The document is closed")
}
