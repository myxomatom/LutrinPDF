// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.myxomatom.lutrin.core.ui.R as CoreUiR
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState

/**
 * Asks for the password of an encrypted document (V4, F10, ADR 0017).
 *
 * What the user types stays in a [TextFieldState] of this dialog only: it is deliberately not
 * saveable (no `rememberTextFieldState`), so that it never reaches the saved instance state, and
 * it is cleared when the dialog goes away (document open, « Annuler », back). After a rotation the
 * field is therefore empty. The field is a password field (masked, password keyboard, no
 * suggestions nor learning by the keyboard), and the dialog keeps out of autofill: a document
 * password is not an account credential, and a password manager would store it where the app
 * cannot see (ADR 0017).
 *
 * A wrong password keeps the text, selected, with an error announced by screen readers; a new
 * attempt is always possible. Tapping outside does not close the dialog, so that a stray tap
 * never loses what was typed.
 *
 * @param state the question: document name, wrong password, check in progress.
 * @param onSubmit called with the typed password, by « Ouvrir » or the keyboard action.
 * @param onCancel called by « Annuler » and the back gesture; the reader is expected to close.
 */
@Composable
internal fun PasswordDialog(
    state: ReaderUiState.PasswordRequired,
    onSubmit: (CharSequence) -> Unit,
    onCancel: () -> Unit,
) {
    val field = remember { TextFieldState() }
    val autofill = LocalAutofillManager.current
    KeepOutOfAutofill()
    DisposableEffect(field) {
        onDispose {
            autofill?.cancel()
            field.clearText()
        }
    }
    val showError = state.isIncorrect && !state.isChecking
    val canSubmit = field.text.isNotEmpty() && !state.isChecking
    val submit = {
        if (canSubmit) {
            autofill?.cancel()
            onSubmit(field.text)
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = {
            Icon(painterResource(CoreUiR.drawable.ic_lock), null, tint = MaterialTheme.colorScheme.primary)
        },
        title = { Text(stringResource(R.string.reader_password_title)) },
        text = { PasswordQuestion(state.documentName, field, showError, onImeAction = submit) },
        confirmButton = {
            TextButton(onClick = submit, enabled = canSubmit) { Text(stringResource(R.string.reader_password_open)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.reader_password_cancel)) }
        },
    )
}

/**
 * Encrypted document (V4): the password dialog over an empty reader, whose « back » arrow leaves
 * like « Annuler ». The mock-up shows the dialog over the library; the reader is already the
 * current screen when the engine reports the encryption (ADR 0017).
 */
@Composable
internal fun ReaderPasswordContent(
    state: ReaderUiState.PasswordRequired,
    onBack: () -> Unit,
    onPasswordSubmit: (CharSequence) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, topBar = { ReaderTopBar(title = "", onBack = onBack) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize())
    }
    PasswordDialog(state, onSubmit = onPasswordSubmit, onCancel = onBack)
}

/**
 * The text of the dialog: the question, the password field and the reminder that the password is
 * never saved (mock-up V4).
 */
@Composable
private fun PasswordQuestion(documentName: String, field: TextFieldState, showError: Boolean, onImeAction: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    // On opening, and again after each wrong password: the text is selected, to type it again.
    LaunchedEffect(showError) {
        focus.requestFocus()
        if (showError) field.edit { selectAll() }
    }
    // Scrolls with very large fonts, so that the field and the buttons stay reachable.
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PasswordDialogDefaults.Spacing),
    ) {
        Text(stringResource(R.string.reader_password_message, documentName))
        OutlinedSecureTextField(
            state = field,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text(stringResource(R.string.reader_password_label)) },
            trailingIcon = { VisibilityToggle(visible, onToggle = { visible = !visible }) },
            supportingText = if (showError) ({ IncorrectPasswordMessage() }) else null,
            isError = showError,
            textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
            keyboardOptions = PasswordDialogDefaults.KeyboardOptions,
            onKeyboardAction = { onImeAction() },
        )
        Text(stringResource(R.string.reader_password_not_stored), style = MaterialTheme.typography.bodySmall)
    }
}

/** « Mot de passe incorrect. Réessayez. », announced by screen readers as soon as it appears. */
@Composable
private fun IncorrectPasswordMessage() {
    Text(
        stringResource(R.string.reader_password_incorrect),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The eye button that shows or masks the password; a 48 dp target. */
@Composable
private fun VisibilityToggle(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        if (visible) {
            Icon(painterResource(R.drawable.ic_visibility_off), stringResource(R.string.reader_password_hide))
        } else {
            Icon(painterResource(R.drawable.ic_visibility), stringResource(R.string.reader_password_show))
        }
    }
}

/**
 * Excludes the window of the dialog from autofill: no fill request, and no offer to save the
 * password. Best effort, as Android leaves the last word to the autofill service (ADR 0017).
 */
@Composable
private fun KeepOutOfAutofill() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose {}
    }
}

/** Constants of the password dialog. */
internal object PasswordDialogDefaults {
    /** Gap between the question, the field and the reminder (mock-up V4). */
    val Spacing = 16.dp

    /** Password keyboard, without suggestions nor learning; its action is « Ouvrir ». */
    val KeyboardOptions =
        KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        )
}
