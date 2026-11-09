// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.recents

import java.security.MessageDigest

/** Bytes of the SHA-256 digest kept in an identifier: 128 bits, collisions are not a concern. */
private const val ID_BYTES = 16

/** Mask selecting the low byte of an `Int`, for the hexadecimal encoding. */
private const val BYTE_MASK = 0xFF

/** Radix of the encoding. */
private const val HEX_RADIX = 16

/** Hexadecimal digits of one byte. */
private const val HEX_WIDTH = 2

/**
 * Identifier of the recent document opened from [uri] (ADR 0015): the first 128 bits of the
 * SHA-256 digest of the URI, in lowercase hexadecimal (32 characters).
 *
 * The same URI always gives the same identifier, so that a document that leaves the list and
 * comes back keeps it, and the identifier says nothing readable about the URI.
 *
 * @param uri the document URI, as stored in the recent list.
 */
fun documentIdFor(uri: String): String = MessageDigest
    .getInstance("SHA-256")
    .digest(uri.toByteArray(Charsets.UTF_8))
    .take(ID_BYTES)
    .joinToString("") { (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(HEX_WIDTH, '0') }
