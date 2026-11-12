// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.toClipEntry
import androidx.core.net.toUri
import io.github.myxomatom.lutrin.core.domain.pdf.PdfLink
import io.github.myxomatom.lutrin.feature.reader.LinkEffect
import io.github.myxomatom.lutrin.feature.reader.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * What the reader screen needs for the links of the document (F09, ADR 0022), wired by
 * [ReaderRoute] to the ViewModel. The defaults make a reader without links.
 *
 * @property externalLink external link waiting for confirmation: V7 is shown while not `null`.
 * @property effects effects of the links, collected once by the screen.
 * @property onLinkClick called with a link activated on the pages, and the page shown.
 * @property onExternalLinkConfirm « Ouvrir » in V7.
 * @property onExternalLinkDismiss « Annuler », « Copier le lien », back or a tap outside V7.
 * @property onOpenExternalLink hands a confirmed link to another app; returns `false` when no
 *   app can open it.
 */
@Stable
class ReaderLinks(
    val externalLink: String? = null,
    val effects: Flow<LinkEffect> = emptyFlow(),
    val onLinkClick: (PdfLink, Int) -> Unit = { _, _ -> },
    val onExternalLinkConfirm: () -> Unit = {},
    val onExternalLinkDismiss: () -> Unit = {},
    val onOpenExternalLink: (String) -> Boolean = { false },
)

/**
 * Hands [uri] to the app of the user's choice: `ACTION_VIEW` in `CATEGORY_BROWSABLE`, as a link
 * of a web page would be (ADR 0022). Lutrin itself never opens it and never reaches the network.
 *
 * @param context where the activity is started from; outside an activity, it starts in a new task.
 * @param uri a link accepted by `CheckExternalLinkUseCase` and confirmed in V7.
 * @return `false` when no installed app can open the link.
 */
internal fun openExternalLink(context: Context, uri: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, uri.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (expected: ActivityNotFoundException) {
        // Reported to the user by the caller (« Aucune application ne peut ouvrir ce lien »).
        false
    }
}

/**
 * Everything the links add to the reader (F09, ADR 0022): carries out their effects
 * ([LinkEffectsHandler]) and shows V7 while an external link waits for confirmation
 * ([ExternalLinkConfirmation]).
 *
 * @return the state of the messages of the reader, for its `SnackbarHost`.
 */
@Composable
internal fun rememberLinkHandling(links: ReaderLinks, navigator: PageNavigator): SnackbarHostState {
    val snackbar = remember { SnackbarHostState() }
    LinkEffectsHandler(links, navigator, snackbar)
    ExternalLinkConfirmation(links, snackbar)
    return snackbar
}

/**
 * Carries out the effects of the links: shows the destination of an internal link, then offers
 * to go back to the page left (« Vous étiez à la page N. », « Revenir »); hands a confirmed
 * external link to another app, or says that none can open it; says that a link is refused.
 */
@Composable
internal fun LinkEffectsHandler(links: ReaderLinks, navigator: PageNavigator, snackbar: SnackbarHostState) {
    val resources = LocalResources.current
    val currentOnOpen by rememberUpdatedState(links.onOpenExternalLink)
    LaunchedEffect(links.effects, navigator) {
        links.effects.collect { effect ->
            when (effect) {
                is LinkEffect.GoTo -> {
                    val destination = effect.destination
                    navigator.goTo(destination.pageIndex, destination.y ?: 0f)
                    effect.returnPage?.let { page ->
                        offerReturn(
                            snackbar = snackbar,
                            message = resources.getString(R.string.reader_link_return_message, page + 1),
                            action = resources.getString(R.string.reader_link_return_action),
                            onReturn = { navigator.goTo(page) },
                        )
                    }
                }

                is LinkEffect.OpenExternalLink ->
                    if (!currentOnOpen(effect.uri)) inform(snackbar, resources.getString(R.string.reader_link_no_app))

                LinkEffect.LinkRefused -> inform(snackbar, resources.getString(R.string.reader_link_refused))
            }
        }
    }
}

/** « Vous étiez à la page N. » with « Revenir », replacing any message shown; never blocks the effects. */
private fun CoroutineScope.offerReturn(
    snackbar: SnackbarHostState,
    message: String,
    action: String,
    onReturn: suspend () -> Unit,
) {
    launch {
        snackbar.currentSnackbarData?.dismiss()
        val result = snackbar.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) onReturn()
    }
}

/** A short message replacing any message shown; never blocks the effects. */
private fun CoroutineScope.inform(snackbar: SnackbarHostState, message: String) {
    launch {
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(message)
    }
}

/**
 * The messages of the reader, above the floating bar when it is shown, above the navigation bar
 * otherwise.
 */
@Composable
internal fun ReaderSnackbarHost(snackbar: SnackbarHostState, controlsVisible: Boolean, modifier: Modifier = Modifier) {
    val placement =
        if (controlsVisible) {
            Modifier.padding(bottom = floatingPageBarClearance())
        } else {
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        }
    SnackbarHost(snackbar, modifier.then(placement))
}

/**
 * V7 over the reader while [ReaderLinks.externalLink] is set. « Copier le lien » copies the link
 * to the clipboard and closes the dialog; before Android 13, which has no copy confirmation of
 * its own, « Lien copié » is shown.
 */
@Composable
internal fun ExternalLinkConfirmation(links: ReaderLinks, snackbar: SnackbarHostState) {
    // Outside the condition: the copy goes on after the dialog has closed.
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val resources = LocalResources.current
    val uri = links.externalLink ?: return
    ExternalLinkDialog(
        uri = uri,
        onOpen = links.onExternalLinkConfirm,
        onCopy = {
            links.onExternalLinkDismiss()
            scope.launch {
                clipboard.setClipEntry(ClipData.newPlainText(null, uri).toClipEntry())
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    inform(snackbar, resources.getString(R.string.reader_link_copied))
                }
            }
        },
        onDismiss = links.onExternalLinkDismiss,
    )
}
