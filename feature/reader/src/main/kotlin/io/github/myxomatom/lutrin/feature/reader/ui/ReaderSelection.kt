// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.selection.SelectionController
import io.github.myxomatom.lutrin.feature.reader.selection.SelectionEffect
import io.github.myxomatom.lutrin.feature.reader.selection.SelectionHandle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * What the reader screen needs for the text selection (F08, ADR 0024), wired by [ReaderRoute] to
 * the selection controller of the ViewModel. The defaults make a reader in which a long press
 * selects nothing.
 *
 * @property selection the selection shown, on one page; `null` when nothing is selected.
 * @property canCopy whether « Copier » copies (the document allows it, or the setting ignores
 *   its restriction); otherwise it explains that the author forbids it (ADR 0024).
 * @property effects copies and refusals, collected once by the screen.
 * @property onLongPress a long press on a page, with the page and the point pressed; returns
 *   whether a word was selected.
 * @property onHandleDragStart a handle starts moving.
 * @property onHandleDrag the dragged handle points at this point of the page of the selection.
 * @property onHandleDragEnd the handle is released.
 * @property onCopy « Copier ».
 * @property onSelectAll « Tout sélectionner ».
 * @property onCopyPage « Copier le texte de la page », the TalkBack action, with the page shown.
 * @property onClear closes the selection.
 */
@Stable
class ReaderSelection(
    val selection: TextSelection? = null,
    val canCopy: Boolean = true,
    val effects: Flow<SelectionEffect> = emptyFlow(),
    val onLongPress: suspend (Int, PdfPoint) -> Boolean = { _, _ -> false },
    val onHandleDragStart: (SelectionHandle) -> Unit = {},
    val onHandleDrag: (PdfPoint) -> Unit = {},
    val onHandleDragEnd: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onCopyPage: (Int) -> Unit = {},
    val onClear: () -> Unit = {},
)

/**
 * The selection of [controller] for the screen, with its [selection] collected by the caller.
 *
 * @param controller the selection controller of the ViewModel.
 * @param selection its current selection.
 * @param canCopy whether the text of the document may be copied now.
 */
internal fun readerSelection(
    controller: SelectionController,
    selection: TextSelection?,
    canCopy: Boolean,
): ReaderSelection = ReaderSelection(
    selection = selection,
    canCopy = canCopy,
    effects = controller.effects,
    onLongPress = controller::onLongPress,
    onHandleDragStart = controller::onHandleDragStart,
    onHandleDrag = controller::onHandleDrag,
    onHandleDragEnd = controller::onHandleDragEnd,
    onCopy = controller::onCopy,
    onSelectAll = controller::onSelectAll,
    onCopyPage = controller::onCopyPage,
    onClear = controller::onClear,
)

/**
 * The clipboard content of a copied selection. From Android 13, the text of a document opened
 * with a password is flagged as sensitive, so that the system hides it in its copy preview
 * (ADR 0017, ADR 0024); any other text is copied as is.
 *
 * @param text the copied text.
 * @param isSensitive whether the document is protected by a password.
 * @param sdkInt version of the system, given by tests.
 */
// The constant is inlined at compile time; it is only written for Android 13 and later.
@SuppressLint("InlinedApi")
internal fun selectionClip(text: String, isSensitive: Boolean, sdkInt: Int = Build.VERSION.SDK_INT): ClipData =
    ClipData.newPlainText(null, text).apply {
        if (isSensitive && sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
    }

/** Whether the system confirms a copy to the clipboard by itself: from Android 13. */
private val systemConfirmsCopy: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Everything the selection adds to the reader beyond the pages: carries out its effects
 * ([SelectionEffectsHandler]), lets back close it, and closes it when the page shown changes or
 * the search opens (ADR 0024). Composed after the search, so that back closes the selection
 * first.
 */
@Composable
internal fun SelectionHandling(
    selection: ReaderSelection,
    navigator: PageNavigator,
    searchOpen: Boolean,
    snackbar: SnackbarHostState,
) {
    SelectionEffectsHandler(selection.effects, snackbar)
    BackHandler(enabled = selection.selection != null, onBack = selection.onClear)
    val currentOnClear by rememberUpdatedState(selection.onClear)
    LaunchedEffect(navigator) {
        snapshotFlow { navigator.currentPage }.drop(1).collect { currentOnClear() }
    }
    LaunchedEffect(searchOpen) {
        if (searchOpen) currentOnClear()
    }
}

/**
 * Carries out the effects of the selection: puts the text on the clipboard and, before Android
 * 13 only, says so (« Texte copié »): from Android 13 the system shows its own confirmation.
 * Explains a refused copy, and a page without text.
 *
 * @param systemConfirmsCopy whether the system shows its own confirmation; given by tests.
 */
@Composable
internal fun SelectionEffectsHandler(
    effects: Flow<SelectionEffect>,
    snackbar: SnackbarHostState,
    systemConfirmsCopy: Boolean = io.github.myxomatom.lutrin.feature.reader.ui.systemConfirmsCopy,
) {
    val clipboard = LocalClipboard.current
    val resources = LocalResources.current
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is SelectionEffect.Copy -> {
                    clipboard.setClipEntry(selectionClip(effect.text, effect.isSensitive).toClipEntry())
                    if (!systemConfirmsCopy) {
                        inform(snackbar, resources.getString(R.string.reader_selection_copied))
                    }
                }

                SelectionEffect.CopyRefused ->
                    inform(snackbar, resources.getString(R.string.reader_selection_copy_forbidden))

                SelectionEffect.NothingToCopy -> inform(
                    snackbar,
                    resources.getString(R.string.reader_selection_no_text),
                )
            }
        }
    }
}

/**
 * A tap on the pages while text is selected only closes the selection (ADR 0024); otherwise it
 * goes to [onTap] (link, or full screen).
 */
@Composable
internal fun rememberSelectionTap(selection: ReaderSelection, onTap: (PageHit?) -> Unit): (PageHit?) -> Unit {
    val currentSelection by rememberUpdatedState(selection)
    val currentOnTap by rememberUpdatedState(onTap)
    return remember {
        { hit -> if (currentSelection.selection != null) currentSelection.onClear() else currentOnTap(hit) }
    }
}

/**
 * Handles a long press on the pages: selects the word under the finger (F08). Outside the text,
 * or on a page without text, the press does what a tap does ([onTap]), as before the selection
 * existed (ADR 0024).
 */
@Composable
internal fun rememberPageLongPress(selection: ReaderSelection, onTap: (PageHit?) -> Unit): (PageHit?) -> Unit {
    val scope = rememberCoroutineScope()
    val currentSelection by rememberUpdatedState(selection)
    val currentOnTap by rememberUpdatedState(onTap)
    return remember {
        { hit ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val selected = hit != null && currentSelection.onLongPress(hit.pageIndex, hit.point)
                if (!selected) currentOnTap(hit)
            }
        }
    }
}

/**
 * « Copier le texte de la page », the TalkBack action of the document area (F08, F18): the whole
 * text of the page shown, without a long press, under the same copy permission (ADR 0024).
 */
@Composable
internal fun copyPageAction(selection: ReaderSelection, page: Int): CustomAccessibilityAction {
    val currentSelection by rememberUpdatedState(selection)
    return CustomAccessibilityAction(stringResource(R.string.reader_selection_copy_page_action)) {
        currentSelection.onCopyPage(page)
        true
    }
}
