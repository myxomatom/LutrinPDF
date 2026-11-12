// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.links

import java.util.Locale
import javax.inject.Inject

/**
 * Longest external link accepted, in characters: far beyond any real address, it keeps a link
 * crafted to be huge out of the confirmation dialog (ADR 0022).
 */
const val MAX_EXTERNAL_LINK_LENGTH: Int = 8_192

/** Schemes opened by another app, after confirmation (F09, ADR 0022). */
private val ALLOWED_SCHEMES = setOf("http", "https", "mailto")

/** Schemes whose address must name a host (`scheme://host…`). */
private val SCHEMES_WITH_HOST = setOf("http", "https")

/** Separator between the scheme and the rest of a URI. */
private const val SCHEME_END = ':'

/** Start of the authority of a hierarchical URI. */
private const val AUTHORITY_START = "//"

/** Characters ending the authority of a URI (RFC 3986 §3.2). */
private val AUTHORITY_END = charArrayOf('/', '?', '#')

/** Separator of the user information in an authority. */
private const val USER_INFO_END = '@'

/** Separator of the port in an authority. */
private const val PORT_START = ':'

/**
 * Outcome of [CheckExternalLinkUseCase].
 */
sealed interface ExternalLinkCheck {
    /**
     * The link may be offered to another app, after confirmation (V7).
     *
     * @property uri the link to show and open: trimmed, with its scheme in lower case (Android
     *   matches intent schemes case-sensitively).
     */
    data class Allowed(val uri: String) : ExternalLinkCheck

    /** The link is never opened: unsupported scheme, relative or malformed link (ADR 0022). */
    data object Refused : ExternalLinkCheck
}

/**
 * Decides whether an external link of a document (link or outline entry, F09) may be offered to
 * another app. The engine gives the URI as written in the document, whatever its scheme
 * (ADR 0020); only `http`, `https` and `mailto` are accepted (ADR 0022). Everything else is
 * refused: `javascript:`, `file:`, `content:`, `intent:`, any other scheme, relative links (MuPDF
 * turns them into `file://…`), blank or malformed links, links holding spaces, control or
 * invisible formatting characters, and links longer than [MAX_EXTERNAL_LINK_LENGTH].
 *
 * Pure: the app never opens the link itself and never reaches the network.
 */
class CheckExternalLinkUseCase @Inject constructor() {
    /**
     * @param uri the external link, as given by the engine.
     * @return [ExternalLinkCheck.Allowed] with the link to show, or [ExternalLinkCheck.Refused].
     */
    operator fun invoke(uri: String): ExternalLinkCheck {
        val link = uri.trim()
        val scheme = schemeOf(link)?.lowercase(Locale.ROOT)
        return if (scheme != null && isAcceptable(link, scheme)) {
            ExternalLinkCheck.Allowed(scheme + link.substring(scheme.length))
        } else {
            ExternalLinkCheck.Refused
        }
    }

    /** The scheme of [link], before its first `:`; `null` for a relative or malformed link. */
    private fun schemeOf(link: String): String? {
        val schemeEnd = link.indexOf(SCHEME_END)
        val scheme = if (schemeEnd > 0) link.substring(0, schemeEnd) else null
        return scheme?.takeIf(::isScheme)
    }

    /** Whether [link], of the lower-case [scheme], may be opened by another app. */
    private fun isAcceptable(link: String, scheme: String): Boolean {
        val rest = link.substring(scheme.length + 1)
        return link.length <= MAX_EXTERNAL_LINK_LENGTH &&
            link.none(::isForbiddenChar) &&
            scheme in ALLOWED_SCHEMES &&
            rest.isNotEmpty() &&
            (scheme !in SCHEMES_WITH_HOST || hasHost(rest))
    }

    /** RFC 3986 §3.1: a letter, then letters, digits, `+`, `-` or `.`. */
    private fun isScheme(scheme: String): Boolean =
        scheme.first().isAsciiLetter() && scheme.all { it.isAsciiLetter() || it.isAsciiDigit() || it in "+-." }

    /** Whether the part after `scheme:` starts with `//` and a non-empty host. */
    private fun hasHost(rest: String): Boolean {
        if (!rest.startsWith(AUTHORITY_START)) return false
        val authority = rest.substring(AUTHORITY_START.length).substringBefore(AUTHORITY_END)
        val host = authority.substringAfterLast(USER_INFO_END).substringBefore(PORT_START)
        return host.isNotEmpty()
    }

    /**
     * Spaces, control characters and invisible formatting characters (such as the bidirectional
     * overrides, which reverse how the rest of the link is displayed) have no place in a link:
     * they hide what it really is.
     */
    private fun isForbiddenChar(char: Char): Boolean =
        char.isWhitespace() || char.isISOControl() || char.category == CharCategory.FORMAT

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}

/** First index of any of [chars], as [String.substringBefore] for several delimiters. */
private fun String.substringBefore(chars: CharArray): String {
    val end = indexOfAny(chars)
    return if (end < 0) this else substring(0, end)
}
