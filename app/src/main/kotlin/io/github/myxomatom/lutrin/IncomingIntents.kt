// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import io.github.myxomatom.lutrin.core.domain.document.IncomingIntent

/**
 * Optional `Int` extra of `ACTION_VIEW` (`SPEC.md` §2.4): the one-based page to open the
 * document at, as the user reads it. Ignored when it is not an `Int` or not positive; clamped to
 * the last page (ADR 0014).
 */
internal const val EXTRA_PAGE = "page"

/** Sentinel of [Intent.getIntExtra]: the extra holds another type than `Int`. */
private const val NO_PAGE = Int.MIN_VALUE

/**
 * Copies what [ParseIncomingDocumentUseCase][io.github.myxomatom.lutrin.core.domain.document.ParseIncomingDocumentUseCase]
 * checks out of this intent; nothing is validated here.
 *
 * @param receiverPackage package name of the app at run time.
 */
internal fun Intent.toIncomingIntent(receiverPackage: String): IncomingIntent = IncomingIntent(
    action = action,
    data = data?.toString(),
    stream = IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)?.toString(),
    type = type,
    page = pageExtra(),
    receiverPackage = receiverPackage,
)

/** The [EXTRA_PAGE] extra, or `null` when absent or not an `Int`. */
private fun Intent.pageExtra(): Int? =
    if (hasExtra(EXTRA_PAGE)) getIntExtra(EXTRA_PAGE, NO_PAGE).takeUnless { it == NO_PAGE } else null
