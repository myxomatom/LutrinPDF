// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.share

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.myxomatom.lutrin.feature.reader.R

/** Media type sent with the document: the file is shared as it is, a PDF (ADR 0027). */
internal const val SHARED_MIME_TYPE = "application/pdf"

/**
 * What to share (F15, ADR 0027): the document as the provider gave it to Lutrin, without any copy.
 *
 * @property uri `content://` URI of the document, the one it was opened from.
 * @property name name of the document, without its extension; the label of the clip.
 */
internal class ShareRequest(val uri: Uri, val name: String)

/** What came of a request to share (F15, ADR 0027). */
enum class ShareOutcome {
    /** The chooser of the system is open. */
    STARTED,

    /** No app on the device can receive a PDF file. */
    NO_APP,

    /** The file cannot be handed to another app: no grantable `content://` URI. */
    NOT_SHAREABLE,
}

/**
 * The intent that sends [request] to another app: `ACTION_SEND` with the URI of the document, the
 * type `application/pdf` and a temporary read grant for whoever receives it (`AGENTS.md` §9: no
 * path, no copy, no file provider). The document is sent as it is, encrypted if it is (ADR 0027).
 */
internal fun shareIntent(request: ShareRequest): Intent = Intent(Intent.ACTION_SEND).apply {
    type = SHARED_MIME_TYPE
    putExtra(Intent.EXTRA_STREAM, request.uri)
    // The clip is what the grant is read from on every Android version.
    clipData = ClipData.newRawUri(request.name, request.uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/**
 * The chooser of the system around [shareIntent], without Lutrin itself among the targets: it
 * receives PDF files too (ADR 0014), and sharing a document back to the reader that holds it is
 * of no use.
 *
 * @param context any context of the app, to find its own receivers and read the title.
 */
// Only the activities of the app itself are looked for, which Android 11 package visibility always shows.
@SuppressLint("QueryPermissionsNeeded")
internal fun shareChooser(context: Context, request: ShareRequest): Intent {
    val send = shareIntent(request)
    val own =
        context.packageManager
            .queryIntentActivities(send, 0)
            .filter { it.activityInfo.packageName == context.packageName }
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
    return Intent.createChooser(send, context.getString(R.string.reader_share_chooser_title)).apply {
        if (own.isNotEmpty()) putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, own.toTypedArray())
    }
}

/**
 * Opens the chooser of the system for [request] (F15, ADR 0027). Called by the reader's route with
 * the `Context` of its activity; the ViewModel never sees one.
 *
 * @param context the activity showing the reader.
 * @return what happened, for the reader to say when the chooser did not open. A refused grant
 *   (a provider that does not let the document go) is [ShareOutcome.NOT_SHAREABLE], never a
 *   crash.
 */
internal fun startSharing(context: Context, request: ShareRequest): ShareOutcome = try {
    context.startActivity(shareChooser(context, request))
    ShareOutcome.STARTED
} catch (expected: ActivityNotFoundException) {
    // A device without a chooser, or without any app to send to.
    ShareOutcome.NO_APP
} catch (expected: SecurityException) {
    // The provider refuses to grant its document to another app.
    ShareOutcome.NOT_SHAREABLE
}
