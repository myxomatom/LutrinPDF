// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.document

import java.util.Locale
import javax.inject.Inject

/**
 * Checks an intent sent by another app (« Ouvrir avec », « Partager », F01) and extracts the
 * document to open, following the policy of ADR 0014 (`AGENTS.md` §9):
 *
 * - only [IncomingIntent.ACTION_VIEW] (data URI) and [IncomingIntent.ACTION_SEND]
 *   (`EXTRA_STREAM`) ask for a document; any other action is [IncomingDocumentResult.NoDocument];
 * - the URI must be a well-formed `content://` URI that does not belong to Lutrin itself;
 * - the declared or provider type must be PDF, or else unknown or generic
 *   (`application/octet-stream`…): the engine then accepts the content only if it starts with
 *   the `%PDF-` header, and reports « not a PDF » otherwise;
 * - the `page` extra, one-based, is read for [IncomingIntent.ACTION_VIEW] only (`SPEC.md` §2.4);
 *   a missing, zero or negative page is no page at all: the reader resumes the saved page, or
 *   opens the first one (ADR 0015).
 *
 * @param typeResolver asks the provider for the MIME type when the sender's type is not PDF.
 */
class ParseIncomingDocumentUseCase @Inject constructor(private val typeResolver: DocumentTypeResolver) {
    /**
     * @param intent what the other app sent.
     * @return the document to open, the reason to refuse it, or [IncomingDocumentResult.NoDocument].
     */
    suspend operator fun invoke(intent: IncomingIntent): IncomingDocumentResult {
        if (intent.action != IncomingIntent.ACTION_VIEW && intent.action != IncomingIntent.ACTION_SEND) {
            return IncomingDocumentResult.NoDocument
        }
        val uri = if (intent.action == IncomingIntent.ACTION_VIEW) intent.data else intent.stream
        return if (uri ==
            null
        ) {
            IncomingDocumentResult.Rejected(IncomingRejection.MISSING_URI)
        } else {
            checkDocument(uri, intent)
        }
    }

    /** Checks the URI, then the type; the page extra is read for « Ouvrir avec » only. */
    private suspend fun checkDocument(uri: String, intent: IncomingIntent): IncomingDocumentResult {
        val problem =
            uriProblem(uri, intent.receiverPackage)
                ?: IncomingRejection.UNSUPPORTED_TYPE.takeUnless { hasAcceptableType(intent.type, uri) }
        if (problem != null) return IncomingDocumentResult.Rejected(problem)
        val page = if (intent.action == IncomingIntent.ACTION_VIEW) intent.page else null
        return IncomingDocumentResult.Accepted(uri, pageIndex(page))
    }

    /** The provider is only asked when the sender did not already say PDF (it is an IPC). */
    private suspend fun hasAcceptableType(declared: String?, uri: String): Boolean {
        val declaredKind = mimeKind(declared)
        if (declaredKind == MimeKind.PDF) return true
        val resolvedKind = mimeKind(typeResolver.typeOf(uri))
        return resolvedKind == MimeKind.PDF || (declaredKind == MimeKind.UNKNOWN && resolvedKind == MimeKind.UNKNOWN)
    }
}

/** What a MIME type says about a document. */
internal enum class MimeKind {
    /** A PDF type. */
    PDF,

    /** No type, or a generic one that says nothing about the format: the content decides. */
    UNKNOWN,

    /** Any other type: not a PDF. */
    OTHER,
}

/** MIME types of PDF documents; `application/x-pdf` is still sent by some older apps. */
private val PDF_TYPES = setOf("application/pdf", "application/x-pdf")

/** Types that say nothing about the format, sent by mail clients, browsers and file managers. */
private val GENERIC_TYPES = setOf("application/octet-stream", "binary/octet-stream", "application/*", "*/*")

/**
 * Classifies a MIME type, ignoring its case and its parameters (`; charset=…`).
 *
 * @param type the type, or `null` when unknown.
 */
internal fun mimeKind(type: String?): MimeKind {
    val essence = type?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    return when {
        essence.isNullOrEmpty() -> MimeKind.UNKNOWN
        essence in PDF_TYPES -> MimeKind.PDF
        essence in GENERIC_TYPES -> MimeKind.UNKNOWN
        else -> MimeKind.OTHER
    }
}

/** A URI scheme (RFC 3986 §3.1) followed by its colon. */
private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

/** The only scheme accepted: content served by a provider, with a grant (`AGENTS.md` §9). */
private const val CONTENT_SCHEME = "content"

/** Characters ending the authority of a hierarchical URI. */
private const val AUTHORITY_END = "/?#"

/**
 * What is wrong with [uri], if anything.
 *
 * @param uri the URI sent by another app.
 * @param receiverPackage package name of this app; its providers' authorities start with it
 *   (Android convention, followed by every library the app uses).
 * @return the rejection reason, or `null` when the URI is an acceptable `content://` URI.
 */
internal fun uriProblem(uri: String, receiverPackage: String): IncomingRejection? {
    val scheme = SCHEME.find(uri)?.groupValues?.get(1)
    return when {
        uri.any(Char::isISOControl) -> IncomingRejection.MALFORMED_URI
        scheme == null -> IncomingRejection.MALFORMED_URI
        !scheme.equals(CONTENT_SCHEME, ignoreCase = true) -> IncomingRejection.UNSUPPORTED_SCHEME
        else -> authorityProblem(uri.substring(scheme.length + 1), receiverPackage)
    }
}

/**
 * What is wrong with the part of a `content:` URI after its scheme, if anything.
 *
 * @param hierarchical `//authority/path…`.
 * @param receiverPackage package name of this app.
 */
private fun authorityProblem(hierarchical: String, receiverPackage: String): IncomingRejection? {
    // "10@authority" designates a provider of another user profile (work profile sharing).
    val authority =
        if (hierarchical.startsWith("//")) {
            hierarchical.drop(2).takeWhile { it !in AUTHORITY_END }.substringAfter('@')
        } else {
            ""
        }
    return when {
        authority.isEmpty() -> IncomingRejection.MALFORMED_URI
        authority == receiverPackage || authority.startsWith("$receiverPackage.") -> IncomingRejection.APP_OWN_URI
        else -> null
    }
}

/**
 * Zero-based page index of the one-based `page` extra.
 *
 * @param page the page number sent, or `null`.
 * @return `page - 1`, or `null` (no explicit page) when [page] is missing, zero or negative.
 */
internal fun pageIndex(page: Int?): Int? = if (page != null && page >= 1) page - 1 else null
