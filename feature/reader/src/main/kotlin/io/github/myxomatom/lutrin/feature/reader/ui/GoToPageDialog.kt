// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.myxomatom.lutrin.feature.reader.R

/** Longest page number accepted by the field: more digits than any document has pages. */
private const val MAX_DIGITS = 7

/** Keeps the digits, up to [MAX_DIGITS]: anything else typed or pasted is dropped. */
private val DigitsOnly = InputTransformation {
    val digits = asCharSequence().filter(Char::isDigit).take(MAX_DIGITS)
    if (digits.length != length) replace(0, length, digits)
}

/**
 * « Go to page » dialog (V3, F05): a numeric field filled with the page shown, selected, in focus
 * with the keyboard open. « Aller » and the keyboard action are enabled for a page of the
 * document only; otherwise the field shows why (nothing typed, 0 or beyond the last page:
 * « Entre 1 et N »), announced by screen readers.
 *
 * The text typed survives a rotation (`rememberTextFieldState` is saveable, unlike the password
 * dialog's), and goes away with the dialog.
 *
 * @param currentPage zero-based page shown by the reader, proposed at first.
 * @param pageCount number of pages of the document, at least 1.
 * @param onGoTo called with the zero-based page asked for; the caller shows it and closes the dialog.
 * @param onDismiss called by « Annuler », the back gesture and a tap outside.
 */
@Composable
internal fun GoToPageDialog(currentPage: Int, pageCount: Int, onGoTo: (Int) -> Unit, onDismiss: () -> Unit) {
    val field = rememberTextFieldState((currentPage + 1).toString())
    val input = parsePageNumber(field.text, pageCount)
    val valid = input as? PageNumberInput.Valid
    val submit = { if (valid != null) onGoTo(valid.page) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_goto_title)) },
        text = { PageNumberField(field, input, pageCount, onImeAction = submit) },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = valid != null) {
                Text(stringResource(R.string.reader_goto_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_goto_cancel)) } },
    )
}

/** The numeric field with its hint « Entre 1 et N », replaced by the reason when [input] is not a page. */
@Composable
private fun PageNumberField(field: TextFieldState, input: PageNumberInput, pageCount: Int, onImeAction: () -> Unit) {
    val focus = remember { FocusRequester() }
    // On opening: in focus, which opens the keyboard, with the page shown selected to type over it.
    LaunchedEffect(Unit) {
        focus.requestFocus()
        field.edit { selectAll() }
    }
    val hint = stringResource(R.string.reader_goto_range, pageCount)
    val message =
        when (input) {
            PageNumberInput.Empty -> stringResource(R.string.reader_goto_empty)
            PageNumberInput.OutOfRange -> hint
            is PageNumberInput.Valid -> null
        }
    OutlinedTextField(
        state = field,
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .semantics { message?.let { error(it) } },
        label = { Text(stringResource(R.string.reader_goto_label)) },
        supportingText = { SupportingText(message ?: hint, announce = message != null) },
        isError = message != null,
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = DigitsOnly,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
        onKeyboardAction = { onImeAction() },
    )
}

/** The line under the field; announced as soon as it turns into an error. */
@Composable
private fun SupportingText(text: String, announce: Boolean) {
    Text(text, Modifier.semantics { if (announce) liveRegion = LiveRegionMode.Polite })
}
