// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.document

/**
 * What another app sent to Lutrin with an intent (F01, « Ouvrir avec » and « Partager »),
 * copied out of the Android `Intent` so that its checks stay pure Kotlin (ADR 0014).
 *
 * Nothing here is trusted: every field comes from the sender and is checked by
 * [ParseIncomingDocumentUseCase].
 *
 * @property action the intent action, such as [ACTION_VIEW] or [ACTION_SEND]; `null` if none.
 * @property data the data URI of the intent (used by [ACTION_VIEW]), as a string.
 * @property stream the `EXTRA_STREAM` URI (used by [ACTION_SEND]), as a string; `null` when
 *   absent or not a URI.
 * @property type the MIME type declared by the sender, as given (parameters and case included).
 * @property page the `page` extra (`SPEC.md` §2.4), a one-based page number as the user reads
 *   it; `null` when absent or not an `Int`.
 * @property receiverPackage package name of this app at run time: documents served by its own
 *   content providers are refused.
 */
data class IncomingIntent(
    val action: String?,
    val data: String? = null,
    val stream: String? = null,
    val type: String? = null,
    val page: Int? = null,
    val receiverPackage: String,
) {
    /** The actions Lutrin answers to; the values of `android.content.Intent`. */
    companion object {
        /** `Intent.ACTION_VIEW`: « Ouvrir avec ». */
        const val ACTION_VIEW: String = "android.intent.action.VIEW"

        /** `Intent.ACTION_SEND`: « Partager » with a single document. */
        const val ACTION_SEND: String = "android.intent.action.SEND"
    }
}

/** Outcome of checking an [IncomingIntent]. */
sealed interface IncomingDocumentResult {
    /** The intent does not ask to open a document (for example the launcher): nothing to do. */
    data object NoDocument : IncomingDocumentResult

    /**
     * The intent designates a document to open in the reader. The content itself is not read
     * yet: the PDF engine checks it when it opens the document (header, damage, password).
     *
     * @property uri the `content://` URI of the document.
     * @property pageIndex zero-based page to open the document at; `null` when the intent gave no
     *   valid page, and the reader then resumes where the user stopped reading, or opens the first
     *   page (ADR 0015). It may exceed the page count, which only the reader knows: the reader
     *   clamps it.
     */
    data class Accepted(val uri: String, val pageIndex: Int? = null) : IncomingDocumentResult

    /**
     * The intent cannot be honoured; the user is told why and stays in (or returns to) the library.
     *
     * @property reason what is wrong with the intent.
     */
    data class Rejected(val reason: IncomingRejection) : IncomingDocumentResult
}

/** Why an intent asking to open a document was refused (`AGENTS.md` §9). */
enum class IncomingRejection {
    /** No data URI (« Ouvrir avec ») or no `EXTRA_STREAM` URI (« Partager »). */
    MISSING_URI,

    /** Not a URI, or a `content:` URI without an authority. */
    MALFORMED_URI,

    /** Not a `content://` URI: `file://` bypasses the Storage Access Framework, `http(s)://` needs the network. */
    UNSUPPORTED_SCHEME,

    /** A URI of one of Lutrin's own content providers, which another app must not make it read. */
    APP_OWN_URI,

    /** The sender or the provider says the content is of another type than PDF. */
    UNSUPPORTED_TYPE,
}
